// Copyright (C) 2019 The Android Open Source Project
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

package com.googlesource.gerrit.plugins.saml.ldap;

import com.google.common.base.Strings;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.data.ParameterizedString;
import com.google.gerrit.server.account.AccountException;
import com.google.gerrit.server.auth.NoSuchUserException;
import com.google.gerrit.server.config.ConfigUtil;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.util.ssl.BlindHostnameVerifier;
import com.google.gerrit.util.ssl.BlindSSLSocketFactory;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapContext;
import javax.naming.ldap.StartTlsRequest;
import javax.naming.ldap.StartTlsResponse;
import javax.net.ssl.SSLSocketFactory;
import org.eclipse.jgit.lib.Config;

@Singleton
public class LdapHelper {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  static final String LDAP_UUID = "ldap:";
  static final String LDAP = "com.sun.jndi.ldap.LdapCtxFactory";
  static final String USERNAME = "username";

  private final Config config;
  private final String server;
  private final String username;
  private final String password;
  private final String referral;
  private final boolean startTls;
  private final boolean sslVerify;
  private volatile LdapSchema ldapSchema;
  private final String readTimeoutMillis;
  private final String connectTimeoutMillis;
  private final boolean useConnectionPooling;

  @Inject
  LdapHelper(@GerritServerConfig Config config) {
    this.config = config;
    this.server = optional(config, "server");
    this.username = optional(config, "username");
    this.password = optional(config, "password", "");
    this.referral = optional(config, "referral", "ignore");
    this.startTls = config.getBoolean("ldap", "startTls", false);
    this.sslVerify = config.getBoolean("ldap", "sslverify", true);
    String readTimeout = optional(config, "readTimeout");
    if (readTimeout != null) {
      readTimeoutMillis =
          Long.toString(ConfigUtil.getTimeUnit(readTimeout, 0, TimeUnit.MILLISECONDS));
    } else {
      readTimeoutMillis = null;
    }
    String connectTimeout = optional(config, "connectTimeout");
    if (connectTimeout != null) {
      connectTimeoutMillis =
          Long.toString(ConfigUtil.getTimeUnit(connectTimeout, 0, TimeUnit.MILLISECONDS));
    } else {
      connectTimeoutMillis = null;
    }
    this.useConnectionPooling = optional(config, "useConnectionPooling", false);
  }

  private Properties createContextProperties() {
    final Properties env = new Properties();
    env.put(Context.INITIAL_CONTEXT_FACTORY, LDAP);
    env.put(Context.PROVIDER_URL, server);
    if (server.startsWith("ldaps:") && !sslVerify) {
      Class<? extends SSLSocketFactory> factory = BlindSSLSocketFactory.class;
      env.put("java.naming.ldap.factory.socket", factory.getName());
    }
    if (readTimeoutMillis != null) {
      env.put("com.sun.jndi.ldap.read.timeout", readTimeoutMillis);
    }
    if (connectTimeoutMillis != null) {
      env.put("com.sun.jndi.ldap.connect.timeout", connectTimeoutMillis);
    }
    if (useConnectionPooling) {
      env.put("com.sun.jndi.ldap.connect.pool", "true");
    }
    return env;
  }

  private LdapContext createContext(Properties env) throws IOException, NamingException {
    LdapContext ctx = new InitialLdapContext(env, null);
    if (startTls) {
      StartTlsResponse tls = (StartTlsResponse) ctx.extendedOperation(new StartTlsRequest());
      SSLSocketFactory sslfactory = null;
      if (!sslVerify) {
        sslfactory = (SSLSocketFactory) BlindSSLSocketFactory.getDefault();
        tls.setHostnameVerifier(BlindHostnameVerifier.getInstance());
      }
      tls.negotiate(sslfactory);
    }
    return ctx;
  }

  public void close(DirContext ctx) {
    try {
      ctx.close();
    } catch (NamingException e) {
      logger.atWarning().withCause(e).log("Cannot close LDAP handle");
    }
  }

  public String lookupUserNameForId(String id) {
    try {
      DirContext ctx = open();
      try {
        return findAccount(getSchema(ctx), ctx, id).getUsername();
      } finally {
        close(ctx);
      }
    } catch (AccountException | IOException | NamingException e) {
      logger.atSevere().withCause(e).log("Cannot query LDAP to retrieve user id: %s", id);
      throw new IllegalStateException("Cannot retrieve user id", e);
    }
  }

  public DirContext open() throws IOException, NamingException {
    final Properties env = createContextProperties();
    env.put(Context.REFERRAL, referral);
    LdapContext ctx = createContext(env);
    if (username != null) {
      ctx.addToEnvironment(Context.SECURITY_PRINCIPAL, username);
      ctx.addToEnvironment(Context.SECURITY_CREDENTIALS, password);
      ctx.reconnect(null);
    }
    return ctx;
  }

  public LdapSchema getSchema(DirContext ctx) {
    if (ldapSchema == null) {
      synchronized (this) {
        if (ldapSchema == null) {
          ldapSchema = new LdapSchema(ctx);
        }
      }
    }
    return ldapSchema;
  }

