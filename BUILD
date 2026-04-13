load(
    "@com_googlesource_gerrit_bazlets//:gerrit_plugin.bzl",
    "gerrit_plugin",
    "gerrit_plugin_tests",
)
load(
    "@com_googlesource_gerrit_bazlets//tools:in_gerrit_tree.bzl",
    "in_gerrit_tree_enabled",
)
load(
    "@com_googlesource_gerrit_bazlets//tools:runtime_jars_allowlist.bzl",
    "runtime_jars_allowlist_test",
)
load(
    "@com_googlesource_gerrit_bazlets//tools:runtime_jars_overlap.bzl",
    "runtime_jars_overlap_test",
)

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
    srcs = glob([
        "src/main/java/com/googlesource/gerrit/plugins/saml/**/*.java",
    ]),
    main_class = "com.googlesource.gerrit.plugins.saml.pgm.SamlMetadataCreator",
    deps = SAML_DEPS + [
        "//plugins:plugin-lib-neverlink",
    ],
)

runtime_jars_allowlist_test(
    name = "check_saml_third_party_runtime_jars",
    allowlist = ":saml_third_party_runtime_jars.allowlist.txt",
    hint = "plugins/saml:check_saml_third_party_runtime_jars_manifest",
    target = ":saml__plugin",
)

runtime_jars_overlap_test(
    name = "saml_no_overlap_with_gerrit",
    against = "//:headless.war.jars.txt",
    hint = "Exclude overlaps via maven.install(excluded_artifacts=[...]) and re-run this test.",
    target = ":saml__plugin",
    target_compatible_with = in_gerrit_tree_enabled(),
)
