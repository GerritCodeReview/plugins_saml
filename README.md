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
- [SimpleSamlPHP](simplesamlphp/README.md)

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
  bazel-bin/plugins/@PLUGIN@/@PLUGIN@.jar
```

The @PLUGIN@.jar should be deployed to `gerrit_site/lib` directory:

```
 cp bazel-bin/plugins/@PLUGIN@/@PLUGIN@.jar `$gerrit_site/lib`
```

__NOTE__: Even though the project is built as a Gerrit plugin, it must be loaded
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
In `$site_path/etc/gerrit.config` file, the `[httpd]` and `[gerrit]` sections should
contain:

```
[gerrit]
    installModule = com.googlesource.gerrit.plugins.saml.Module
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

**saml.forceAuth**: (Optional) Whether to force authentication with the IdP, when
the session in Gerrit expires.

Default is `false`

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

**saml.serviceProviderEntityId**: SAML service provider entity id.

Default is not set.

**saml.identityProviderEntityId**: SAML identity provider entity id.  When present
a `IDPSSODescriptor` is expected in the SAML metadata document.  When absent a
saml service provider with its `SPSSODescriptor` is assumed.
This value takes precedence over the value in **saml.serviceProviderEntityId**.

Default is not set.

**saml.memberOfAttr**: Gerrit will look for an attribute with this name in the
assertion to find the groups the user is member of.

The user will receive these groups prefixed with `saml/` in gerrit.  When the
groups do not exist, they will be created.  When a user its membership is removed
this group will also be removed from this user on his next login.

As group membership is only updated when a user logs in on the UI, so when a
user loses membership to a group in SAML, he will still be able to execute his
rights as if he is part of that group as long as he does not log in to the UI.
So enabling this feature can be seen as a security risk in certain environments.

When this attribute is not set or empty, SAML membership synchronization is disabled.

Default is not set.

**saml.useNameQualifier**: By SAML specification, the authentication request must not contain a NameQualifier, if the SP entity is in the format nameid-format:entity. However, some IdP require that information to be present. You can force a NameQualifier in the request with the useNameQualifier parameter. For ADFS 3.0 support, set this to `false`.

Default is true.

### Create SAML metadata offline

The SAML metadata file (`$SITE/data/saml/sp-metadata.xml`) will be created on the
first login attempt, when the plugin has been installed. However, at that point
authentication would fail until the identity provider was configured using the
metadata file of Gerrit.

To avoid this period in which authentication is not possible, the metadata can
be created offline. To do so, a separate java binary has to be built:

```sh
bazelisk build //plugins/saml:SamlMetadataCreator_deploy.jar
```

The resulting jar-file can then be used to create the metadata file based on the
existing gerrit.config:

```sh
bazel-bin/plugins/saml/SamlMetaDataCreator \
  -d $SITE \    # Path to the Gerrit site
  --overwrite   # Whether to overwrite any existing metadata file
```

The resulting metadata will be printed to standard out and stored at
`$SITE/data/saml/sp-metadata.xml`.
