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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gerrit.server.account.Realm;
import com.google.gerrit.server.config.AuthConfig;
import com.google.gerrit.util.http.testutil.FakeHttpServletRequest;
import com.google.gerrit.util.http.testutil.FakeHttpServletResponse;
import java.util.Optional;
import java.util.Set;
import javax.servlet.FilterChain;
import javax.servlet.http.HttpSession;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.pac4j.core.context.CallContext;
import org.pac4j.core.credentials.Credentials;
import org.pac4j.core.exception.http.FoundAction;
import org.pac4j.core.exception.http.OkAction;
import org.pac4j.saml.client.SAML2Client;
import org.pac4j.saml.profile.SAML2Profile;
import org.pac4j.saml.state.SAML2StateGenerator;

public class SamlWebFilterTest {
  private final SAML2Client client = mock(SAML2Client.class);
  private final HttpSession session = mock(HttpSession.class);
  private final FakeHttpServletResponse response = new FakeHttpServletResponse();
  private final FilterChain chain = mock(FilterChain.class);
  private SamlWebFilter filter;

  @Before
  public void setUp() {
    SamlConfig config = mock(SamlConfig.class);
    when(config.getUserNameAttr()).thenReturn("UserName");
    when(config.getDisplayNameAttr()).thenReturn("DisplayName");
    when(config.getEmailAddressAttr()).thenReturn("EmailAddress");
    filter =
        new SamlWebFilter(
            mock(AuthConfig.class),
            mock(Realm.class),
            config,
            mock(SamlMembership.class),
            Set.of(),
            null,
            null,
            client,
            null);
  }

  private FakeHttpServletRequest request(String path, String method) {
    FakeHttpServletRequest request =
        new FakeHttpServletRequest("gerrit.example.com", 80, "", "") {
          @Override
          public HttpSession getSession() {
            return session;
          }

          @Override
          public HttpSession getSession(boolean create) {
            return session;
          }
        };
    request.setPathInfo(path);
    request.setMethod(method);
    return request;
  }

  @Test
  public void rendersIdentityProviderRedirect() throws Exception {
    when(client.getRedirectionAction(any()))
        .thenReturn(Optional.of(new FoundAction("https://idp.example.com/sso")));
    filter.doFilter(request("/login/%2Fc%2Fproject", "GET"), response, chain);
    assertThat(response.getStatus()).isEqualTo(302);
    assertThat(response.getHeader("Location")).isEqualTo("https://idp.example.com/sso");
    verify(session)
        .setAttribute(SAML2StateGenerator.SAML_RELAY_STATE_ATTRIBUTE, "/login/%2Fc%2Fproject");
    verify(chain, never()).doFilter(any(), any());
  }

  @Test
  public void preservesIdentityProviderPostForm() throws Exception {
    String form = "<html><body><form method='post'>SAMLRequest</form></body></html>";
    when(client.getRedirectionAction(any())).thenReturn(Optional.of(new OkAction(form)));
    filter.doFilter(request("/login/", "GET"), response, chain);
    response.getWriter().flush();
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getActualBodyString()).isEqualTo(form);
    assertThat(response.getHeader("Location")).isNull();
  }

  @Test
  public void rendersHttpActionThrownDuringCallback() throws Exception {
    when(client.getCredentials(any())).thenThrow(new OkAction("logout response"));
    filter.doFilter(request("/plugins/saml/callback", "POST"), response, chain);
    response.getWriter().flush();
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getActualBodyString()).isEqualTo("logout response");
    assertThat(response.getHeader("Location")).isNull();
  }

  @Test
  public void validatesCredentialsBeforeCreatingAuthenticatedSession() throws Exception {
    Credentials extracted = mock(Credentials.class);
    Credentials validated = mock(Credentials.class);
    SAML2Profile profile = new SAML2Profile();
    profile.setId("persistent-subject");
    profile.addAttribute("UserName", "alice");
    profile.addAttribute("DisplayName", "Alice");
    profile.addAttribute("EmailAddress", "alice@example.com");
    when(client.getCredentials(any())).thenReturn(Optional.of(extracted));
    when(client.validateCredentials(any(), eq(extracted))).thenReturn(Optional.of(validated));
    when(client.getUserProfile(any(), eq(validated))).thenReturn(Optional.of(profile));
    filter.doFilter(request("/plugins/saml/callback", "POST"), response, chain);
    ArgumentCaptor<AuthenticatedUser> user = ArgumentCaptor.forClass(AuthenticatedUser.class);
    verify(session).setAttribute(eq(SamlWebFilter.SESSION_ATTR_USER), user.capture());
    assertThat(user.getValue().getUsername()).isEqualTo("alice");
    assertThat(user.getValue().getDisplayName()).isEqualTo("Alice");
    assertThat(user.getValue().getEmail()).isEqualTo("alice@example.com");
    assertThat(user.getValue().getExternalId()).isEqualTo("saml/persistent-subject");
    assertThat(response.getStatus()).isEqualTo(302);
  }

  @Test
  public void rejectedCredentialsDoNotCreateAuthenticatedSession() throws Exception {
    Credentials extracted = mock(Credentials.class);
    when(client.getCredentials(any())).thenReturn(Optional.of(extracted));
    when(client.validateCredentials(any(), eq(extracted))).thenReturn(Optional.empty());
    filter.doFilter(request("/plugins/saml/callback", "POST"), response, chain);
    verify(client, never()).getUserProfile(any(CallContext.class), any());
    verify(session, never()).setAttribute(eq(SamlWebFilter.SESSION_ATTR_USER), any());
  }

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
