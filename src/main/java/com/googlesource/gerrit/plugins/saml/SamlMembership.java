// Copyright (C) 2020 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.googlesource.gerrit.plugins.saml;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.InternalGroup;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.ServerInitiated;
import com.google.gerrit.server.account.*;
import com.google.gerrit.server.group.db.GroupsUpdate;
import com.google.gerrit.server.group.db.InternalGroupCreation;
import com.google.gerrit.server.group.db.InternalGroupUpdate;
import com.google.gerrit.server.notedb.Sequences;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.*;
import java.util.stream.Collectors;
import org.eclipse.jgit.lib.PersonIdent;
import org.pac4j.saml.profile.SAML2Profile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
/**
 * This class maps the membership attributes in the SAML document onto Internal groups prefixed with
 * the saml group prefix.
 */
public class SamlMembership {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private static final String GROUP_PREFIX = "saml/";

  private final String memberAttr;
  private final PersonIdent serverIdent;
  private final AccountManager accountManager;
  private final GroupCache groupCache;
  private final IdentifiedUser.GenericFactory userFactory;
  private final Provider<GroupsUpdate> groupsUpdateProvider;
  private final Sequences sequences;
  private final AuthRequest.Factory authRequestFactory;

  @Inject
  SamlMembership(
      SamlConfig samlConfig,
      @GerritPersonIdent PersonIdent serverIdent,
      AccountManager accountManager,
      GroupCache groupCache,
      IdentifiedUser.GenericFactory userFactory,
      @ServerInitiated Provider<GroupsUpdate> groupsUpdateProvider,
      Sequences sequences,
      AuthRequest.Factory authRequestFactory) {
    this.memberAttr = samlConfig.getMemberOfAttr();
    this.serverIdent = serverIdent;
    this.accountManager = accountManager;
    this.groupCache = groupCache;
    this.userFactory = userFactory;
    this.groupsUpdateProvider = groupsUpdateProvider;
    this.sequences = sequences;
    this.authRequestFactory = authRequestFactory;
  }

  /**
   * Synchronises the groups of a user with those in LDAP.
   *
   * @param user gerrit user
   * @param profile SAML profile
   */
  public void sync(AuthenticatedUser user, SAML2Profile profile) throws IOException {
    Set<AccountGroup.UUID> samlMembership =
        Optional.ofNullable((List<?>) profile.getAttribute(memberAttr, List.class))
            .orElse(Collections.emptyList()).stream()
            .map(m -> getOrCreateGroup(m.toString()))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toSet());
    IdentifiedUser identifiedUser = userFactory.create(getOrCreateAccountId(user));
    Set<AccountGroup.UUID> userMembership =
        identifiedUser.getEffectiveGroups().getKnownGroups().stream()
            .filter(
                uuid ->
                    groupCache
                        .get(uuid)
                        .filter(g -> g.getName().startsWith(GROUP_PREFIX))
                        .isPresent())
            .collect(Collectors.toSet());

    log.debug(
        "User {} is member of {} in saml and {} in gerrit",
        user.getUsername(),
        samlMembership,
        userMembership);

    Set<Account.Id> accountIdSet = ImmutableSet.of(identifiedUser.getAccountId());
    samlMembership.stream()
        .filter(g -> !userMembership.contains(g))
        .forEach(g -> this.updateMembers(g, members -> Sets.union(members, accountIdSet)));
    userMembership.stream()
        .filter(g -> !samlMembership.contains(g))
        .forEach(
            g ->
                this.updateMembers(
                    g,
                    members ->
                        Sets.difference(members, ImmutableSet.of(identifiedUser.getAccountId()))));
  }

  /**
   * test if membership syncing is enabled.
   *
   * @return true when it is enabled.
   */
  public boolean isEnabled() {
    return !Strings.isNullOrEmpty(memberAttr);
  }

  private void updateMembers(
      AccountGroup.UUID group, InternalGroupUpdate.MemberModification memberModification) {
    InternalGroupUpdate update =
        InternalGroupUpdate.builder().setMemberModification(memberModification).build();
    try {
      groupsUpdateProvider.get().updateGroup(group, update);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private Optional<AccountGroup.UUID> getOrCreateGroup(String samlGroup) {
    return samlGroupToName(samlGroup)
        .map(name -> groupCache.get(name).orElseGet(() -> createGroup(name, samlGroup)))
        .map(InternalGroup::getGroupUUID);
  }

  private InternalGroup createGroup(AccountGroup.NameKey name, String samlGroup) {
    try {
      AccountGroup.Id groupId = AccountGroup.id(sequences.nextGroupId());
      AccountGroup.UUID uuid = GroupUuid.make(name.get(), serverIdent);
      InternalGroupCreation groupCreation =
          InternalGroupCreation.builder()
              .setGroupUUID(uuid)
              .setNameKey(name)
              .setId(groupId)
              .build();
      InternalGroupUpdate.Builder groupUpdateBuilder =
          InternalGroupUpdate.builder()
              .setVisibleToAll(false)
              .setDescription(samlGroup + " (imported by the SAML plugin)");
      return groupsUpdateProvider.get().createGroup(groupCreation, groupUpdateBuilder.build());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private Optional<AccountGroup.NameKey> samlGroupToName(String samlGroup) {
    return Optional.of(samlGroup)
        .filter(s -> !s.isEmpty())
        .map(GROUP_PREFIX::concat)
        .map(AccountGroup::nameKey);
  }

  private Account.Id getOrCreateAccountId(AuthenticatedUser user) throws IOException {
    AuthRequest authRequest = authRequestFactory.createForUser(user.getUsername());
    authRequest.setUserName(user.getUsername());
    authRequest.setEmailAddress(user.getEmail());
    authRequest.setDisplayName(user.getDisplayName());
    try {
      return accountManager.authenticate(authRequest).getAccountId();
    } catch (AccountException e) {
      throw new RuntimeException(e);
    }
  }
}
