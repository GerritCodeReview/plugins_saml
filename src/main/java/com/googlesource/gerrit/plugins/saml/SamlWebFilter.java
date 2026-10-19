// Copyright (C) 2015 The Android Open Source Project
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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Iterators;
import com.google.gerrit.entities.Account;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.api.accounts.Accounts;
import com.google.gerrit.extensions.client.AccountFieldName;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.account.Realm;
import com.google.gerrit.server.config.AuthConfig;
import com.google.gerrit.server.util.ManualRequestContext;
import com.google.gerrit.server.util.OneOffRequestContext;
import com.google.inject.Inject;
import com.google.inject.ProvisionException;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import javax.servlet.http.HttpSession;
import org.pac4j.core.context.CallContext;
import org.pac4j.core.context.session.SessionStore;
import org.pac4j.core.credentials.Credentials;
import org.pac4j.core.exception.TechnicalException;
import org.pac4j.core.exception.http.HttpAction;
import org.pac4j.jee.context.JEEContext;
import org.pac4j.jee.context.session.JEESessionStore;
import org.pac4j.jee.http.adapter.JEEHttpActionAdapter;
import org.pac4j.saml.client.SAML2Client;
import org.pac4j.saml.profile.SAML2Profile;
import org.pac4j.saml.state.SAML2StateGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
class SamlWebFilter implements Filter {
  private static final Logger log = LoggerFactory.getLogger(SamlWebFilter.class);

  private static final String GERRIT_LOGOUT = "/logout";
  @VisibleForTesting static final String GERRIT_LOGIN = "/login";
  public static final String SAML = "saml";
  public static final String SAML_CALLBACK = "plugins/" + SAML + "/callback";
  @VisibleForTesting static final String SESSION_ATTR_USER = "Gerrit-Saml-User";

  private final SAML2Client saml2Client;
  private final SessionStore sessionStore = new JEESessionStore();
  private final SamlConfig samlConfig;
  private final AuthConfig auth;
  private final Set<String> authHeaders;
  private final SamlMembership samlMembership;
  private final GerritApi gApi;
  private final Accounts accounts;
  private final OneOffRequestContext oneOffRequestContext;
  private final boolean realmAllowsFullNameEditing;

  @Inject
  SamlWebFilter(
      AuthConfig auth,
      Realm realm,
      SamlConfig samlConfig,
      SamlMembership samlMembership,
      @AuthHeaders Set<String> authHeaders,
      GerritApi gApi,
      Accounts accounts,
      SAML2Client saml2Client,
      OneOffRequestContext oneOffRequestContext) {
    this.auth = auth;
    if (auth.getHttpDisplaynameHeader() != null) {
      throw new ProvisionException(
          "auth.httpdisplaynameheader is not compatible with SAML: remove the config and restart");
    }

    this.samlConfig = samlConfig;
    this.realmAllowsFullNameEditing = realm.allowsEdit(AccountFieldName.FULL_NAME);
    this.samlMembership = samlMembership;
    log.debug("Max Authentication Lifetime: " + samlConfig.getMaxAuthLifetimeAttr());
    this.saml2Client = saml2Client;
    this.authHeaders = authHeaders;
    this.gApi = gApi;
    this.accounts = accounts;
    this.oneOffRequestContext = oneOffRequestContext;
  }

  @Override
  public void init(FilterConfig filterConfig) throws ServletException {}

  @Override
  public void destroy() {}

  private AuthenticatedUser userFromRequest(HttpServletRequest request) {
    HttpSession s = request.getSession();
    AuthenticatedUser user = (AuthenticatedUser) s.getAttribute(SESSION_ATTR_USER);
    if (user == null || user.getUsername() == null) return null;
    return user;
  }

  @Override
  public void doFilter(ServletRequest incomingRequest, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    /* The first thing we do is to wrap the request in an anonymous request, so in case
      a malicious user is trying to set the headers manually, they'll be discarded.
    */
    HttpServletRequest httpRequest = new AnonymousHttpRequest((HttpServletRequest) incomingRequest);
    HttpServletResponse httpResponse = (HttpServletResponse) response;

    try {
      if (isSamlCallback(httpRequest)) {
        handleSamlCallback(httpRequest, httpResponse, chain);
      } else if (isGerritLogin(httpRequest)) {
        handleLoginRequest(httpRequest, httpResponse, chain);
      } else if (isGerritLogout(httpRequest)) {
        handleLogoutRequest(httpRequest, httpResponse, chain);
      } else {
        chain.doFilter(httpRequest, httpResponse);
      }
    } catch (HttpAction httpAction) {
      JEEHttpActionAdapter.INSTANCE.adapt(httpAction, new JEEContext(httpRequest, httpResponse));
    }
  }

