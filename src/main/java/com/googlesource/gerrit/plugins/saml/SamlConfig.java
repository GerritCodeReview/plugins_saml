// Copyright (C) 2015 The Android Open Source Project
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

import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.eclipse.jgit.lib.Config;

/** SAML 2.0 related settings from {@code gerrit.config}. */
@Singleton
public class SamlConfig {
  private static final String SAML_SECTION = "saml";
  private final String identityProviderEntityId;
  private final String serviceProviderEntityId;
  private final String metadataPath;
  private final String keystorePath;
  private final String privateKeyPassword;
  private final String keystorePassword;
  private final String displayNameAttr;
  private final String userNameAttr;
  private final String emailAddressAttr;
  private final int maxAuthLifetimeAttr;
  private final boolean computedDisplayName;
  private final String firstNameAttr;
  private final String lastNameAttr;
  private final int maxAuthLifetimeDefault = 24 * 60 * 60; // 24h;
  private final boolean useNameQualifier;
  private final String memberOfAttr;

  @Inject
  SamlConfig(@GerritServerConfig Config cfg) {
    serviceProviderEntityId = getString(cfg, "serviceProviderEntityId");
    identityProviderEntityId = getString(cfg, "identityProviderEntityId");
    metadataPath = getString(cfg, "metadataPath");
    keystorePath = getString(cfg, "keystorePath");
    privateKeyPassword = getString(cfg, "privateKeyPassword");
    keystorePassword = getString(cfg, "keystorePassword");
    displayNameAttr = getGetStringWithDefault(cfg, "displayNameAttr", "DisplayName");
    userNameAttr = getGetStringWithDefault(cfg, "userNameAttr", "UserName");
    emailAddressAttr = getGetStringWithDefault(cfg, "emailAddressAttr", "EmailAddress");
    maxAuthLifetimeAttr = cfg.getInt("saml", "maxAuthLifetime", maxAuthLifetimeDefault);
    computedDisplayName = cfg.getBoolean(SAML_SECTION, "computedDisplayName", false);
    firstNameAttr = getGetStringWithDefault(cfg, "firstNameAttr", "FirstName");
    lastNameAttr = getGetStringWithDefault(cfg, "lastNameAttr", "LastName");
    useNameQualifier = cfg.getBoolean(SAML_SECTION, "useNameQualifier", true);
    memberOfAttr = getString(cfg, "memberOfAttr");
  }

  public String getMetadataPath() {
    return metadataPath;
  }

  public String getKeystorePath() {
    return keystorePath;
  }

  public String getPrivateKeyPassword() {
    return privateKeyPassword;
  }

  public String getKeystorePassword() {
    return keystorePassword;
  }

  public String getDisplayNameAttr() {
    return displayNameAttr;
  }

  public String getUserNameAttr() {
    return userNameAttr;
  }

  public String getEmailAddressAttr() {
    return emailAddressAttr;
  }

  public int getMaxAuthLifetimeAttr() {
    return maxAuthLifetimeAttr;
  }

  private static String getString(Config cfg, String name) {
    return cfg.getString(SAML_SECTION, null, name);
  }

  private static String getGetStringWithDefault(Config cfg, String name, String defaultValue) {
    String result = getString(cfg, name);
    if (result != null) {
      return result;
    }
    return defaultValue;
  }

  public String getFirstNameAttr() {
    return firstNameAttr;
  }

  public String getLastNameAttr() {
    return lastNameAttr;
  }

  public boolean isComputedDisplayName() {
    return computedDisplayName;
  }

  public String getServiceProviderEntityId() {
    return serviceProviderEntityId;
  }

  public boolean useNameQualifier() {
    return useNameQualifier;
  }

  public String getIdentityProviderEntityId() {
    return identityProviderEntityId;
  }

  public String getMemberOfAttr() {
    return memberOfAttr;
  }
}
