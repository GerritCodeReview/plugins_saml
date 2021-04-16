# SimpleSamlPHP as Gerrit SAML provider

[SimpleSamlPHP](https://simplesamlphp.org/) is open source Identity and Access
Management tool and supports the SAML authentication protocol.

## Objective

This document provides a step-by-step tutorial how to set-up SimpleSamlPHP as
SAML provider for Gerrit Code Review for development and guidance only.
For production HTTPS protocol and other more secure credentials and keys
would need to be put in place.

## Prerequisites

- [Docker](https://www.docker.com/get-started)
- [Gerrit Code Review v2.15 or later](https://www.gerritcodereview.com)

## Steps

1. Install the `jamedjo/test-saml-idp` docker image:

```bash
docker run -it --rm --name=testsamlidp_idp \
	-p 8080:8080 \
	-p 8443:8443 \
	-v $(realpath simplesamlphp/config/authsources.php):/var/www/simplesamlphp/config/authsources.php \
	-v $(realpath simplesamlphp/config/config.php):/var/www/simplesamlphp/config/config.php \
	-v $(realpath simplesamlphp/metadata/saml20-sp-remote.php):/var/www/simplesamlphp/metadata/saml20-sp-remote.php \
	-v $(realpath simplesamlphp/metadata/saml20-idp-hosted.php):/var/www/simplesamlphp/metadata/saml20-idp-hosted.php \
	-e SIMPLESAMLPHP_SP_ENTITY_ID=gerritSaml \
	-e SIMPLESAMLPHP_SP_ASSERTION_CONSUMER_SERVICE=http://localhost/simplesaml/module.php/saml/sp/saml2-acs.php/test-sp \
	-e SIMPLESAMLPHP_SP_SINGLE_LOGOUT_SERVICE=http://localhost/simplesaml/module.php/saml/sp/saml2-logout.php/test-sp \
	-d jamedjo/test-saml-idp
```

2. Add the following configuration settings to $GERRIT_SITE/etc/gerrit.config:

```
[gerrit]
	basePath = git
	canonicalWebUrl = http://localhost:8081/
[auth]
	type = HTTP
	logoutUrl = http://localhost:8080/simplesaml/saml2/idp/SingleLogoutService.php?ReturnTo=http://localhost:8081
	httpHeader = X-SAML-UserName
	httpDisplaynameHeader = X-SAML-DisplayName
	httpEmailHeader = X-SAML-EmailHeader
	httpExternalIdHeader = X-SAML-ExternalId
	autoUpdateAccountActiveStatus = true
[saml]
	serviceProviderEntityId = gerritSaml
	keystorePath = /Users/d073103/sites/serviceuserMaster/etc/keystore
	keystorePassword = pac4j-demo-password
	privateKeyPassword = pac4j-demo-password
	metadataPath = http://localhost:8080/simplesaml/saml2/idp/metadata.php
	userNameAttr = username
	emailAddressAttr = email
	computedDisplayName = true
	firstNameAttr = first_name
	lastNameAttr = last_name
[httpd]
	listenUrl = http://*:8081/
	filterClass = com.googlesource.gerrit.plugins.saml.SamlWebFilter
```

3. Generate keystore in `$GERRIT_SITE/etc` local keystore:

```
keytool -genkeypair -alias pac4j -keypass pac4j-demo-password \
  -keystore samlKeystore.jks \
  -storepass pac4j-demo-password -keyalg RSA -keysize 2048 -validity 365
```

4. Install the saml.jar filter into the `$GERRIT_SITE/lib` directory

5. Start gerrit using: `$GERRIT_SITE/bin/gerrit.sh start`

6. Enter gerrit URL in browser: http://localhost:8081 and hit "Sign In" button

7. SimpleSamlPHP Login Dialog should appear

8. Enter user: "user1" and password: "user1pass" (Note that additional users can
be added in `config/authsources.php`.)

9. You are redirected to gerrit and the first user/admin User One is created
in gerrit with the right user name and email address.

12. Congrats, you have Gerrit / SimpleSamlPHP SAML integration up and running.