  private void handleLoginRequest(
      HttpServletRequest httpRequest, HttpServletResponse httpResponse, FilterChain chain)
      throws HttpAction, IOException, ServletException {
    AuthenticatedUser user = userFromRequest(httpRequest);
    if (user == null) {
      JEEContext context = new JEEContext(httpRequest, httpResponse);
      startSamlLogin(context);
    } else {
      HttpServletRequest req = new AuthenticatedHttpRequest(httpRequest, user);

      if (realmAllowsFullNameEditing) {
        HttpServletBufferedStatusResponse respWrapper =
            new HttpServletBufferedStatusResponse(httpResponse);
        chain.doFilter(req, respWrapper);
        try (ManualRequestContext ignored =
            oneOffRequestContext.openAs(
                Account.id(accounts.id(user.getUsername()).get()._accountId))) {
          gApi.accounts().id(user.getUsername()).setName(user.getDisplayName());
          respWrapper.commit();
        } catch (RestApiException e) {
          log.error("Saml plugin could not set account name", e);
          httpResponse.sendError(HttpServletResponse.SC_FORBIDDEN);
        }
      } else {
        chain.doFilter(req, httpResponse);
      }
    }
  }

  private void handleLogoutRequest(
      HttpServletRequest httpRequest, HttpServletResponse httpResponse, FilterChain chain)
      throws HttpAction, IOException, ServletException {
    AuthenticatedUser user = userFromRequest(httpRequest);
    if (!samlConfig.getLogoutType().isSlo() || user == null || user.getProfile() == null) {
      // Keep ordinary logout and sessions created before SLO on Gerrit's normal path.
      httpRequest.getSession().removeAttribute(SESSION_ATTR_USER);
      chain.doFilter(httpRequest, httpResponse);
    } else {
      // Revoke the Gerrit token before contacting the IdP. Keep the HTTP session
      // available for pac4j's logout request/response state.
      logoutLocally(httpRequest, httpResponse, chain);
      JEEContext context = new JEEContext(httpRequest, httpResponse);
      Optional<? extends HttpAction> action =
          saml2Client.getLogoutAction(
              new CallContext(context, sessionStore), user.getProfile(), null);
      if (action.isPresent()) {
        JEEHttpActionAdapter.INSTANCE.adapt(action.get(), context);
      } else {
        httpResponse.sendError(
            HttpServletResponse.SC_BAD_GATEWAY, "Unable to initiate SAML logout");
      }
    }
  }

  private void handleSamlCallback(
      HttpServletRequest httpRequest, HttpServletResponse httpResponse, FilterChain chain)
      throws HttpAction, IOException, ServletException {
    JEEContext context = new JEEContext(httpRequest, httpResponse);
    CallContext callContext = new CallContext(context, sessionStore);
    // pac4j 3 validated credentials during extraction. pac4j 6 requires an
    // explicit validation step before profile creation. An empty Optional
    // stops the chain before an authenticated session is created.
    Optional<Credentials> credentials =
        saml2Client
            .getCredentials(callContext)
            .flatMap(extracted -> saml2Client.validateCredentials(callContext, extracted));
    if (credentials.isEmpty()) {
      httpResponse.sendError(HttpServletResponse.SC_FORBIDDEN);
      return;
    }
    Credentials validated = credentials.get();
    // The same callback endpoint receives login and logout messages.
    // Process logout separately so it cannot create an authenticated session.
    if (validated.isForAuthentication()) {
      handleLoginCallback(httpRequest, httpResponse, callContext, validated);
    } else {
      handleLogoutCallback(context, callContext, validated, chain);
    }
  }

