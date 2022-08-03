# Okta as Gerrit SAML authentication provider

- Create a new SAML 2.0 application.
- Set the following parameters:
  - Single sign on URL: http://gerrit.example.com/plugins/saml/callback?client_name=SAML2Client
  - Check "Use this for Recipient URL and Destination URL".
  - Audience URI (SP Entity Id): http://gerrit.example.com/plugins/saml/callback
  - We need to set up the attributes in the assertion to send the right
    information. Here is how to do it with Okta:
    - Application username: "Okta username prefix"
    - Add attribute statement: Name: "DisplayName" with Value
      "user.displayName"
    - Add attribute statement: Name: "EmailAddress" with Value
      "user.email"
    - **IMPORTANT**: If you are not using Okta, you need to set up an attribute
      "UserName" with the value of the username (not email, without @). If you
      do not do so, the name will be taken from the NameId provided by
      the assertion.  This is why in Okta we set the application username to
      "Okta username prefix".
  - If using Single Logout - In the "SAML Settings" section click the "Show Advanced Features"
    - Enable Single Logout - check "Allow application to initiate Single Logout"
    - Single Logout URL: http://gerrit.example.com/plugins/saml/callback?client_name=SAML2Client&logoutendpoint=true
    - SP Issuer: http://gerrit.example.com/plugins/saml/callback
    - Signature Certificate: Use the public key from the keystore defined at saml.keystorePath
- Obtain your IdP metadata (either URL or a local XML file)
