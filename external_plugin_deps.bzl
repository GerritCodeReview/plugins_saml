load("//tools/bzl:maven_jar.bzl", "maven_jar")

SHIBBOLETH = "https://build.shibboleth.net/nexus/content/repositories/releases/"

OPENSAML_VERSION = "3.4.3"

PAC4J_VERSION = "3.8.3"

def external_plugin_deps():
    # Transitive dependency of velocity
    maven_jar(
        name = "commons-collections",
        artifact = "commons-collections:commons-collections:3.2.2",
        sha1 = "8ad72fe39fa8c91eaaf12aadb21e0c3661fe26d5",
    )

    maven_jar(
        name = "commons-io",
        artifact = "commons-io:commons-io:2.4",
        sha1 = "b1b6ea3b7e4aa4f492509a4952029cd8e48019ad",
    )

    maven_jar(
        name = "commons-lang",
        artifact = "commons-lang:commons-lang:2.6",
        sha1 = "0ce1edb914c94ebc388f086c6827e8bdeec71ac2",
    )

    maven_jar(
        name = "cryptacular",
        artifact = "org.cryptacular:cryptacular:1.2.1",
        sha1 = "c470bac7309ac04b0b9529bd7dcb1e0b75954f11",
    )

    maven_jar(
        name = "joda-time",
        artifact = "joda-time:joda-time:2.9.9",
        sha1 = "f7b520c458572890807d143670c9b24f4de90897",
    )

    maven_jar(
        name = "opensaml-core",
        artifact = "org.opensaml:opensaml-core:" + OPENSAML_VERSION,
        sha1 = "406eedd86ea88c1442a6b1c7625a45cf696b9f55",
        repository = SHIBBOLETH,
    )

    maven_jar(
        name = "opensaml-saml-api",
        artifact = "org.opensaml:opensaml-saml-api:" + OPENSAML_VERSION,
        sha1 = "b2c68a7265e8b059ecbfff0ac6525720cd3e1a86",
        repository = SHIBBOLETH,
    )

    maven_jar(
        name = "opensaml-storage-api",
        artifact = "org.opensaml:opensaml-storage-api:" + OPENSAML_VERSION,
        sha1 = "80ff32a3df660fe71527f293a317813c51375dcc",
        repository = SHIBBOLETH,
    )

    maven_jar(
        name = "opensaml-storage-impl",
        artifact = "org.opensaml:opensaml-storage-impl:" + OPENSAML_VERSION,
        sha1 = "da2116186a10ba5e1adecf2445184bf1feb1aa1c",
        repository = SHIBBOLETH,
    )

    maven_jar(
        name = "opensaml-saml-impl",
        artifact = "org.opensaml:opensaml-saml-impl:" + OPENSAML_VERSION,
        sha1 = "c4bce04bec8fd065bbc014a2c4003172ec612ba6",
        repository = SHIBBOLETH,
    )

    maven_jar(
        name = "opensaml-soap-impl",
        artifact = "org.opensaml:opensaml-soap-impl:" + OPENSAML_VERSION,
        sha1 = "9a1b9bc0ed6a0c62f3f607cc2c1164c76a57303e",
        repository = SHIBBOLETH,
    )

    maven_jar(
        name = "opensaml-soap-api",
        artifact = "org.opensaml:opensaml-soap-api:" + OPENSAML_VERSION,
        sha1 = "4fe18269fff79f7172d9dbe0d421886282baa434",
        repository = SHIBBOLETH,
    )

    maven_jar(
        name = "opensaml-xmlsec-api",
        artifact = "org.opensaml:opensaml-xmlsec-api:" + OPENSAML_VERSION,
        sha1 = "b7f0f8a9c17997008bcef75a8886faeb5e9d9ea9",
        repository = SHIBBOLETH,
    )

    maven_jar(
        name = "opensaml-xmlsec-impl",
        artifact = "org.opensaml:opensaml-xmlsec-impl:" + OPENSAML_VERSION,
        sha1 = "3dbdf38773a07d37f013dc9a2ecc4d0295a724de",
        repository = SHIBBOLETH,
    )

    maven_jar(
        name = "opensaml-security-api",
        artifact = "org.opensaml:opensaml-security-api:" + OPENSAML_VERSION,
        sha1 = "b6878bd144c15612ab899643e561e52f04d332c1",
        repository = SHIBBOLETH,
    )

    maven_jar(
        name = "opensaml-security-impl",
        artifact = "org.opensaml:opensaml-security-impl:" + OPENSAML_VERSION,
        sha1 = "72edf27dbce57ed29aebab8563a41942f7f15527",
        repository = SHIBBOLETH,
    )

    maven_jar(
        name = "opensaml-profile-api",
        artifact = "org.opensaml:opensaml-profile-api:" + OPENSAML_VERSION,
        sha1 = "8daff1c6b7ff47178054e17e78b0d4b19b622434",
        repository = SHIBBOLETH,
    )

    maven_jar(
        name = "opensaml-profile-impl",
        artifact = "org.opensaml:opensaml-profile-impl:" + OPENSAML_VERSION,
        sha1 = "175bd3d0ba07a17f0222ea799c3971119c9b32b3",
        repository = SHIBBOLETH,
    )

    maven_jar(
        name = "opensaml-messaging-api",
        artifact = "org.opensaml:opensaml-messaging-api:" + OPENSAML_VERSION,
        sha1 = "18f68283a3729e4355a29936861f6472ab20b2be",
        repository = SHIBBOLETH,
    )

    maven_jar(
        name = "opensaml-messaging-impl",
        artifact = "org.opensaml:opensaml-messaging-impl:" + OPENSAML_VERSION,
        sha1 = "d0cd65f2b0a167dc25477245adf5417a8735e132",
        repository = SHIBBOLETH,
    )

    maven_jar(
        name = "pac4j-saml",
        artifact = "org.pac4j:pac4j-saml:" + PAC4J_VERSION,
        sha1 = "c112c180703e600815c949bdd4c9646b6d70238e",
    )

    maven_jar(
        name = "pac4j-core",
        artifact = "org.pac4j:pac4j-core:" + PAC4J_VERSION,
        sha1 = "ce2d5c63d9f034f5631d3b3ebec46f916b7064f2",
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
        artifact = "org.apache.santuario:xmlsec:2.1.4",
        sha1 = "cb43326f02e3e77526c24269c8b5d3cc3f7f6653",
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
        name = "velocity",
        artifact = "org.apache.velocity:velocity:1.7",
        sha1 = "2ceb567b8f3f21118ecdec129fe1271dbc09aa7a",
    )

    maven_jar(
        name = "woodstox-core",
        artifact = "com.fasterxml.woodstox:woodstox-core:5.0.3",
        sha1 = "10aa199207fda142eff01cd61c69244877d71770",
    )
