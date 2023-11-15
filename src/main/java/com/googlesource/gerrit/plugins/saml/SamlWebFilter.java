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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.Iterators;
import com.google.common.collect.Sets;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.api.accounts.Accounts;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.server.config.AuthConfig;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.util.ManualRequestContext;
import com.google.gerrit.server.util.OneOffRequestContext;
import com.google.inject.Inject;
import com.google.inject.ProvisionException;
import com.google.inject.Singleton;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.pac4j.core.context.J2EContext;
import org.pac4j.core.context.session.SessionStore;
import org.pac4j.core.exception.HttpAction;
import org.pac4j.core.exception.TechnicalException;
import org.pac4j.saml.client.SAML2Client;
import org.pac4j.saml.config.SAML2Configuration;
import org.pac4j.saml.credentials.SAML2Credentials;
import org.pac4j.saml.profile.SAML2Profile;
import org.pac4j.saml.state.SAML2StateGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
class SamlWebFilter implements Filter {
  private static final Logger log = LoggerFactory.getLogger(SamlWebFilter.class);

  private static final String GERRIT_LOGOUT = "/logout";
  @VisibleForTesting static final String GERRIT_LOGIN = "/login";
  private static final String SAML = "saml";
  private static final String SAML_CALLBACK = "plugins/" + SAML + "/callback";
  @VisibleForTesting static final String SESSION_ATTR_USER = "Gerrit-Saml-User";

  private final SAML2Client saml2Client;
  private final SamlConfig samlConfig;
  private final AuthConfig auth;
  private final HashSet<String> authHeaders;
  private final SamlMembership samlMembership;
  private final GerritApi gApi;
  private final Accounts accounts;
  private final OneOffRequestContext oneOffRequestContext;

