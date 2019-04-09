# Gerrit SAML Authentication Filter

This filter allows you to authenticate to Gerrit using a SAML identity
provider.

## Installation

Gerrit looks for 3 attributes (which are configurable) in the AttributeStatement:

- **DisplayName:** the full name of the user.
- **EmailAddress:** email address of the user.
- **UserName:** username (used for ssh).

If any of these attributes is not found in the assertion, their value is
taken from the NameId field of the SAML assertion.

### Setting Gerrit in your IdP

- [Okta](okta/README.md)
- [Keycloak](keycloak/README.md)
- [ADFS](adfs/README.md)

### Download the plugin

Download Gerrit SAML plugin for the appropriate version of gerrit from the [Gerrit-CI](https://gerrit-ci.gerritforge.com/search/?q=saml)
into $gerrit_site/lib/.

### Building the SAML filter

This authentication filter is built with Bazel.

## Build in Gerrit tree

Clone or link this filter to the plugins directory of Gerrit's
source tree. Put the external dependency Bazel build file into
the Gerrit /plugins directory, replacing the existing empty one.

```
  cd gerrit/plugins
  rm external_plugin_deps.bzl
  ln -s @PLUGIN@/external_plugin_deps.bzl .
```

Then issue

```
  bazel build plugins/@PLUGIN@
```

The output is created in

```
  bazel-genfiles/plugins/@PLUGIN@/@PLUGIN@.jar
```

The @PLUGIN@.jar should be deployed to `gerrit_site/lib` directory:

```
 cp bazel-genfiles/plugins/@PLUGIN@/@PLUGIN@.jar `$gerrit_site/lib`
```

__NOTE__: Even though the project is build as a Gerrit plugin, it must be loaded
as a Servlet filter by Gerrit and thus needs to be located with the libraries and
cannot be dynamically loaded like other plugins.

This project can be imported into the Eclipse IDE.
Add the plugin name to the `CUSTOM_PLUGINS` set in
Gerrit core in `tools/bzl/plugins.bzl`, and execute:

```
  ./tools/eclipse/project.py
```

How to build the Gerrit Plugin API is described in the [Gerrit documentation](../../../Documentation/dev-bazel.html#_extension_and_plugin_api_jar_files).

### Configure Gerrit to use the SAML filter:
In `$site_path/etc/gerrit.config` file, the `[httpd]` section should contain

```
[httpd]
    filterClass = com.googlesource.gerrit.plugins.saml.SamlWebFilter
```

### Configure HTTP authentication for Gerrit:

Please make sure you are using Gerrit 2.11.5 or later.

In `$site_path/etc/gerrit.config` file, the `[auth]` section should include
the following lines:

```
[auth]
	type = HTTP
    logoutUrl = https://mysso.example.com/logout
    httpHeader = X-SAML-UserName
    httpDisplaynameHeader = X-SAML-DisplayName
    httpEmailHeader = X-SAML-EmailHeader
    httpExternalIdHeader = X-SAML-ExternalId
```

The header names are used internally between the SAML plugin and Gerrit to
communicate the user's identity.  You can use other names (as long as it will
not conflict with any other HTTP header Gerrit might expect).

### Create a local keystore

In `$gerrit_site/etc` create a local keystore:

```
keytool -genkeypair -alias pac4j -keypass pac4j-demo-password \
  -keystore samlKeystore.jks \
  -storepass pac4j-demo-password -keyalg RSA -keysize 2048 -validity 3650
```

### Configure SAML

Add a new `[saml]` section to `$site_path/etc/gerrit.config`:

```
[saml]
    keystorePath = /path/to/samlKeystore.jks
    keystorePassword = pac4j-demo-password
    privateKeyPassword = pac4j-demo-password
    metadataPath = https://mycompany.okta.com/app/hashash/sso/saml/metadata
```

**saml.metadataPath**: Location of IdP Metadata from your SAML identity provider.
The value can be a URL, or a local file (prefix with `file://`)

**saml.keystorePath**: Path to the keystore created above. If not absolute,
the path is resolved relative to `$site_path`.

**saml.privateKeyPassword**: Password protecting the private key of the generated
key pair (needs to be the same as the password provided throguh the `keypass`
flag above.)

**saml.keystorePassword**: Password that is used to protect the integrity of the
keystore (needs to be the same as the password provided throguh the `keystore`
flag above.)

**saml.maxAuthLifetime**: (Optional) Max Authentication Lifetime (secs) configuration.

Default is `86400`

**saml.displayNameAttr**: Gerrit will look for an attribute with this name in
the assertion to find a display name for the user. If the attribute is not
found, the NameId from the SAML assertion is used instead.

Default is `DisplayName`

**saml.computedDisplayName**: Set to compute display name attribute from first
and last names.

Default is false.

**saml.firstNameAttr**: Gerrit will look for an attribute with this name in
the assertion to find the first name of the user. Only used, when `computedDisplayName`
is set to true. If the attribute is not found, the NameId from the SAML assertion
is used instead.

Default is `FirstName`

**saml.lastNameAttr**: Gerrit will look for an attribute with this name in
the assertion to find the last name of the user. Only used, when `computedDisplayName`
is set to true. If the attribute is not found, the NameId from the SAML assertion
is used instead.

Default is `LastName`

**saml.emailAddressAttr**: Gerrit will look for an attribute with this name in
the assertion to find a the email address of the user. If the attribute is not
found, the NameId from the SAML assertion is used instead.

Default is `EmailAddress`

**saml.userNameAttr**: Gerrit will look for an attribute with this name in the
assertion to find a the email address of the user. If the attribute is not
found, the NameId from the SAML assertion is used instead.

Default is `UserName`

**saml.serviceProviderEntityId**: Saml service provider entity id

Default is not set.
