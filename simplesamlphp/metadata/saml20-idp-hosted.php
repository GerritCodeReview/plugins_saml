<?php

$metadata['http://localhost:8080/simplesaml/saml2/idp/metadata.php'] = array(
    'host' => 'localhost',
    'auth' => 'example-userpass',
    'entityid' => 'http://localhost:8080/simplesaml/saml2/idp/metadata.php',
    'contacts' => array(
    ),
    'metadata-set' => 'saml20-idp-hosted',
    'SingleSignOnServiceBinding' => array(
        0 => 'urn:oasis:names:tc:SAML:2.0:bindings:HTTP-Redirect',
        1 => 'urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST',
    ),
    'SingleLogoutServiceBinding' => array(
        0 => 'urn:oasis:names:tc:SAML:2.0:bindings:HTTP-Redirect',
        1 => 'urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST',
    ),
    'ArtifactResolutionService' => array(
    ),
    'NameIDFormats' => array(
        0 => 'urn:oasis:names:tc:SAML:2.0:nameid-format:transient',
    ),
    'privatekey' => 'server.pem',
    'certificate' => 'server.crt',
);
