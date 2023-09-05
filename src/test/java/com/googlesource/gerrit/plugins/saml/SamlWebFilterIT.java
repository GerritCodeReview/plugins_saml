package com.googlesource.gerrit.plugins.saml;

import static com.google.common.base.Strings.nullToEmpty;
import static com.google.common.truth.Truth.assertThat;
import static javax.servlet.http.HttpServletResponse.SC_OK;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.extensions.common.AccountDetailInfo;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.testing.ConfigSuite;
import com.google.gerrit.util.http.testutil.FakeHttpServletRequest;
import com.google.gerrit.util.http.testutil.FakeHttpServletResponse;
import java.io.IOException;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.junit.Test;

public class SamlWebFilterIT extends AbstractDaemonTest {

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
  public void supportAccountNamesWithNonIso88591Characters() throws IOException, ServletException, RestApiException {
    SamlWebFilter samlWebFilter = server.getTestInjector().getInstance(SamlWebFilter.class);

    String samlDisplayName = nullToEmpty(user.displayName()) + " Saml Test 合覺那加情力心";

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
    assertThat(account.name).isEqualTo(samlDisplayName);
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