  private void handleLoginCallback(
      HttpServletRequest httpRequest,
      HttpServletResponse httpResponse,
      CallContext callContext,
      Credentials validated)
      throws HttpAction, IOException {
    SAML2Profile user =
        saml2Client
            .getUserProfile(callContext, validated)
            .map(SAML2Profile.class::cast)
            .orElse(null);
    if (user != null) {
      log.debug("Received authenticated SAML callback");
      HttpSession s = httpRequest.getSession();
      // Rotate the session ID before storing the authenticated identity.
      // Preserve existing session attributes.
      String oldSessionId = s.getId();
      httpRequest.changeSessionId();
      saml2Client.notifySessionRenewal(callContext, oldSessionId);
      AuthenticatedUser authenticatedUser =
          new AuthenticatedUser(
              getUserName(user),
              getDisplayName(user),
              getEmailAddress(user),
              String.format("%s/%s", SAML, user.getId()),
              user);
      s.setAttribute(SESSION_ATTR_USER, authenticatedUser);
      if (samlMembership.isEnabled()) {
        samlMembership.sync(authenticatedUser, user);
      }

      String redirectUri = httpRequest.getParameter("RelayState");
      if (null == redirectUri || redirectUri.isEmpty()) {
        redirectUri = "/";
      }
      httpResponse.sendRedirect(httpRequest.getContextPath() + redirectUri);
    }
  }

  /** True if the current HTTP session carries an authenticated SAML user. */
  private boolean hasAuthenticatedSession(HttpServletRequest request) {
    HttpSession session = request.getSession(false);
    if (session == null) {
      return false;
    }
    AuthenticatedUser user = (AuthenticatedUser) session.getAttribute(SESSION_ATTR_USER);
    return user != null && user.getUsername() != null;
  }

  private void handleLogoutCallback(
      JEEContext context, CallContext callContext, Credentials validated, FilterChain chain)
      throws HttpAction, IOException, ServletException {
    if (!samlConfig.getLogoutType().isSlo()) {
      context.getNativeResponse().sendError(HttpServletResponse.SC_FORBIDDEN);
      return;
    }
    // Only drive Gerrit's local logout chain when the request carries a real
    // authenticated SAML session. A replayed or crafted LogoutResponse that
    // passes validateCredentials must not invoke the synthetic logout servlet.
    if (hasAuthenticatedSession(context.getNativeRequest())) {
      logoutLocally(context.getNativeRequest(), context.getNativeResponse(), chain);
    }
    // Always let pac4j answer the SAML message. An IdP-initiated LogoutRequest
    // must get a LogoutResponse even without a local session, so this is not
    // gated. A stray LogoutResponse with no session is a harmless NoContent.
    JEEHttpActionAdapter.INSTANCE.adapt(saml2Client.processLogout(callContext, validated), context);
  }

  private void startSamlLogin(JEEContext context) throws HttpAction {
    String redirectUri = getRedirectUri(context.getNativeRequest());
    sessionStore.set(context, SAML2StateGenerator.SAML_RELAY_STATE_ATTRIBUTE, redirectUri);
    log.debug("Setting redirectUri: {}", redirectUri);
    HttpAction action =
        saml2Client
            .getRedirectionAction(new CallContext(context, sessionStore))
            .orElseThrow(() -> new TechnicalException("No SAML redirection action"));
    JEEHttpActionAdapter.INSTANCE.adapt(action, context);
  }

  /**
   * Returns the request URI relative to the servlet context, left percent-encoded. Decoding it
   * would turn an encoded deep-link login such as {@code /login/%2Fc%2F<change>} into {@code
   * /login//c/<change>}, whose empty path segment Jetty 12 rejects ("Ambiguous URI empty segment")
   * when the post-authentication callback redirects to it.
   */
  @VisibleForTesting
  static String getRedirectUri(HttpServletRequest request) {
    return request.getRequestURI().substring(request.getContextPath().length());
  }

  private static boolean isGerritLogin(HttpServletRequest request) {
    return request.getRequestURI().indexOf(GERRIT_LOGIN) >= 0;
  }

  private static boolean isGerritLogout(HttpServletRequest request) {
    return request.getRequestURI().equals(request.getContextPath() + GERRIT_LOGOUT);
  }

  private static boolean isSamlCallback(HttpServletRequest request) {
    return ("POST".equals(request.getMethod()) || "GET".equals(request.getMethod()))
        && request.getRequestURI().equals(request.getContextPath() + "/" + SAML_CALLBACK);
  }

