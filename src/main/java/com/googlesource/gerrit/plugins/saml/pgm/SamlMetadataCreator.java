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

package com.googlesource.gerrit.plugins.saml.pgm;

import com.google.gerrit.lifecycle.LifecycleManager;
import com.google.gerrit.pgm.util.SiteProgram;
import com.google.inject.Injector;
import com.googlesource.gerrit.plugins.saml.SamlClientFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.kohsuke.args4j.Option;
import org.pac4j.saml.client.SAML2Client;

public class SamlMetadataCreator extends SiteProgram {
  @Option(
      name = "--overwrite",
      usage = "Overwrite existing metadata file. Otherwise, print existing.")
  private boolean overwrite;

  private final LifecycleManager manager = new LifecycleManager();

  private Path sitePath;
  private SamlClientFactory samlClientFcatory;

  private void createSamlMetadata() throws IOException {
    Path metadataFilePath =
        Files.createDirectories(sitePath.resolve("data/saml")).resolve("sp-metadata.xml");

    if (overwrite) {
      metadataFilePath.toFile().delete();
    }

    SAML2Client saml2Client = samlClientFcatory.create();

    saml2Client.init();
    String spMetadata = saml2Client.getServiceProviderMetadataResolver().getMetadata();
    System.out.print(spMetadata);
  }

  @Override
  public int run() {
    mustHaveValidSite();
    Injector dbInjector = createDbInjector();
    manager.add(dbInjector);

    sitePath = getSitePath();
    samlClientFcatory = dbInjector.getInstance(SamlClientFactory.class);
    try {
      createSamlMetadata();
    } catch (IOException e) {
      e.printStackTrace();
      return 1;
    }
    return 0;
  }
}
