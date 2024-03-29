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

package com.googlesource.gerrit.plugins.saml.pgm;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class LibModuleDataDirUtil {

  public static Path createLibModuleDataDir(Path sitePath) {
    Path dataDir = sitePath.resolve("data/saml");
    if (!Files.isDirectory(dataDir)) {
      try {
        Files.createDirectories(dataDir);
      } catch (IOException e) {
        throw new RuntimeException(String.format("Cannot create %s", dataDir.toAbsolutePath()), e);
      }
    }
    return dataDir;
  }
}
