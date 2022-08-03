// Copyright (C) 2023 The Android Open Source Project
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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.googlesource.gerrit.plugins.saml.SamlWebFilter.SAML_CALLBACK;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.nio.file.Path;
import org.opensaml.saml.common.xml.SAMLConstants;
import org.pac4j.saml.client.SAML2Client;
import org.pac4j.saml.config.SAML2Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class SamlClientProvider implements Provider<SAML2Client> {
  private static final Logger log = LoggerFactory.getLogger(SamlClientProvider.class);

  private final SamlConfig samlConfig;
  private final String canonicalUrl;
  private final Path libModuleDataDir;

  private static final ImmutableList<String> LOGOUT_TYPE_VALID_VALUES =
    ImmutableList.of("redirect", "slo-default", "slo-post", "slo-redirect");

  @Inject
  public SamlClientProvider(
      @CanonicalWebUrl @Nullable String canonicalUrl,
      SamlConfig samlConfig,
      @LibModuleData Path libModuleDataDir) {
    this.samlConfig = samlConfig;
    this.canonicalUrl = canonicalUrl;
    this.libModuleDataDir = libModuleDataDir;
  }

  @Override
  public SAML2Client get() {
    SAML2Configuration samlClientConfig =
        new SAML2Configuration(
            samlConfig.getKeystorePath(), samlConfig.getKeystorePassword(),
            samlConfig.getPrivateKeyPassword(), samlConfig.getMetadataPath());

    if (!Strings.isNullOrEmpty(samlConfig.getIdentityProviderEntityId())) {
      if (!Strings.isNullOrEmpty(samlConfig.getServiceProviderEntityId())) {
        log.warn(
            "Both identityProviderEntityId as serviceProviderEntityId are set, ignoring serviceProviderEntityId.");
      }
      samlClientConfig.setIdentityProviderEntityId(samlConfig.getIdentityProviderEntityId());
    } else {
      samlClientConfig.setServiceProviderMetadataPath(getSpMetadataPath().toString());
      if (!Strings.isNullOrEmpty(samlConfig.getServiceProviderEntityId())) {
        samlClientConfig.setServiceProviderEntityId(samlConfig.getServiceProviderEntityId());
      }
    }

    samlClientConfig.setUseNameQualifier(samlConfig.useNameQualifier());
    samlClientConfig.setMaximumAuthenticationLifetime(samlConfig.getMaxAuthLifetimeAttr());

    if (!LOGOUT_TYPE_VALID_VALUES.contains(samlConfig.getLogoutType())) {
      log.warn(
          "logoutType is not one of the expected values ("
              + String.join(",", LOGOUT_TYPE_VALID_VALUES)
              + "), set to redirect");
      samlConfig.setLogoutType("redirect");
    }
    if (samlConfig.getLogoutType().startsWith("slo")) {
      log.debug("Setting up Single Logout (SLO) for " + samlConfig.getLogoutType());
      samlClientConfig.setSpLogoutRequestSigned(samlConfig.signSLORequest());
      if (!Strings.isNullOrEmpty(samlConfig.getPostLogoutURL())) {
        samlClientConfig.setPostLogoutURL(samlConfig.getPostLogoutURL());
      }
      // Can be set or rely on the default in pac4j
      if (samlConfig.getLogoutType().equalsIgnoreCase("slo-redirect")) {
        samlClientConfig.setSpLogoutRequestBindingType(SAMLConstants.SAML2_REDIRECT_BINDING_URI);
      } else if (samlConfig.getLogoutType().equalsIgnoreCase("slo-post")) {
        samlClientConfig.setSpLogoutRequestBindingType(SAMLConstants.SAML2_POST_BINDING_URI);
      }
    }

    SAML2Client saml2Client = new SAML2Client(samlClientConfig);

    checkNotNull(canonicalUrl, "gerrit.canonicalWebUrl must be set in gerrit.config");
    saml2Client.setCallbackUrl(canonicalUrl + SAML_CALLBACK);

    return saml2Client;
  }

  public Path getSpMetadataPath() {
    return libModuleDataDir.resolve("sp-metadata.xml");
  }
}
