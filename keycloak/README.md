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

2. Login to Keycloak using user=admin and password=Pa55w0rd credentials and import
the Gerrit client [keycloak json file](keycloak-gerrit-client-export.json).

3. Create test user (e.g., fullname="John Doe", username "jdoe", email: "john@doe.org", password "secret", Temporary=OFF)

4. Add the following configuration settings to $GERRIT_SITE/etc/gerrit.config:

```
[auth]
    type = HTTP
    logoutUrl = http://localhost:8080/auth/realms/master/protocol/openid-connect/logout
    httpHeader = X-SAML-UserName
    httpDisplaynameHeader = X-SAML-DisplayName
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

