load(
    "@com_googlesource_gerrit_bazlets//:gerrit_plugin.bzl",
    "gerrit_plugin",
    "gerrit_plugin_dependency_tests",
    "gerrit_plugin_tests",
)
load("@rules_java//java:defs.bzl", "java_binary")
load("@rules_jvm_external//:defs.bzl", "artifact")

SAML_DEPS = [
    "org.pac4j:pac4j-core",
    "org.pac4j:pac4j-javaee",
    "org.pac4j:pac4j-saml",
    "org.springframework:spring-core",
]

gerrit_plugin(
    srcs = glob(["src/main/java/**/*.java"]),
    ext_deps = SAML_DEPS,
    manifest_entries = [
        "Gerrit-PluginName: saml",
    ],
    plugin = "saml",
)

gerrit_plugin_tests(
    srcs = glob(["src/test/java/**/*.java"]),
    ext_deps = SAML_DEPS,
    plugin = "saml",
    deps = [
        "//javatests/com/google/gerrit/util/http/testutil",
    ],
)

java_binary(
    name = "SamlMetadataCreator",
    main_class = "com.googlesource.gerrit.plugins.saml.pgm.SamlMetadataCreator",
    runtime_deps = [artifact(
        dep,
        repository_name = "saml_plugin_deps",
    ) for dep in SAML_DEPS] + [
        ":saml__plugin",
        "//java/com/google/gerrit/server",
        "//lib:args4j",
        "//lib:jgit",
        "//lib/bouncycastle:bcprov",
        "//lib/dropwizard:dropwizard-core",
    ],
)
