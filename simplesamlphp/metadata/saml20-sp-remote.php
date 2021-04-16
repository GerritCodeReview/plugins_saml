<?php
/**
 * SAML 2.0 remote SP metadata for SimpleSAMLphp.
 *
 * See: https://simplesamlphp.org/docs/stable/simplesamlphp-reference-sp-remote
 */

$metadata['gerritSaml'] = array(
    'entityid' => 'gerritSaml',
    'contacts' => array(
    ),
    'metadata-set' => 'saml20-sp-remote',
    'expire' => 2249280670,
    'AssertionConsumerService' => array(
        0 => array(
            'Binding' => 'urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST',
            'Location' => 'http://localhost:8081/plugins/saml/callback?client_name=SAML2Client',
            'index' => 0,
        ),
    ),
    'SingleLogoutService' => array(
        0 => array(
            'Binding' => 'urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST',
            'Location' => 'http://localhost:8081/plugins/saml/callback?client_name=SAML2Client&logoutendpoint=true',
        ),
        1 => array(
            'Binding' => 'urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST-SimpleSign',
            'Location' => 'http://localhost:8081/plugins/saml/callback?client_name=SAML2Client&logoutendpoint=true',
        ),
        2 => array(
            'Binding' => 'urn:oasis:names:tc:SAML:2.0:bindings:HTTP-Redirect',
            'Location' => 'http://localhost:8081/plugins/saml/callback?client_name=SAML2Client&logoutendpoint=true',
        ),
        3 => array(
            'Binding' => 'urn:oasis:names:tc:SAML:2.0:bindings:SOAP',
            'Location' => 'http://localhost:8081/plugins/saml/callback?client_name=SAML2Client&logoutendpoint=true',
        ),
    ),
    'NameIDFormat' => 'urn:oasis:names:tc:SAML:2.0:nameid-format:persistent',
    'authproc' => array(
        1 => array(
            'class' => 'saml:AttributeNameID',
            'attribute' => 'username',
            'Format' => 'urn:oasis:names:tc:SAML:2.0:nameid-format:persistent',
        ),
    ),
    'keys' => array(
        0 => array(
            'encryption' => false,
            'signing' => true,
            'type' => 'X509Certificate',
            'X509Certificate' => 'MIIDdzCCAl+gAwIBAgIEFJJc0DANBgkqhkiG9w0BAQsFADBsMRAwDgYDVQQGEwdVbmtub3duMRAw
  DgYDVQQIEwdVbmtub3duMRAwDgYDVQQHEwdVbmtub3duMRAwDgYDVQQKEwdVbmtub3duMRAwDgYD
  VQQLEwdVbmtub3duMRAwDgYDVQQDEwdVbmtub3duMB4XDTIxMDQwODA2MDE1MVoXDTIyMDQwODA2
  MDE1MVowbDEQMA4GA1UEBhMHVW5rbm93bjEQMA4GA1UECBMHVW5rbm93bjEQMA4GA1UEBxMHVW5r
  bm93bjEQMA4GA1UEChMHVW5rbm93bjEQMA4GA1UECxMHVW5rbm93bjEQMA4GA1UEAxMHVW5rbm93
  bjCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAKL01jq3y+1g7qN2+ieNQ7VjRJpLNMgy
  yOmdQ28kWbNNemB5mO/LqpU5kCfZJRSvwh4rSoZxOG3FkChOhUyZMr9SBFHNB1HGAE/JSh+1g2eh
  x315LBvKKB5EMfsWB4fi37nEkLmlrV6BLl0TcKCoQTIn4DkHsb5OmUu/tqVE2u2w0G6YxZRu2CmL
  acbaTTS6HAgJQmsZBpVq+NfyqOyabV3aYvpE5oWsVNRRkFrEeNQ6sQGTVUkjuvMTHLgI40DEg1Is
  vPSrT2K5FZ/ImWZwzWWzj3htJJ938KfXygISunuNce7CUSldWnC8oUDbTnb+TpXVFG30xX6P2uG3
  NcULFdcCAwEAAaMhMB8wHQYDVR0OBBYEFHQpiu87m0wXW5uiGQMewPZsamxYMA0GCSqGSIb3DQEB
  CwUAA4IBAQCYdqwk+Iv1/bCZi8+MuuFKparAydfEG+eFyOPUPdP9MhmuK7xgSpgu282rLTeQ+izg
  BgFOMzRS1EAe07y878eovUEGi+YYtFWORp3G/7Pa5ZFVEZ/nS/BDYhYzVF59EnmDd9qf0fFSnv9B
  FZc98Pe1vzc0XbwScOMtpZMkA3jCqtV8jITNvD+79SaFkiDE8m1xp8dBSBxJN9H1CgfOx5cbRbVd
  UFkH8KpwVwzlR8MjnTdcq7JQPX5f78aCJ1Wl3t5yz5Gs9+WLa1tPGn+ucb8qkONeSlCUgQ3x5C+1
  wzhwl/zxYfJfODzCtjH3ObQfkoBDUF2b5OxkldgsOxl3R6he',
        ),
        1 => array(
            'encryption' => true,
            'signing' => false,
            'type' => 'X509Certificate',
            'X509Certificate' => 'MIIDdzCCAl+gAwIBAgIEFJJc0DANBgkqhkiG9w0BAQsFADBsMRAwDgYDVQQGEwdVbmtub3duMRAw
  DgYDVQQIEwdVbmtub3duMRAwDgYDVQQHEwdVbmtub3duMRAwDgYDVQQKEwdVbmtub3duMRAwDgYD
  VQQLEwdVbmtub3duMRAwDgYDVQQDEwdVbmtub3duMB4XDTIxMDQwODA2MDE1MVoXDTIyMDQwODA2
  MDE1MVowbDEQMA4GA1UEBhMHVW5rbm93bjEQMA4GA1UECBMHVW5rbm93bjEQMA4GA1UEBxMHVW5r
  bm93bjEQMA4GA1UEChMHVW5rbm93bjEQMA4GA1UECxMHVW5rbm93bjEQMA4GA1UEAxMHVW5rbm93
  bjCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAKL01jq3y+1g7qN2+ieNQ7VjRJpLNMgy
  yOmdQ28kWbNNemB5mO/LqpU5kCfZJRSvwh4rSoZxOG3FkChOhUyZMr9SBFHNB1HGAE/JSh+1g2eh
  x315LBvKKB5EMfsWB4fi37nEkLmlrV6BLl0TcKCoQTIn4DkHsb5OmUu/tqVE2u2w0G6YxZRu2CmL
  acbaTTS6HAgJQmsZBpVq+NfyqOyabV3aYvpE5oWsVNRRkFrEeNQ6sQGTVUkjuvMTHLgI40DEg1Is
  vPSrT2K5FZ/ImWZwzWWzj3htJJ938KfXygISunuNce7CUSldWnC8oUDbTnb+TpXVFG30xX6P2uG3
  NcULFdcCAwEAAaMhMB8wHQYDVR0OBBYEFHQpiu87m0wXW5uiGQMewPZsamxYMA0GCSqGSIb3DQEB
  CwUAA4IBAQCYdqwk+Iv1/bCZi8+MuuFKparAydfEG+eFyOPUPdP9MhmuK7xgSpgu282rLTeQ+izg
  BgFOMzRS1EAe07y878eovUEGi+YYtFWORp3G/7Pa5ZFVEZ/nS/BDYhYzVF59EnmDd9qf0fFSnv9B
  FZc98Pe1vzc0XbwScOMtpZMkA3jCqtV8jITNvD+79SaFkiDE8m1xp8dBSBxJN9H1CgfOx5cbRbVd
  UFkH8KpwVwzlR8MjnTdcq7JQPX5f78aCJ1Wl3t5yz5Gs9+WLa1tPGn+ucb8qkONeSlCUgQ3x5C+1
  wzhwl/zxYfJfODzCtjH3ObQfkoBDUF2b5OxkldgsOxl3R6he',
        ),
    ),
    'validate.authnrequest' => false,
    'validate.logout' => false,
    'saml20.sign.assertion' => false,
);
