// Copyright (C) 2024 The Android Open Source Project
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

import com.google.common.collect.Sets;
import com.google.gerrit.server.config.AuthConfig;
import com.google.gerrit.server.config.SitePath;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.ProvisionException;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.saml.pgm.LibModuleDataDirUtil;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.pac4j.saml.client.SAML2Client;

public class Module extends AbstractModule {
  @Override
  protected void configure() {
    bind(SAML2Client.class).toProvider(SamlClientProvider.class);
  }

  @Provides
  @Singleton
  @AuthHeaders
  public Set<String> getAuthHeaders(AuthConfig auth) {
    HashSet<String> authHeaders =
        Sets.newHashSet(
            auth.getLoginHttpHeader().toUpperCase(),
            auth.getHttpEmailHeader().toUpperCase(),
            auth.getHttpExternalIdHeader().toUpperCase());

    if (authHeaders.contains("") || authHeaders.contains(null)) {
      throw new ProvisionException("All authentication headers must be set.");
    }

    if (authHeaders.size() != 3) {
      throw new ProvisionException(
          "Unique values for httpUserNameHeader, "
              + "httpEmailHeader and httpExternalIdHeader are required.");
    }

    return authHeaders;
  }

  @Provides
  @LibModuleData
  Path getLibModuleData(@SitePath Path sitePath) {
    return LibModuleDataDirUtil.createLibModuleDataDir(sitePath);
  }
}