  @Inject
  SamlWebFilter(
      AuthConfig auth,
      @CanonicalWebUrl @Nullable String canonicalUrl,
      SitePaths sitePaths,
      SamlConfig samlConfig,
      SamlMembership samlMembership,
      GerritApi gApi,
      Accounts accounts,
      OneOffRequestContext oneOffRequestContext)
      throws IOException {
    this.auth = auth;
    if (auth.getHttpDisplaynameHeader() != null) {
      throw new ProvisionException(
          "auth.httpdisplaynameheader is not compatible with SAML: remove the config and restart");
    }

    this.samlConfig = samlConfig;
    this.samlMembership = samlMembership;
    log.debug("Max Authentication Lifetime: " + samlConfig.getMaxAuthLifetimeAttr());
    SAML2Configuration samlClientConfig =
        new SAML2Configuration(
            samlConfig.getKeystorePath(), samlConfig.getKeystorePassword(),
            samlConfig.getPrivateKeyPassword(), samlConfig.getMetadataPath());

    if (!Strings.isNullOrEmpty(samlConfig.getIdentityProviderEntityId())) {
      if (!Strings.isNullOrEmpty(samlConfig.getServiceProviderEntityId())) {
        log.warn(
            "Both identityProviderEntityId as serviceProviderEntityId are set, ignoring serviceProviderEntityId.");
      }
      samlClientConfig.setIdentityProviderEntityId(samlConfig.getIdentityProviderEntityId());
    } else {
      samlClientConfig.setServiceProviderMetadataPath(
          ensureExists(sitePaths.data_dir).resolve("sp-metadata.xml").toString());
      if (!Strings.isNullOrEmpty(samlConfig.getServiceProviderEntityId())) {
        samlClientConfig.setServiceProviderEntityId(samlConfig.getServiceProviderEntityId());
      }
    }

    samlClientConfig.setUseNameQualifier(samlConfig.useNameQualifier());
    samlClientConfig.setMaximumAuthenticationLifetime(samlConfig.getMaxAuthLifetimeAttr());

    saml2Client = new SAML2Client(samlClientConfig);
    authHeaders =
        Sets.newHashSet(
            auth.getLoginHttpHeader().toUpperCase(),
            auth.getHttpEmailHeader().toUpperCase(),
            auth.getHttpExternalIdHeader().toUpperCase());
    if (authHeaders.contains("") || authHeaders.contains(null)) {
      throw new RuntimeException("All authentication headers must be set.");
    }
    if (authHeaders.size() != 3) {
      throw new RuntimeException(
          "Unique values for httpUserNameHeader, "
              + "httpEmailHeader and httpExternalIdHeader are required.");
    }
    checkNotNull(canonicalUrl, "gerrit.canonicalWebUrl must be set in gerrit.config");
    saml2Client.setCallbackUrl(canonicalUrl + SAML_CALLBACK);

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
      if (isSamlPostback(httpRequest)) {
        J2EContext context = new J2EContext(httpRequest, httpResponse);
        signin(context);
      } else if (isGerritLogin(httpRequest)) {
        AuthenticatedUser user = userFromRequest(httpRequest);
        if (user == null) {
          J2EContext context = new J2EContext(httpRequest, httpResponse);
          redirectToIdentityProvider(context);
        } else {
          HttpServletRequest req = new AuthenticatedHttpRequest(httpRequest, user);

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
        }
      } else if (isGerritLogout(httpRequest)) {
        httpRequest.getSession().removeAttribute(SESSION_ATTR_USER);
        chain.doFilter(httpRequest, httpResponse);
      } else {
        chain.doFilter(httpRequest, httpResponse);
      }
    } catch (HttpAction httpAction) {
      // In pac4j v3.4.0 SLO (Single Log Out) throws HttpAction with code 200.
      // Detect that flow and recover by redirecting to the main gerrit page.
      if (httpAction.getCode() != 200) {
        throw new TechnicalException("Unexpected HTTP action", httpAction);
      }

      httpResponse.sendRedirect(httpRequest.getContextPath() + "/");
    }
  }

  private void signin(J2EContext context) throws HttpAction, IOException {
    SAML2Credentials credentials = saml2Client.getCredentials(context);
    SAML2Profile user = saml2Client.getUserProfile(credentials, context);
    if (user != null) {
      log.debug(
          "Received SAML callback for userId={} with attributes: {}",
          getUserName(user),
          user.getAttributes());
      HttpSession s = context.getRequest().getSession();
      AuthenticatedUser authenticatedUser =
          new AuthenticatedUser(
              getUserName(user),
              getDisplayName(user),
              getEmailAddress(user),
              String.format("%s/%s", SAML, user.getId()));
      s.setAttribute(SESSION_ATTR_USER, authenticatedUser);
      if (samlMembership.isEnabled()) {
        samlMembership.sync(authenticatedUser, user);
      }

      String redirectUri = context.getRequest().getParameter("RelayState");
      if (null == redirectUri || redirectUri.isEmpty()) {
        redirectUri = "/";
      }
      context.getResponse().sendRedirect(context.getRequest().getContextPath() + redirectUri);
    }
  }

  private void redirectToIdentityProvider(J2EContext context) throws HttpAction {
    String redirectUri =
        Url.decode(
            context
                .getRequest()
                .getRequestURI()
                .substring(context.getRequest().getContextPath().length()));
    @SuppressWarnings("unchecked")
    SessionStore<J2EContext> store = context.getSessionStore();
    store.set(context, SAML2StateGenerator.SAML_RELAY_STATE_ATTRIBUTE, redirectUri);
    log.debug("Setting redirectUri: {}", redirectUri);
    saml2Client.redirect(context);
  }

  private static boolean isGerritLogin(HttpServletRequest request) {
    return request.getRequestURI().indexOf(GERRIT_LOGIN) >= 0;
  }

  private static boolean isGerritLogout(HttpServletRequest request) {
    return request.getRequestURI().indexOf(GERRIT_LOGOUT) >= 0;
  }

  private static boolean isSamlPostback(HttpServletRequest request) {
    return "POST".equals(request.getMethod())
        && request.getRequestURI().indexOf(SAML_CALLBACK) >= 0;
  }

  private static String getAttribute(SAML2Profile user, String attrName) {
    // TODO(davido): Replace with the invocation from upstream method.
    List<String> names = extractAttributeValues(user, attrName);
    if (names != null && !names.isEmpty()) {
      return names.get(0);
    }
    return null;
  }

  // TODO(davido): Remove if getAttribute() uses the upstream method.
  private static List<String> extractAttributeValues(SAML2Profile user, String attrName) {
    final Object value = user.getAttribute(attrName);
    if (value instanceof String) {
      return Collections.singletonList((String) value);
    } else if (value instanceof String[]) {
      return Arrays.asList((String[]) value);
    } else if (value instanceof List) {
      return (List<String>) value;
    } else {
      return null;
    }
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
    return emailAddress;
  }

  private String getUserName(SAML2Profile user) {
    String username = getAttributeOrElseId(user, samlConfig.getUserNameAttr());
    return auth.isUserNameToLowerCase() ? username.toLowerCase(Locale.US) : username;
  }

  private static Path ensureExists(Path dataDir) throws IOException {
    return Files.createDirectories(dataDir.resolve(SAML));
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
      String nameUpperCase = name.toUpperCase();
      if (auth.getLoginHttpHeader().toUpperCase().equals(nameUpperCase)) {
        return user.getUsername();
      } else if (auth.getHttpEmailHeader().toUpperCase().equals(nameUpperCase)) {
        return user.getEmail();
      } else if (auth.getHttpExternalIdHeader().toUpperCase().equals(nameUpperCase)) {
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
        if (!authHeaders.contains(header.toUpperCase())) {
          headerNames.add(header);
        }
      }
      return Iterators.asEnumeration(headerNames.iterator());
    }

    @Override
    public String getHeader(String name) {
      String nameUpperCase = name.toUpperCase();
      if (authHeaders.contains(nameUpperCase)) {
        return null;
      }
      return super.getHeader(name);
    }
  }
}
