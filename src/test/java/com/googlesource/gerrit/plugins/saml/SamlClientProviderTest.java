// Copyright (C) 2026 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.server.config.SitePaths;
import java.nio.file.Files;
import java.nio.file.Path;
import org.eclipse.jgit.lib.Config;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.pac4j.saml.client.SAML2Client;

public class SamlClientProviderTest {
  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void initializesOpenSamlAndGeneratesMetadataOffline() throws Exception {
    Path site = temporaryFolder.getRoot().toPath();
    Files.createDirectories(site.resolve("etc"));
    Path metadata = site.resolve("idp-metadata.xml");
    Files.writeString(
        metadata,
        """
        <EntityDescriptor xmlns="urn:oasis:names:tc:SAML:2.0:metadata"
            entityID="https://idp.example.com">
          <IDPSSODescriptor protocolSupportEnumeration="urn:oasis:names:tc:SAML:2.0:protocol">
            <SingleSignOnService
                Binding="urn:oasis:names:tc:SAML:2.0:bindings:HTTP-Redirect"
                Location="https://idp.example.com/sso"/>
          </IDPSSODescriptor>
        </EntityDescriptor>
        """);
    Config config = new Config();
    config.setString("saml", null, "metadataPath", metadata.toString());
    config.setString("saml", null, "keystorePassword", "test-password");
    config.setString("saml", null, "privateKeyPassword", "test-password");
    config.setString("saml", null, "serviceProviderEntityId", "https://gerrit.example.com/saml");
    SamlClientProvider provider =
        new SamlClientProvider(
            "https://gerrit.example.com/", new SamlConfig(config, new SitePaths(site)), site);
    SAML2Client client = provider.get();
    client.init();
    String generated = client.getServiceProviderMetadataResolver().getMetadata();
    assertThat(generated).contains("https://gerrit.example.com/saml");
    assertThat(generated).contains("https://gerrit.example.com/plugins/saml/callback");
    assertThat(Files.exists(provider.getSpMetadataPath())).isTrue();
  }
}
