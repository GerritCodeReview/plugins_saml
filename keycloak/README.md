# Keycloak as Gerrit SAML provider

[Keycloak](https://www.keycloak.org/) is open source Identity and Access
Management tool and supports the SAML authentication protocol.

## Objective

This document provides a step-by-step tutorial how to set-up Keycloak as
SAML provider for Gerrit Code Review for development and guidance only.
For production HTTPS protocol and other more secure credentials and keys
would need to be put in place.

## Prerequisites

- [Docker](https://www.docker.com/get-started)
- [Docker-compose](https://docs.docker.com/compose/)
- [Gerrit Code Review v2.15 or later](https://www.gerritcodereview.com)

## Steps

1. Install Keycloak official Docker image from this repository and start it:

```bash
  $ git clone https://github.com/jboss-dockerfiles/keycloak
  $ cd keycloak/docker-compose-examples
  $ docker-compose -f keycloak-postgres.yml up
```

2. Open your Keycloak Admin Console and sign in with the administrator
credentials configured for that installation. Select the `master` realm
and import the Gerrit client [keycloak JSON file](keycloak-gerrit-client-export.json).

3. Create test user (e.g., fullname="John Doe", username "jdoe", email: "john@doe.org", password "secret", Temporary=OFF)

4. Add the following configuration settings to $GERRIT_SITE/etc/gerrit.config:

```
[auth]
    type = HTTP
    httpHeader = X-SAML-UserName
    httpEmailHeader = X-SAML-EmailHeader
    httpExternalIdHeader = X-SAML-ExternalId

[httpd]
    listenUrl = http://*:8081/
    filterClass = com.googlesource.gerrit.plugins.saml.SamlWebFilter

[saml]
    serviceProviderEntityId = SAML2Client
    keystorePath = etc/samlKeystore.jks
    keystorePassword = pac4j-demo-password
    privateKeyPassword = pac4j-demo-password
    metadataPath = http://localhost:8080/auth/realms/master/protocol/saml/descriptor
    userNameAttr = UserName
    displayNameAttr = DisplayName
    emailAddressAttr = EmailAddress
    computedDisplayName = true
    firstNameAttr = firstName
    lastNameAttr = lastName
```

5. Generate keystore in `$GERRIT_SITE/etc` local keystore:

```
keytool -genkeypair -alias pac4j -keypass pac4j-demo-password \
  -keystore samlKeystore.jks \
  -storepass pac4j-demo-password -keyalg RSA -keysize 2048 -validity 365
```

6. Install the saml.jar filter into the `$GERRIT_SITE/lib` directory

7. Start gerrit using: `$GERRIT_SITE/bin/gerrit.sh start`

8. Enter gerrit URL in browser: http://localhost:8081 and hit "Sign In" button

9. Keycloak Login Dialog should appear

10. Enter user: "jdoe" and password: "secret"

11. You are redirected to gerrit and the first user/admin John Doe is created
in gerrit with the right user name and email address.

12. Congrats, you have Gerrit / Keycloak SAML integration up and running.

## Single Logout

To end both the Gerrit login and Keycloak SSO session, add this to
`$GERRIT_SITE/etc/gerrit.config` and restart Gerrit:

```ini
[saml]
    logoutType = slo-post
```

In the Keycloak Admin Console, select the realm used by `saml.metadataPath`
and open **Clients → SAML2Client** (or the configured SP entity ID):

1. Under **Settings → Logout settings**, enable **Front channel logout**.
2. Under **Advanced → Fine Grain SAML Endpoint Configuration**, set
   **Logout Service POST Binding URL** and **Logout Service Redirect Binding
   URL** to the corresponding `SingleLogoutService` locations in
   `$GERRIT_SITE/data/saml/sp-metadata.xml`, including any query parameters.
3. Under **Settings → SAML capabilities**, set **Force POST binding** according
   to the selected logout type, then save:

   | Gerrit `saml.logoutType` | Keycloak **Force POST binding** |
   |---|---|
   | `slo-post` | On |
   | `slo-redirect` | Off |

Logout requests are signed by default. If **Client Signature Required** is
on, Keycloak's client certificate must match Gerrit's SAML keystore.
See [Enable Single Logout](../README.md#enable-single-logout) for other options.
