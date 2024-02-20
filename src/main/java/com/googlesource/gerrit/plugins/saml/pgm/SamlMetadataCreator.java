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

import static com.googlesource.gerrit.plugins.saml.pgm.LibModuleDataDirUtil.createLibModuleDataDir;

import com.google.gerrit.server.config.SitePaths;
import com.googlesource.gerrit.plugins.saml.SamlClientProvider;
import com.googlesource.gerrit.plugins.saml.SamlConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.ParserProperties;
import org.pac4j.saml.client.SAML2Client;

public class SamlMetadataCreator {
  @Option(
      name = "--overwrite",
      usage = "Overwrite existing metadata file. Otherwise, print existing.")
  private boolean overwrite;

  @Option(
      name = "--site-path",
      aliases = {"-d"},
      usage = "Local directory containing site data")
  void setSitePath(String path) {
    sitePath = Paths.get(path).normalize();
  }

  private Path sitePath = Paths.get(".").toAbsolutePath();
  private SitePaths sitePaths;
  private SamlClientProvider samlClientProvider;

  private void createSamlMetadata() throws IOException {
    Path spMetadataPath = samlClientProvider.getSpMetadataPath();
    if (overwrite && spMetadataPath.toFile().exists()) {
      Files.delete(spMetadataPath);
    }

    SAML2Client saml2Client = samlClientProvider.get();

    saml2Client.init();
    String spMetadata = saml2Client.getServiceProviderMetadataResolver().getMetadata();
    System.out.print(spMetadata);
  }

  private Config parseGerritConfig() throws ConfigInvalidException, IOException {
    Config baseConfig = new Config();
    baseConfig.fromText(Files.readString(sitePaths.gerrit_config));

    Config cfg = new Config(baseConfig);

    if (sitePaths.secure_config.toFile().exists()) {
      cfg.fromText(Files.readString(sitePaths.secure_config));
    }

    return cfg;
  }

  public void run(String[] args) throws IOException, ConfigInvalidException {
    CmdLineParser parser = new CmdLineParser(this, ParserProperties.defaults().withAtSyntax(false));
    try {
      parser.parseArgument(args);
    } catch (CmdLineException e) {
      System.err.println(e.getMessage());
      parser.printUsage(System.err);
      System.exit(1);
      return;
    }

    sitePaths = new SitePaths(sitePath);
    try {
      Config cfg = parseGerritConfig();
      String canonicalWebUrl = cfg.getString("gerrit", null, "canonicalWebUrl");
      samlClientProvider =
          new SamlClientProvider(
              canonicalWebUrl, new SamlConfig(cfg, sitePaths), createLibModuleDataDir(sitePath));
    } catch (ConfigInvalidException | IOException e) {
      throw new ConfigInvalidException("Unable to parse Gerrit's configuration.", e);
    }
    createSamlMetadata();
  }

  public static void main(String[] args) throws Exception {
    new SamlMetadataCreator().run(args);
  }
}
