load(
    "@com_googlesource_gerrit_bazlets//:gerrit_plugin.bzl",
    "gerrit_plugin",
    "gerrit_plugin_dependency_tests",
    "gerrit_plugin_tests",
)
load("@rules_java//java:defs.bzl", "java_binary")

SAML_DEPS = [
    "@saml_plugin_deps//:org_pac4j_pac4j_core",
    "@saml_plugin_deps//:org_pac4j_pac4j_saml",
]

gerrit_plugin(
    name = "saml",
    srcs = glob(["src/main/java/**/*.java"]),
    manifest_entries = [
        "Gerrit-PluginName: saml",
    ],
    deps = SAML_DEPS,
)

gerrit_plugin_tests(
    name = "saml_tests",
    srcs = glob(["src/test/java/**/*.java"]),
    tags = ["saml"],
    deps = [
        ":saml__plugin",
        "//javatests/com/google/gerrit/util/http/testutil",
    ],
)

java_binary(
    name = "SamlMetadataCreator",
    main_class = "com.googlesource.gerrit.plugins.saml.pgm.SamlMetadataCreator",
    runtime_deps = SAML_DEPS + [
        ":saml__plugin",
        "//plugins:plugin-lib",
    ],
)

gerrit_plugin_dependency_tests(plugin = "saml")
