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

import static com.google.common.base.Strings.nullToEmpty;
import static com.google.common.truth.Truth.assertThat;
import static javax.servlet.http.HttpServletResponse.SC_FORBIDDEN;
import static javax.servlet.http.HttpServletResponse.SC_OK;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.api.accounts.Accounts;
import com.google.gerrit.extensions.client.AccountFieldName;
import com.google.gerrit.extensions.common.AccountDetailInfo;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.ServerInitiated;
import com.google.gerrit.server.account.AccountsUpdate;
import com.google.gerrit.server.account.Realm;
import com.google.gerrit.server.config.AuthConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.util.OneOffRequestContext;
import com.google.gerrit.testing.ConfigSuite;
import com.google.gerrit.util.http.testutil.FakeHttpServletRequest;
import com.google.gerrit.util.http.testutil.FakeHttpServletResponse;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.servlet.FilterChain;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.junit.Test;

public class SamlWebFilterIT extends AbstractDaemonTest {

  @Inject @ServerInitiated private Provider<AccountsUpdate> accountsUpdateProvider;

  @ConfigSuite.Default
  public static Config setupSaml() throws ConfigInvalidException {
    Config cfg = new Config();
    cfg.fromText(
        ""
            + "[httpd]\n"
            + "    filterClass = com.googlesource.gerrit.plugins.saml.SamlWebFilter\n"
            + "[saml]\n"
            + "    keystorePath = etc/samlKeystore.jks\n"
            + "    metadataPath = http://localhost:8080/auth/realms/master/protocol/saml/descriptor\n"
            + "[auth]\n"
            + "    type = HTTP\n"
            + "    httpHeader = X-SAML-UserName\n"
            + "    httpEmailHeader = X-SAML-EmailHeader\n"
            + "    httpExternalIdHeader = X-SAML-ExternalId");
    return cfg;
  }

  @Test
  public void supportAccountNamesWithNonIso88591Characters() throws Exception {
    SamlWebFilter samlWebFilter = server.getTestInjector().getInstance(SamlWebFilter.class);

    String samlDisplayName = nullToEmpty(user.displayName()) + " Saml Test 合覺那加情力心";

    HttpSession httpSession = mock(HttpSession.class);
    AuthenticatedUser authenticatedUser =
        new AuthenticatedUser(user.username(), samlDisplayName, user.email(), "externalId");
    doReturn(authenticatedUser).when(httpSession).getAttribute(SamlWebFilter.SESSION_ATTR_USER);

    FakeHttpServletRequest req = new FakeHttpServletRequestWithSession(httpSession);
    req.setPathInfo(SamlWebFilter.GERRIT_LOGIN);
    HttpServletResponse res = new FakeHttpServletResponse();

    samlWebFilter.doFilter(req, res, mockFilterReturningStatusOK());
    assertThat(res.getStatus()).isEqualTo(SC_OK);

    AccountDetailInfo account = gApi.accounts().id(user.username()).detail();
    assertThat(account.name).isEqualTo(samlDisplayName);
  }

  @Test
  public void failAuthenticationWhenAccountManipulationFails() throws Exception {
    SamlWebFilter samlWebFilter =
        newSamlWebFilter(
            server.getTestInjector().getInstance(Realm.class),
            newGerritApiMockFailingOnAccountsApi());
    List<Integer> responseStatuses = new ArrayList<>();

    HttpServletResponse res =
        new FakeHttpServletResponse() {
          @Override
          public synchronized void setStatus(int sc) {
            super.setStatus(sc);
            responseStatuses.add(sc);
          }

          @SuppressWarnings("deprecation")
          @Override
          public synchronized void setStatus(int sc, String msg) {
            super.setStatus(sc, msg);
            responseStatuses.add(sc);
          }

          @Override
          public synchronized void sendError(int sc) {
            super.sendError(sc);
            responseStatuses.add(sc);
          }

          @Override
          public synchronized void sendError(int sc, String msg) {
            super.sendError(sc, msg);
            responseStatuses.add(sc);
          }
        };

    samlWebFilter.doFilter(newFinalLoginFakeHttpRequest(), res, mockFilterReturningStatusOK());

    assertThat(res.getStatus()).isEqualTo(SC_FORBIDDEN);
    assertThat(responseStatuses).containsExactly(SC_FORBIDDEN);
  }

  private FilterChain mockFilterReturningStatusOK() {
    return (request, response) ->
        ((HttpServletResponse) response).setStatus(HttpServletResponse.SC_OK);
  }

  private FakeHttpServletRequest newFinalLoginFakeHttpRequest() {
    HttpSession httpSession = mock(HttpSession.class);
    AuthenticatedUser authenticatedUser =
        new AuthenticatedUser(user.username(), "User Fullname", user.email(), "externalId");
    doReturn(authenticatedUser).when(httpSession).getAttribute(SamlWebFilter.SESSION_ATTR_USER);
    FakeHttpServletRequest req = new FakeHttpServletRequestWithSession(httpSession);
    req.setPathInfo(SamlWebFilter.GERRIT_LOGIN);
    return req;
  }

  private GerritApi newGerritApiMockFailingOnAccountsApi() throws RestApiException {
    GerritApi apiMock = mock(GerritApi.class);
    Accounts accountsApiMock = mock(Accounts.class);
    doReturn(accountsApiMock).when(apiMock).accounts();
    doThrow(RestApiException.class).when(accountsApiMock).id(any());
    return apiMock;
  }

  private SamlWebFilter newSamlWebFilter(Realm realm, GerritApi gerritApi) throws IOException {
    Injector testInjector = server.getTestInjector();
    return new SamlWebFilter(
        testInjector.getInstance(AuthConfig.class),
        realm,
        "",
        testInjector.getInstance(SitePaths.class),
        testInjector.getInstance(SamlConfig.class),
        testInjector.getInstance(SamlMembership.class),
        gerritApi,
        testInjector.getInstance(Accounts.class),
        testInjector.getInstance(OneOffRequestContext.class));
  }

  @Test
  public void shouldSucceedAndNotSetFullNameWhenNotAllowedByRealm() throws Exception {
    Realm realmMock = mock(Realm.class);
    doReturn(false).when(realmMock).allowsEdit(eq(AccountFieldName.FULL_NAME));
    SamlWebFilter samlWebFilter = newSamlWebFilter(realmMock, gApi);

    String samlDisplayName = "Test display name";

    HttpSession httpSession = mock(HttpSession.class);
    AuthenticatedUser authenticatedUser =
        new AuthenticatedUser(user.username(), samlDisplayName, user.email(), "externalId");
    doReturn(authenticatedUser).when(httpSession).getAttribute(SamlWebFilter.SESSION_ATTR_USER);

    FakeHttpServletRequest req = new FakeHttpServletRequestWithSession(httpSession);
    req.setPathInfo(SamlWebFilter.GERRIT_LOGIN);
    HttpServletResponse res = new FakeHttpServletResponse();

    samlWebFilter.doFilter(req, res, mock(FilterChain.class));
    assertThat(res.getStatus()).isEqualTo(SC_OK);

    AccountDetailInfo account = gApi.accounts().id(user.username()).detail();
    assertThat(account.name).isNotEqualTo(samlDisplayName);
  }

  private static class FakeHttpServletRequestWithSession extends FakeHttpServletRequest {
    HttpSession session;

    public FakeHttpServletRequestWithSession(HttpSession session) {
      super();
      this.session = session;
    }

    @Override
    public HttpSession getSession() {
      return session;
    }
  }
}
