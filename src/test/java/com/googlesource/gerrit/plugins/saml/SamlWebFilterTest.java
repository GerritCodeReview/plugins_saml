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

import com.google.gerrit.util.http.testutil.FakeHttpServletRequest;
import org.junit.Test;

public class SamlWebFilterTest {

  @Test
  public void getRedirectUriKeepsLoginDeepLinkPercentEncoded() {
    FakeHttpServletRequest req =
        new FakeHttpServletRequest("gerrit.example.com", 80, "", "")
            .setPathInfo("/login/%2Fc%2Ftest_repo%2F%2B%2F1");

    String redirectUri = SamlWebFilter.getRedirectUri(req);

    // must stay percent-encoded; decoding would produce /login//c/... whose empty
    // path segment Jetty 12 rejects as "Ambiguous URI empty segment"
    assertThat(redirectUri).isEqualTo("/login/%2Fc%2Ftest_repo%2F%2B%2F1");
    assertThat(redirectUri).doesNotContain("//");
  }
}
