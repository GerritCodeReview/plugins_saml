package com.googlesource.gerrit.plugins.saml;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.Sequences;
import com.google.gerrit.server.ServerInitiated;
import com.google.gerrit.server.account.*;
import com.google.gerrit.server.group.InternalGroup;
import com.google.gerrit.server.group.db.GroupsUpdate;
import com.google.gerrit.server.group.db.InternalGroupCreation;
import com.google.gerrit.server.group.db.InternalGroupUpdate;
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
 * This class maps the membership attributes in the SAML document onto Internal groups prefixed with the saml group
 * prefix.
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

  @Inject
  SamlMembership(SamlConfig samlConfig,
                 @GerritPersonIdent PersonIdent serverIdent,
                 AccountManager accountManager,
                 GroupCache groupCache,
                 IdentifiedUser.GenericFactory userFactory,
                 @ServerInitiated Provider<GroupsUpdate> groupsUpdateProvider,
                 Sequences sequences) {
    this.memberAttr = samlConfig.getMemberOfAttr();
    this.serverIdent = serverIdent;
    this.accountManager = accountManager;
    this.groupCache = groupCache;
    this.userFactory = userFactory;
    this.groupsUpdateProvider = groupsUpdateProvider;
    this.sequences = sequences;
  }

  public void sync(AuthenticatedUser user, SAML2Profile profile) throws IOException {
    if (Strings.isNullOrEmpty(memberAttr)) {
      return;
    }

    Set<AccountGroup.UUID> samlMembership = Optional.ofNullable((List<?>)profile.getAttribute(memberAttr, List.class))
        .orElse(Collections.emptyList())
        .stream()
        .map(m -> getOrCreateGroup(m.toString()))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .collect(Collectors.toSet());
    IdentifiedUser identifiedUser = userFactory.create(getOrCreateAccountId(user));
    Set<AccountGroup.UUID> userMembership = identifiedUser.getEffectiveGroups().getKnownGroups().stream()
        .filter(uuid -> groupCache.get(uuid).filter(g -> g.getName().startsWith(GROUP_PREFIX)).isPresent())
        .collect(Collectors.toSet());

    log.info("User {} is member of {} in saml and {} in gerrit", user.getUsername(), samlMembership, userMembership);

    samlMembership.stream()
        .filter(g -> !userMembership.contains(g))
        .forEach(g -> this.updateMembers(g, members ->
            Sets.union(members, ImmutableSet.of(identifiedUser.getAccountId()))));
    userMembership.stream()
        .filter(g -> !samlMembership.contains(g))
        .forEach(g -> this.updateMembers(g, members ->
            Sets.difference(members, ImmutableSet.of(identifiedUser.getAccountId()))));
  }

  private void updateMembers(AccountGroup.UUID group, InternalGroupUpdate.MemberModification memberModification) {
    InternalGroupUpdate update = InternalGroupUpdate.builder()
        .setMemberModification(memberModification)
        .build();
    try {
      groupsUpdateProvider.get().updateGroup(group, update);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private Optional<AccountGroup.UUID> getOrCreateGroup(String samlGroup) {
    return samlGroupToName(samlGroup).map(name ->
        groupCache.get(name)
            .orElseGet(() -> createGroup(name, samlGroup))
    ).map(InternalGroup::getGroupUUID);
  }

  private InternalGroup createGroup(AccountGroup.NameKey name, String samlGroup) {
    try {
      AccountGroup.Id groupId = new AccountGroup.Id(sequences.nextGroupId());
      AccountGroup.UUID uuid = GroupUUID.make(name.get(), serverIdent);
      InternalGroupCreation groupCreation =
          InternalGroupCreation.builder()
              .setGroupUUID(uuid)
              .setNameKey(name)
              .setId(groupId)
              .build();
      InternalGroupUpdate.Builder groupUpdateBuilder = InternalGroupUpdate.builder()
          .setVisibleToAll(false)
          .setDescription(samlGroup + " (imported by the SAML plugin)");
      return groupsUpdateProvider.get().createGroup(groupCreation, groupUpdateBuilder.build());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private Optional<AccountGroup.NameKey> samlGroupToName(String samlGroup) {
    String id = samlGroup.trim().replaceAll("\\W+", "_").toLowerCase(Locale.US);
    return Optional.of(id)
        .filter(s -> !s.isEmpty())
        .map(GROUP_PREFIX::concat)
        .map(AccountGroup.NameKey::new);
  }

  private Account.Id getOrCreateAccountId(AuthenticatedUser user) throws IOException {
    AuthRequest authRequest = AuthRequest.forUser(user.getUsername());
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