  public LdapQuery.Result findAccount(LdapHelper.LdapSchema schema, DirContext ctx, String value)
      throws NamingException, AccountException {
    final HashMap<String, String> params = new HashMap<>();
    params.put(USERNAME, value);

    List<LdapQuery> accountQueryList = schema.accountQueryList;

    for (LdapQuery accountQuery : accountQueryList) {
      List<LdapQuery.Result> res = accountQuery.query(ctx, params);
      if (res.size() == 1) {
        return res.get(0);
      } else if (res.size() > 1) {
        throw new AccountException("Duplicate users: " + username);
      }
    }
    throw new NoSuchUserException(username);
  }

  public class LdapSchema {
    final LdapType type;

    final ParameterizedString accountSshUserName;
    final List<LdapQuery> accountQueryList;

    LdapSchema(DirContext ctx) {
      type = discoverLdapType(ctx);
      accountQueryList = new ArrayList<>();

      final Set<String> accountAtts = new HashSet<>();

      // Account user name
      //
      accountSshUserName = paramString(config, "accountSshUserName", type.accountSshUserName());
      if (accountSshUserName != null) {
        accountAtts.addAll(accountSshUserName.getParameterNames());
      }

      SearchScope accountScope = scope(config, "accountScope");
      String userNameLdapQuery = config.getString("saml", null, "userNameLdapQuery");
      if (Strings.isNullOrEmpty(userNameLdapQuery)) {
        throw new IllegalStateException("ldap.userNameLdapQuery must be provided");
      }

      for (String accountBase : requiredList(config, "accountBase")) {
        LdapQuery accountQuery =
            new LdapQuery(
                accountBase, accountScope, new ParameterizedString(userNameLdapQuery), accountAtts);
        if (accountQuery.getParameters().isEmpty()) {
          throw new IllegalArgumentException("No variables in ldap.userNameLdapQuery");
        }
        accountQueryList.add(accountQuery);
      }
    }

    LdapType discoverLdapType(DirContext ctx) {
      try {
        return LdapType.guessType(ctx);
      } catch (NamingException e) {
        logger.atWarning().withCause(e).log(
            "Cannot discover type of LDAP server at %s,"
                + " assuming the server is RFC 2307 compliant.",
            server);
        return LdapType.RFC_2307;
      }
    }
  }

  static String optional(Config config, String name) {
    return config.getString("ldap", null, name);
  }

  static int optional(Config config, String name, int defaultValue) {
    return config.getInt("ldap", name, defaultValue);
  }

  static String optional(Config config, String name, String defaultValue) {
    final String v = optional(config, name);
    if (Strings.isNullOrEmpty(v)) {
      return defaultValue;
    }
    return v;
  }

  static boolean optional(Config config, String name, boolean defaultValue) {
    return config.getBoolean("ldap", name, defaultValue);
  }

  private static void checkBackendCompliance(
      String configOption, String suppliedValue, boolean disabledByBackend) {
    if (disabledByBackend && !Strings.isNullOrEmpty(suppliedValue)) {
      String msg = String.format("LDAP backend doesn't support: ldap.%s", configOption);
      logger.atSevere().log(msg);
      throw new IllegalArgumentException(msg);
    }
  }

  static String optdef(Config c, String n, String d) {
    final String[] v = c.getStringList("ldap", null, n);
    if (v == null || v.length == 0) {
      return d;

    } else if (v[0] == null || "".equals(v[0])) {
      return null;

    } else {
      checkBackendCompliance(n, v[0], Strings.isNullOrEmpty(d));
      return v[0];
    }
  }

  static String optdefSaml(Config c, String n, String d) {
    final String[] v = c.getStringList("saml", null, n);
    if (v == null || v.length == 0) {
      return d;

    } else if (v[0] == null || "".equals(v[0])) {
      return null;

    } else {
      checkBackendCompliance(n, v[0], Strings.isNullOrEmpty(d));
      return v[0];
    }
  }

  static ParameterizedString paramString(Config c, String n, String d) {
    String expression = optdef(c, n, d);
    if (expression == null) {
      return null;
    } else if (expression.contains("${")) {
      return new ParameterizedString(expression);
    } else {
      return new ParameterizedString("${" + expression + "}");
    }
  }

  static List<String> optionalList(Config config, String name) {
    String[] s = config.getStringList("ldap", null, name);
    return Arrays.asList(s);
  }

  static List<String> requiredList(Config config, String name) {
    List<String> vlist = optionalList(config, name);

    if (vlist.isEmpty()) {
      throw new IllegalArgumentException("No ldap " + name + " configured");
    }

    return vlist;
  }

  static SearchScope scope(Config c, String setting) {
    return c.getEnum("ldap", null, setting, SearchScope.SUBTREE);
  }

  static String reqdef(Config c, String n, String d) {
    final String v = optdef(c, n, d);
    if (v == null) {
      throw new IllegalArgumentException("No ldap." + n + " configured");
    }
    return v;
  }
}
