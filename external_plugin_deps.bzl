load("//tools/bzl:maven_jar.bzl", "maven_jar")

SHIBBOLETH = "https://build.shibboleth.net/nexus/content/repositories/releases/"

OPENSAML_VERSION = "3.4.2"

PAC4J_VERSION = "3.6.1"

def external_plugin_deps():
    maven_jar(
        name = "cryptacular",
        artifact = "org.cryptacular:cryptacular:1.2.1",
        sha1 = "c470bac7309ac04b0b9529bd7dcb1e0b75954f11",
    )

    maven_jar(
        name = "opensaml-core",
        artifact = "org.opensaml:opensaml-core:" + OPENSAML_VERSION,
        sha1 = "d7bfd76283b4b12074f9a6b866ce048d793390a6",
        repository = SHIBBOLETH,
    )

    maven_jar(
        name = "opensaml-saml-api",
        artifact = "org.opensaml:opensaml-saml-api:" + OPENSAML_VERSION,
        sha1 = "e8e1034cfdee26267d4e5aacc1ab7682cb03ddb8",
        repository = SHIBBOLETH,
    )

    maven_jar(
        name = "opensaml-storage-api",
        artifact = "org.opensaml:opensaml-storage-api:" + OPENSAML_VERSION,
        sha1 = "f562d624a35e5aeb2ba21222f54a5bbcc02a74ce",
        repository = SHIBBOLETH,
    )

    maven_jar(
        name = "opensaml-saml-impl",
        artifact = "org.opensaml:opensaml-saml-impl:" + OPENSAML_VERSION,
        sha1 = "76f1e54ded6ba903a30af54f37494f249cf38f17",
        repository = SHIBBOLETH,
    )

    maven_jar(
        name = "opensaml-soap-impl",
        artifact = "org.opensaml:opensaml-soap-impl:" + OPENSAML_VERSION,
        sha1 = "ecfcf4431dcff6dc57744e0bfca151efc87b96fc",
        repository = SHIBBOLETH,
    )

    maven_jar(
        name = "opensaml-soap-api",
        artifact = "org.opensaml:opensaml-soap-api:" + OPENSAML_VERSION,
        sha1 = "22566f94af9687b5935cce64dbec1ccc8c80eb6f",
        repository = SHIBBOLETH,
    )

    maven_jar(
        name = "opensaml-xmlsec-api",
        artifact = "org.opensaml:opensaml-xmlsec-api:" + OPENSAML_VERSION,
        sha1 = "4f96eebc87f81341ea121afaceae83a5e3f13dd4",
        repository = SHIBBOLETH,
    )

    maven_jar(
        name = "opensaml-xmlsec-impl",
        artifact = "org.opensaml:opensaml-xmlsec-impl:" + OPENSAML_VERSION,
        sha1 = "0e4d4df9809423b2ec386411480afa7c48320919",
        repository = SHIBBOLETH,
    )

    maven_jar(
        name = "opensaml-security-api",
        artifact = "org.opensaml:opensaml-security-api:" + OPENSAML_VERSION,
        sha1 = "bd4502769741b7d2c513f0051bb46da8d4e6f5c0",
        repository = SHIBBOLETH,
    )

    maven_jar(
        name = "opensaml-security-impl",
        artifact = "org.opensaml:opensaml-security-impl:" + OPENSAML_VERSION,
        sha1 = "f4e6bf165291f46c5f7ba267b0c4854bbe058e41",
        repository = SHIBBOLETH,
    )

    maven_jar(
        name = "opensaml-profile-api",
        artifact = "org.opensaml:opensaml-profile-api:" + OPENSAML_VERSION,
        sha1 = "b6a56330992f7ec400411f2efa19fd5115675b07",
        repository = SHIBBOLETH,
    )

    maven_jar(
        name = "opensaml-profile-impl",
        artifact = "org.opensaml:opensaml-profile-impl:" + OPENSAML_VERSION,
        sha1 = "61b086e39649664cdd93b5863cafb0e1042a6bea",
        repository = SHIBBOLETH,
    )

    maven_jar(
        name = "opensaml-messaging-api",
        artifact = "org.opensaml:opensaml-messaging-api:" + OPENSAML_VERSION,
        sha1 = "a2b4f5e7862979d1e769ba262ded93b6d87cc029",
        repository = SHIBBOLETH,
    )

    maven_jar(
        name = "opensaml-messaging-impl",
        artifact = "org.opensaml:opensaml-messaging-impl:" + OPENSAML_VERSION,
        sha1 = "cd622191c79020c89dd51066790facae8a57f26d",
        repository = SHIBBOLETH,
    )

    maven_jar(
        name = "pac4j-saml",
        artifact = "org.pac4j:pac4j-saml:" + PAC4J_VERSION,
        sha1 = "7e62141587aea14c2e1dbcbf5558319cc3896fc0",
    )

    maven_jar(
        name = "pac4j-core",
        artifact = "org.pac4j:pac4j-core:" + PAC4J_VERSION,
        sha1 = "4d60b8c1f9138ec673c1595b82016408b1f41ec3",
    )

    maven_jar(
        name = "shibboleth-utilities",
        artifact = "net.shibboleth.utilities:java-support:7.4.0",
        sha1 = "e10c137cdb5045eea2c0ccf8ac5094052eaee36b",
        repository = SHIBBOLETH,
    )

    maven_jar(
        name = "shibboleth-xmlsectool",
        artifact = "net.shibboleth.tool:xmlsectool:2.0.0",
        sha1 = "c57f887f522c0e930341c7d86eff4d8ec9b797a1",
        repository = SHIBBOLETH,
    )

    maven_jar(
        name = "santuario-xmlsec",
        artifact = "org.apache.santuario:xmlsec:2.0.10",
        sha1 = "57865d2fbaf65f27c6cb8e909e37842e5cb87960",
    )

    maven_jar(
        name = "spring-core",
        artifact = "org.springframework:spring-core:5.1.5.RELEASE",
        sha1 = "aacc4555108f3da913a58114b2aebc819f58cce4",
    )

    maven_jar(
        name = "stax2-api",
        artifact = "org.codehaus.woodstox:stax2-api:3.1.4",
        sha1 = "ac19014b1e6a7c08aad07fe114af792676b685b7",
    )

    maven_jar(
        name = "woodstox-core",
        artifact = "com.fasterxml.woodstox:woodstox-core:5.0.3",
        sha1 = "10aa199207fda142eff01cd61c69244877d71770",
    )
