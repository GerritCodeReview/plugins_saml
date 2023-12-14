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
import static com.googlesource.gerrit.plugins.saml.SamlWebFilter.SAML;
import static com.googlesource.gerrit.plugins.saml.SamlWebFilter.SAML_CALLBACK;

import com.google.common.base.Strings;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.pac4j.saml.client.SAML2Client;
import org.pac4j.saml.config.SAML2Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class SamlClientProvider implements Provider<SAML2Client> {
  private static final Logger log = LoggerFactory.getLogger(SamlClientProvider.class);

  private final SamlConfig samlConfig;
  private final String canonicalUrl;
  private final SitePaths sitePaths;

  @Inject
  public SamlClientProvider(
      @CanonicalWebUrl @Nullable String canonicalUrl, SitePaths sitePaths, SamlConfig samlConfig) {
    this.samlConfig = samlConfig;
    this.canonicalUrl = canonicalUrl;
    this.sitePaths = sitePaths;
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
      samlClientConfig.setServiceProviderMetadataPath(
          ensureExists(sitePaths.data_dir).resolve("sp-metadata.xml").toString());
      if (!Strings.isNullOrEmpty(samlConfig.getServiceProviderEntityId())) {
        samlClientConfig.setServiceProviderEntityId(samlConfig.getServiceProviderEntityId());
      }
    }

    samlClientConfig.setUseNameQualifier(samlConfig.useNameQualifier());
    samlClientConfig.setMaximumAuthenticationLifetime(samlConfig.getMaxAuthLifetimeAttr());

    SAML2Client saml2Client = new SAML2Client(samlClientConfig);

    checkNotNull(canonicalUrl, "gerrit.canonicalWebUrl must be set in gerrit.config");
    if (canonicalUrl.endsWith("/")) {
      saml2Client.setCallbackUrl(canonicalUrl + SAML_CALLBACK);
    } else {
      saml2Client.setCallbackUrl(canonicalUrl + "/" + SAML_CALLBACK);
    }
    return saml2Client;
  }

  private static Path ensureExists(Path dataDir) {
    try {
      return Files.createDirectories(dataDir.resolve(SAML));
    } catch (IOException e) {
      throw new IllegalStateException("Unable to create data directory for the SAML-plugin.", e);
    }
  }
}
