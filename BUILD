load("//tools/bzl:plugin.bzl", "PLUGIN_DEPS", "PLUGIN_TEST_DEPS", "gerrit_plugin")
load("//tools/bzl:junit.bzl", "junit_tests")

SAML_DEPS = [
    "@commons-collections//jar",
    "@commons-lang//jar",
    "@cryptacular//jar",
    "@joda-time//jar",
    "@opensaml-core//jar",
    "@opensaml-messaging-api//jar",
    "@opensaml-messaging-impl//jar",
    "@opensaml-profile-api//jar",
    "@opensaml-profile-impl//jar",
    "@opensaml-saml-api//jar",
    "@opensaml-saml-impl//jar",
    "@opensaml-security-api//jar",
    "@opensaml-security-impl//jar",
    "@opensaml-soap-api//jar",
    "@opensaml-soap-impl//jar",
    "@opensaml-storage-api//jar",
    "@opensaml-storage-impl//jar",
    "@opensaml-xmlsec-api//jar",
    "@opensaml-xmlsec-impl//jar",
    "@pac4j-core//jar",
    "@pac4j-saml//jar",
    "@santuario-xmlsec//jar",
    "@shibboleth-utilities//jar",
    "@shibboleth-xmlsectool//jar",
    "@spring-core//jar",
    "@stax2-api//jar",
    "@velocity//jar",
    "@woodstox-core//jar",
]

gerrit_plugin(
    name = "saml",
    srcs = glob(["src/main/java/**/*.java"]),
    manifest_entries = [
        "Gerrit-PluginName: saml",
    ],
    resources = glob(["src/main/resources/**"]),
    deps = SAML_DEPS,
)

junit_tests(
    name = "saml_tests",
    srcs = glob(["src/test/java/**/*.java"]),
    tags = ["saml"],
    deps = PLUGIN_TEST_DEPS + [
        ":saml__plugin",
        "//javatests/com/google/gerrit/util/http/testutil",
    ],
)

java_binary(
    name = "SamlMetadataCreator",
    srcs = glob([
        "src/main/java/com/googlesource/gerrit/plugins/saml/**/*.java",
    ]),
    main_class = "com.googlesource.gerrit.plugins.saml.pgm.Main",
    deps = PLUGIN_DEPS + SAML_DEPS + [
        "@commons-io//jar",
    ],
)