  private void logoutLocally(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    HttpSession session = request.getSession(false);
    if (session != null) {
      session.removeAttribute(SESSION_ATTR_USER);
    }
    // This front-end filter runs outside Gerrit's request scope. Delegate to
    // its ordinary logout servlet for token revocation, cookie expiry and audit.
    HttpServletRequest logoutRequest =
        new HttpServletRequestWrapper(request) {
          @Override
          public String getMethod() {
            return "GET";
          }

          @Override
          public String getRequestURI() {
            return getContextPath() + GERRIT_LOGOUT;
          }

          @Override
          public String getServletPath() {
            return GERRIT_LOGOUT;
          }

          @Override
          public String getPathInfo() {
            return null;
          }
        };
    chain.doFilter(
        logoutRequest,
        new HttpServletResponseWrapper(response) {
          @Override
          public void sendRedirect(String location) {
            // Gerrit's logout servlet expires cookies and redirects without writing a body.
            // Suppress that redirect so the SAML HTTP action can supply the response.
          }
        });
    if (response.isCommitted()) {
      throw new ServletException("Gerrit local logout committed the response before SAML logout");
    }
  }

  private static String getAttribute(SAML2Profile user, String attrName) {
    List<String> names = user.extractAttributeValues(attrName);
    if (names != null && !names.isEmpty()) {
      return names.getFirst();
    }
    return null;
  }

  private static String getAttributeOrElseId(SAML2Profile user, String attrName) {
    String value = getAttribute(user, attrName);
    if (value != null) {
      return value;
    }
    return user.getId();
  }

  private String getDisplayName(SAML2Profile user) {
    if (samlConfig.isComputedDisplayName()) {
      return String.format(
          "%s %s",
          getAttributeOrElseId(user, samlConfig.getFirstNameAttr()),
          getAttributeOrElseId(user, samlConfig.getLastNameAttr()));
    }
    return getAttributeOrElseId(user, samlConfig.getDisplayNameAttr());
  }

  private String getEmailAddress(SAML2Profile user) {
    String emailAddress = getAttribute(user, samlConfig.getEmailAddressAttr());
    if (emailAddress != null) {
      return emailAddress;
    }
    String nameId = user.getId();
    if (!nameId.contains("@")) {
      log.debug(
          "Email address attribute not found, NameId {} does not look like an email.", nameId);
      return null;
    }
    return nameId;
  }

  private String getUserName(SAML2Profile user) {
    String username = getAttributeOrElseId(user, samlConfig.getUserNameAttr());
    return auth.isUserNameToLowerCase() ? username.toLowerCase(Locale.US) : username;
  }

  private class AuthenticatedHttpRequest extends HttpServletRequestWrapper {
    private AuthenticatedUser user;

    public AuthenticatedHttpRequest(HttpServletRequest request, AuthenticatedUser user) {
      super(request);
      this.user = Objects.requireNonNull(user, "user");
    }

    @Override
    public Enumeration<String> getHeaderNames() {
      Enumeration<String> wrappedHeaderNames = super.getHeaderNames();
      HashSet<String> headerNames = new HashSet<>(authHeaders);
      while (wrappedHeaderNames.hasMoreElements()) {
        headerNames.add(wrappedHeaderNames.nextElement());
      }
      return Iterators.asEnumeration(headerNames.iterator());
    }

    @Override
    public String getHeader(String name) {
      if (auth.getLoginHttpHeader().equalsIgnoreCase(name)) {
        return user.getUsername();
      } else if (auth.getHttpEmailHeader().equalsIgnoreCase(name)) {
        return user.getEmail();
      } else if (auth.getHttpExternalIdHeader().equalsIgnoreCase(name)) {
        return user.getExternalId();
      } else {
        return super.getHeader(name);
      }
    }
  }

  private class AnonymousHttpRequest extends HttpServletRequestWrapper {
    public AnonymousHttpRequest(HttpServletRequest request) {
      super(request);
    }

    @Override
    public Enumeration<String> getHeaderNames() {
      Enumeration<String> wrappedHeaderNames = super.getHeaderNames();

      HashSet<String> headerNames = new HashSet<>();
      while (wrappedHeaderNames.hasMoreElements()) {
        String header = wrappedHeaderNames.nextElement();
        if (!authHeaders.contains(header.toUpperCase(Locale.ROOT))) {
          headerNames.add(header);
        }
      }
      return Iterators.asEnumeration(headerNames.iterator());
    }

    @Override
    public String getHeader(String name) {
      String nameUpperCase = name.toUpperCase(Locale.ROOT);
      if (authHeaders.contains(nameUpperCase)) {
        return null;
      }
      return super.getHeader(name);
    }
  }
}
