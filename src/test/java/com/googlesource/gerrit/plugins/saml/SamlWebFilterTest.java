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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gerrit.server.account.Realm;
import com.google.gerrit.server.config.AuthConfig;
import com.google.gerrit.util.http.testutil.FakeHttpServletRequest;
import com.google.gerrit.util.http.testutil.FakeHttpServletResponse;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
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
  private final SamlConfig config = mock(SamlConfig.class);
  private String sessionId = "before-login";
  private SamlWebFilter filter;

  @Before
  public void setUp() throws Exception {
    when(session.getId()).thenAnswer(ignored -> sessionId);
    doAnswer(
            call -> {
              ((HttpServletResponse) call.getArgument(1))
                  .sendRedirect("https://gerrit.example.com/");
              return null;
            })
        .when(chain)
        .doFilter(any(), any());
    when(config.getLogoutType()).thenReturn(SamlConfig.LogoutType.REDIRECT);
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

          @Override
          public String changeSessionId() {
            sessionId = "after-login";
            return sessionId;
          }
        };
    request.setPathInfo(path);
    request.setMethod(method);
    return request;
  }

  @Test
  public void authenticationHeadersAreCaseInsensitive() throws Exception {
    AuthConfig auth = mock(AuthConfig.class);
    when(auth.getLoginHttpHeader()).thenReturn("X-SAML-UserName");
    when(auth.getHttpEmailHeader()).thenReturn("X-SAML-Email");
    when(auth.getHttpExternalIdHeader()).thenReturn("X-SAML-ExternalId");
    filter =
        new SamlWebFilter(
            auth,
            mock(Realm.class),
            config,
            mock(SamlMembership.class),
            new Module().getAuthHeaders(auth),
            null,
            null,
            client,
            null);
    FakeHttpServletRequest anonymous = request("/changes/", "GET");
    anonymous.addHeader("X-SAML-EMAIL", "forged@example.com");
    filter.doFilter(
        anonymous,
        response,
        (req, res) -> {
          HttpServletRequest wrapped = (HttpServletRequest) req;
          assertThat(wrapped.getHeader("X-SAML-EMAIL")).isNull();
          assertThat(Collections.list(wrapped.getHeaderNames())).doesNotContain("X-SAML-EMAIL");
        });
    when(session.getAttribute(SamlWebFilter.SESSION_ATTR_USER))
        .thenReturn(new AuthenticatedUser("alice", "Alice", "alice@example.com", "saml/alice"));
    filter.doFilter(
        request("/login/", "GET"),
        response,
        (req, res) ->
            assertThat(((HttpServletRequest) req).getHeader("X-SAML-EMAIL"))
                .isEqualTo("alice@example.com"));
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
    when(validated.isForAuthentication()).thenReturn(true);
    SAML2Profile profile = new SAML2Profile();
    profile.setId("persistent-subject");
    profile.addAttribute("UserName", "alice");
    profile.addAttribute("DisplayName", "Alice");
    profile.addAttribute("EmailAddress", "alice@example.com");
    when(client.getCredentials(any())).thenReturn(Optional.of(extracted));
    when(client.validateCredentials(any(), eq(extracted))).thenReturn(Optional.of(validated));
    when(client.getUserProfile(any(), eq(validated))).thenReturn(Optional.of(profile));
    doAnswer(
            ignored -> {
              assertThat(session.getId()).isEqualTo("after-login");
              return null;
            })
        .when(session)
        .setAttribute(eq(SamlWebFilter.SESSION_ATTR_USER), any());
    filter.doFilter(request("/plugins/saml/callback", "POST"), response, chain);
    ArgumentCaptor<AuthenticatedUser> user = ArgumentCaptor.forClass(AuthenticatedUser.class);
    InOrder order = inOrder(client, session);
    order.verify(client).getCredentials(any());
    order.verify(client).validateCredentials(any(), eq(extracted));
    order.verify(client).getUserProfile(any(), eq(validated));
    order.verify(client).notifySessionRenewal(any(), eq("before-login"));
    order.verify(session).setAttribute(eq(SamlWebFilter.SESSION_ATTR_USER), user.capture());
    assertThat(user.getValue().getUsername()).isEqualTo("alice");
    assertThat(user.getValue().getDisplayName()).isEqualTo("Alice");
    assertThat(user.getValue().getEmail()).isEqualTo("alice@example.com");
    assertThat(user.getValue().getExternalId()).isEqualTo("saml/persistent-subject");
    assertThat(user.getValue().getProfile()).isSameInstanceAs(profile);
    assertThat(response.getStatus()).isEqualTo(302);
  }

  @Test
  public void sessionRotationFailureDoesNotPublishAuthenticatedIdentity() throws Exception {
    Credentials credentials = mock(Credentials.class);
    when(credentials.isForAuthentication()).thenReturn(true);
    SAML2Profile profile = new SAML2Profile();
    profile.setId("alice");
    when(client.getCredentials(any())).thenReturn(Optional.of(credentials));
    when(client.validateCredentials(any(), eq(credentials))).thenReturn(Optional.of(credentials));
    when(client.getUserProfile(any(), eq(credentials))).thenReturn(Optional.of(profile));
    FakeHttpServletRequest request = spy(request("/plugins/saml/callback", "POST"));
    doThrow(new IllegalStateException("rotation failed")).when(request).changeSessionId();
    assertThrows(IllegalStateException.class, () -> filter.doFilter(request, response, chain));
    verify(session, never()).setAttribute(eq(SamlWebFilter.SESSION_ATTR_USER), any());
    verify(client, never()).notifySessionRenewal(any(), any());
  }

  @Test
  public void rejectedCredentialsDoNotCreateAuthenticatedSession() throws Exception {
    Credentials extracted = mock(Credentials.class);
    when(client.getCredentials(any())).thenReturn(Optional.of(extracted));
    when(client.validateCredentials(any(), eq(extracted))).thenReturn(Optional.empty());
    filter.doFilter(request("/plugins/saml/callback", "POST"), response, chain);
    verify(client, never()).getUserProfile(any(CallContext.class), any());
    verify(session, never()).setAttribute(eq(SamlWebFilter.SESSION_ATTR_USER), any());
    assertThat(session.getId()).isEqualTo("before-login");
    verify(client, never()).notifySessionRenewal(any(), any());
  }

  private SAML2Profile enableSlo(SamlConfig.LogoutType type) {
    when(config.getLogoutType()).thenReturn(type);
    SAML2Profile profile = new SAML2Profile();
    profile.setId("subject");
    when(session.getAttribute(SamlWebFilter.SESSION_ATTR_USER))
        .thenReturn(
            new AuthenticatedUser("alice", "Alice", "alice@example.com", "saml/subject", profile));
    return profile;
  }

  @Test
  public void ordinaryLogoutUsesGerritServlet() throws Exception {
    filter.doFilter(request("/logout", "GET"), response, chain);
    verify(session).removeAttribute(SamlWebFilter.SESSION_ATTR_USER);
    verify(chain).doFilter(any(), any());
    verify(client, never()).getLogoutAction(any(), any(), any());
  }

  @Test
  public void sessionWithoutProfileFallsBackToOrdinaryLogout() throws Exception {
    when(config.getLogoutType()).thenReturn(SamlConfig.LogoutType.SLO_POST);
    when(session.getAttribute(SamlWebFilter.SESSION_ATTR_USER))
        .thenReturn(new AuthenticatedUser("alice", "Alice", "alice@example.com", "saml/subject"));
    filter.doFilter(request("/logout", "GET"), response, chain);
    verify(chain).doFilter(any(), any());
    verify(client, never()).getLogoutAction(any(), any(), any());
  }

  @Test
  public void sloPostDelegatesLocalLogoutBeforeRenderingForm() throws Exception {
    SAML2Profile profile = enableSlo(SamlConfig.LogoutType.SLO_POST);
    String form = "<form method='post'>LogoutRequest</form>";
    when(client.getLogoutAction(any(), eq(profile), isNull()))
        .thenReturn(Optional.of(new OkAction(form)));
    filter.doFilter(request("/logout", "GET"), response, chain);
    InOrder order = inOrder(chain, client);
    order.verify(chain).doFilter(any(), any());
    order.verify(client).getLogoutAction(any(), eq(profile), isNull());
    response.getWriter().flush();
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getActualBodyString()).isEqualTo(form);
    assertThat(response.getHeader("Location")).isNull();
    verify(session, never()).invalidate();
    assertThat(session.getId()).isEqualTo("before-login");
  }

  @Test
  public void sloRedirectDelegatesLocalLogoutAndRedirectsToIdp() throws Exception {
    SAML2Profile profile = enableSlo(SamlConfig.LogoutType.SLO_REDIRECT);
    when(client.getLogoutAction(any(), eq(profile), isNull()))
        .thenReturn(Optional.of(new FoundAction("https://idp.example.com/logout")));
    filter.doFilter(request("/logout", "GET"), response, chain);
    verify(chain).doFilter(any(), any());
    assertThat(response.getStatus()).isEqualTo(302);
    assertThat(response.getHeader("Location")).isEqualTo("https://idp.example.com/logout");
  }

  @Test
  public void failedSloStillDelegatesLocalLogout() throws Exception {
    SAML2Profile profile = enableSlo(SamlConfig.LogoutType.SLO_POST);
    when(client.getLogoutAction(any(), eq(profile), isNull())).thenReturn(Optional.empty());
    filter.doFilter(request("/logout", "GET"), response, chain);
    verify(chain).doFilter(any(), any());
    assertThat(response.getStatus()).isEqualTo(502);
  }

  @Test
  public void sloDoesNotRenderActionAfterLocalLogoutCommitsResponse() throws Exception {
    enableSlo(SamlConfig.LogoutType.SLO_POST);
    commitLocalLogoutResponse();
    ServletException error =
        assertThrows(
            ServletException.class,
            () -> filter.doFilter(request("/logout", "GET"), response, chain));
    assertThat(error).hasMessageThat().contains("local logout committed the response");
    assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
    verify(client, never()).getLogoutAction(any(), any(), any());
  }

  @Test
  public void logoutCallbackDoesNotRenderActionAfterLocalLogoutCommitsResponse() throws Exception {
    prepareLogoutCallback();
    commitLocalLogoutResponse();
    assertThrows(
        ServletException.class,
        () -> filter.doFilter(request("/plugins/saml/callback", "POST"), response, chain));
    assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
    verify(client, never()).processLogout(any(), any());
  }

  private void commitLocalLogoutResponse() throws Exception {
    doAnswer(
            call -> {
              ((HttpServletResponse) call.getArgument(1))
                  .sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
              return null;
            })
        .when(chain)
        .doFilter(any(), any());
  }

  private Credentials prepareLogoutCallback() {
    when(config.getLogoutType()).thenReturn(SamlConfig.LogoutType.SLO_POST);
    SAML2Profile profile = new SAML2Profile();
    profile.setId("subject");
    when(session.getAttribute(SamlWebFilter.SESSION_ATTR_USER))
        .thenReturn(
            new AuthenticatedUser("alice", "Alice", "alice@example.com", "saml/subject", profile));
    Credentials extracted = mock(Credentials.class);
    Credentials validated = mock(Credentials.class);
    when(client.getCredentials(any())).thenReturn(Optional.of(extracted));
    when(client.validateCredentials(any(), eq(extracted))).thenReturn(Optional.of(validated));
    return validated;
  }

  @Test
  public void redirectLogoutCallbackIsValidatedAndProcessed() throws Exception {
    Credentials validated = prepareLogoutCallback();
    when(client.processLogout(any(), eq(validated)))
        .thenReturn(new FoundAction("https://gerrit.example.com/"));
    filter.doFilter(request("/plugins/saml/callback", "GET"), response, chain);
    ArgumentCaptor<HttpServletRequest> logoutRequest =
        ArgumentCaptor.forClass(HttpServletRequest.class);
    verify(chain).doFilter(logoutRequest.capture(), any());
    assertThat(logoutRequest.getValue().getRequestURI()).isEqualTo("/logout");
    assertThat(logoutRequest.getValue().getMethod()).isEqualTo("GET");
    verify(client, never()).getUserProfile(any(), any());
    assertThat(response.getStatus()).isEqualTo(302);
    assertThat(response.getHeader("Location")).isEqualTo("https://gerrit.example.com/");
  }

  @Test
  public void postLogoutCallbackPreservesIdpResponseForm() throws Exception {
    Credentials validated = prepareLogoutCallback();
    String form = "<form>LogoutResponse</form>";
    when(client.processLogout(any(), eq(validated))).thenReturn(new OkAction(form));
    filter.doFilter(request("/plugins/saml/callback", "POST"), response, chain);
    verify(chain).doFilter(any(), any());
    response.getWriter().flush();
    assertThat(response.getActualBodyString()).isEqualTo(form);
    assertThat(response.getHeader("Location")).isNull();
  }

  @Test
  public void invalidLogoutCredentialsDoNotDelegateLocalLogout() throws Exception {
    when(config.getLogoutType()).thenReturn(SamlConfig.LogoutType.SLO_POST);
    Credentials extracted = mock(Credentials.class);
    when(client.getCredentials(any())).thenReturn(Optional.of(extracted));
    when(client.validateCredentials(any(), eq(extracted))).thenReturn(Optional.empty());
    filter.doFilter(request("/plugins/saml/callback", "POST"), response, chain);
    verify(chain, never()).doFilter(any(), any());
    verify(client, never()).processLogout(any(), any());
    assertThat(response.getStatus()).isEqualTo(403);
  }

  @Test
  public void logoutCallbackIsRejectedWhenSloIsDisabled() throws Exception {
    prepareLogoutCallback();
    when(config.getLogoutType()).thenReturn(SamlConfig.LogoutType.REDIRECT);
    filter.doFilter(request("/plugins/saml/callback", "POST"), response, chain);
    verify(chain, never()).doFilter(any(), any());
    verify(client, never()).processLogout(any(), any());
    assertThat(response.getStatus()).isEqualTo(403);
  }

  @Test
  public void logoutCallbackWithoutActiveSessionSkipsLocalLogout() throws Exception {
    Credentials validated = prepareLogoutCallback();
    when(session.getAttribute(SamlWebFilter.SESSION_ATTR_USER)).thenReturn(null);
    when(client.processLogout(any(), eq(validated))).thenReturn(new OkAction("ok"));
    filter.doFilter(request("/plugins/saml/callback", "POST"), response, chain);
    // No authenticated session, so the synthetic Gerrit logout must not run.
    verify(chain, never()).doFilter(any(), any());
    // The SAML message is still answered. An IdP LogoutRequest needs its response.
    verify(client).processLogout(any(), eq(validated));
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
