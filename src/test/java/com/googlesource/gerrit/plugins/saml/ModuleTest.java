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
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.gerrit.server.config.AuthConfig;
import com.google.inject.ProvisionException;
import org.junit.Test;

public class ModuleTest {
  @Test
  public void rejectsMissingHeadersWithConfigurationError() {
    for (String missing : new String[] {null, ""}) {
      for (int index = 0; index < 3; index++) {
        String[] headers = {"X-User", "X-Email", "X-ExternalId"};
        headers[index] = missing;
        AuthConfig auth = authConfig(headers[0], headers[1], headers[2]);
        ProvisionException error =
            assertThrows(ProvisionException.class, () -> new Module().getAuthHeaders(auth));
        assertThat(error).hasMessageThat().contains("All authentication headers must be set.");
      }
    }
  }

  @Test
  public void rejectsCaseInsensitiveDuplicateHeaders() {
    AuthConfig auth = authConfig("X-User", "x-user", "X-ExternalId");
    ProvisionException error =
        assertThrows(ProvisionException.class, () -> new Module().getAuthHeaders(auth));
    assertThat(error).hasMessageThat().contains("Unique values");
  }

  @Test
  public void normalizesAuthenticationHeaders() {
    assertThat(new Module().getAuthHeaders(authConfig("X-User", "X-Email", "X-ExternalId")))
        .containsExactly("X-USER", "X-EMAIL", "X-EXTERNALID");
  }

  private static AuthConfig authConfig(String user, String email, String externalId) {
    AuthConfig auth = mock(AuthConfig.class);
    when(auth.getLoginHttpHeader()).thenReturn(user);
    when(auth.getHttpEmailHeader()).thenReturn(email);
    when(auth.getHttpExternalIdHeader()).thenReturn(externalId);
    return auth;
  }
}
