package com.aiplacementos.app.platform

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 32: the invariants that make release signing safe, pinned so they stay true.
 *
 * Release signing cannot be regression-tested by building, because the dangerous outcomes all
 * *succeed*. A build that signs production with the debug key succeeds. A build whose password
 * was pasted into build.gradle.kts succeeds. A checkout that commits the keystore succeeds, and
 * the damage is done in git history rather than in any artifact. So the failures these guards
 * exist for are failures no green build reports -- which is the same argument
 * [Android16MigrationGuardTest] makes about absences, applied to credentials.
 *
 * Two things these are NOT:
 *
 *  - They are not a signature check. Whether a given artifact is actually signed, and by which
 *    certificate, is apksigner's job against the built file, not a unit test's.
 *  - They are not a claim that production is signed. At the time of writing it is not: there is
 *    no keystore.properties in this checkout, so `hasReleaseSigningConfig` is false and the
 *    release build is unsigned by design. The last test below pins the one thing that must hold
 *    in *both* states -- that the absence of credentials never silently becomes debug signing.
 */
class ReleaseSigningGuardTest {

    private fun moduleRoot(): File {
        val candidates = listOf(File("."), File("app"), File("../app"))
        val root = candidates.firstOrNull { File(it, "build.gradle.kts").isFile }
        requireNotNull(root) {
            "Could not locate the app module from ${File(".").absolutePath}; these guards would " +
                "otherwise pass by reading nothing."
        }
        return root
    }

    private fun buildScript(): String = File(moduleRoot(), "build.gradle.kts").readText()

    /**
     * The build script with whole-line comments removed.
     *
     * Every guard here reads it, and it is unusually comment-heavy: the real signing block sits
     * directly under a paragraph that uses the words "storePassword", "keystore" and "hardcoded"
     * to explain why none of them are literals. Searching the raw text for those words finds the
     * explanation and reports it as the defect.
     */
    private fun buildScriptCode(): String =
        buildScript().lineSequence().filterNot { it.trimStart().startsWith("//") }
            .joinToString("\n")

    private fun gitignore(): String {
        val file = File(moduleRoot(), "../.gitignore")
        require(file.isFile) { "no .gitignore at ${file.absolutePath}" }
        return file.readText()
    }

    private fun gitignoreRules(): List<String> =
        gitignore().lineSequence().map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .toList()

    private fun manifest(flavor: String): String {
        val file = File(moduleRoot(), "src/$flavor/AndroidManifest.xml")
        require(file.isFile) { "no AndroidManifest.xml for flavor $flavor" }
        return file.readText()
    }

    // --- credentials stay out of the source tree ------------------------------------------------

    @Test
    fun `no signing credential is a literal in the build script`() {
        val code = buildScriptCode()
        // Each of these is the start of an assignment whose right-hand side is a string literal,
        // which is the shape a pasted password takes. The real block assigns from
        // keystoreProperties.getProperty(...) instead, so it matches none of them.
        listOf("storePassword", "keyPassword", "keyAlias", "storeFile").forEach { property ->
            val literal = Regex("""$property\s*=\s*"""")
            assertFalse(
                "$property is assigned a string literal in app/build.gradle.kts. Signing " +
                    "credentials must come from the gitignored keystore.properties, never " +
                    "from a tracked file.",
                literal.containsMatchIn(code),
            )
        }
    }

    @Test
    fun `release signing reads its values from an untracked properties file`() {
        val code = buildScriptCode()
        assertTrue(
            "app/build.gradle.kts no longer reads keystore.properties. That file is the only " +
                "credential source this project's signing design has; losing it means the " +
                "credentials moved somewhere else, and every other candidate is tracked.",
            code.contains("keystore.properties"),
        )
        assertTrue(
            "the release signingConfig no longer reads from keystoreProperties",
            Regex("""storePassword\s*=\s*keystoreProperties""").containsMatchIn(code),
        )
    }

    @Test
    fun `no signing credential is exported through BuildConfig or a resource`() {
        // Checked structurally, over the argument list of every field the build generates,
        // rather than by searching the script for the Gradle property names. The first version
        // of this test did the latter and a mutation walked straight through it: the realistic
        // form of this mistake names the constant SIGNING_STORE_PASSWORD, which contains none
        // of "storePassword", "keyPassword" or "storeFile" as a substring. The name a developer
        // picks is theirs, so the guard cannot depend on it being the DSL's.
        val generated = Regex("""(buildConfigField|resValue)\s*\(([^)]*)\)""")
            .findAll(buildScriptCode())
            .map { it.groupValues[2] }
            .toList()
        assertTrue("found no generated BuildConfig fields to check", generated.isNotEmpty())
        // Separators are allowed between the words because that is what distinguishes
        // STORE_PASSWORD from storePassword, and both ship the same value.
        val credentialName = Regex(
            """(store|key)[_\-\s]*(password|alias)|keystore|passphrase""",
            RegexOption.IGNORE_CASE,
        )
        generated.forEach { arguments ->
            assertFalse(
                "a generated field names a signing credential ($arguments). BuildConfig and " +
                    "resources are compiled into the APK, where anyone who installs it can " +
                    "read the value back out -- which is the whole point of keeping the " +
                    "credentials in an untracked file.",
                credentialName.containsMatchIn(arguments),
            )
            assertFalse(
                "a generated field reads from keystoreProperties ($arguments), which ships a " +
                    "signing credential inside the APK whatever the field is called.",
                arguments.contains("keystoreProperties"),
            )
        }
    }

    @Test
    fun `the tracked template holds placeholders rather than real credentials`() {
        val template = File(moduleRoot(), "../keystore.properties.example")
        assertTrue(
            "keystore.properties.example is missing. It is the only tracked documentation of " +
                "which keys release signing needs, and without it the next person has to " +
                "rediscover them from the build script.",
            template.isFile,
        )
        val values = template.readText().lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains('=') }
            .associate { line -> line.substringBefore('=') to line.substringAfter('=') }
        assertTrue(
            "the template documents none of the signing keys",
            values.keys.containsAll(listOf("storeFile", "storePassword", "keyAlias", "keyPassword")),
        )
        listOf("storePassword", "keyAlias", "keyPassword").forEach { property ->
            assertTrue(
                "keystore.properties.example sets $property to something other than a " +
                    "placeholder. A tracked file must never carry a real credential.",
                values[property] == "changeme",
            )
        }
    }

    @Test
    fun `the tracked gradle properties file declares no credential`() {
        // gradle.properties is tracked, and it is the single most likely place for a signing
        // credential to end up: half the signing guides on the internet put RELEASE_STORE_PASSWORD
        // there, Gradle reads it with no extra code, and it works. The build script guards above
        // would not see it, because the credential would never appear in the script at all.
        val file = File(moduleRoot(), "../gradle.properties")
        assertTrue("no gradle.properties at ${file.absolutePath}", file.isFile)
        val declared = file.readText().lineSequence().map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains('=') }
            .map { it.substringBefore('=') }
            .toList()
        assertTrue("read no properties out of gradle.properties", declared.isNotEmpty())
        val credentialName = Regex(
            """(store|key)[_\-.\s]*(password|alias)|keystore|passphrase|signingkey""",
            RegexOption.IGNORE_CASE,
        )
        declared.forEach { property ->
            assertFalse(
                "gradle.properties declares $property. That file is tracked, so a value there " +
                    "is committed; signing credentials belong in the gitignored " +
                    "keystore.properties, or in CI secrets.",
                credentialName.containsMatchIn(property),
            )
        }
    }

    // --- signing material cannot be committed ---------------------------------------------------

    @Test
    fun `keystores and their credentials are gitignored`() {
        val rules = gitignoreRules()
        listOf("*.jks", "*.keystore", "/keystore.properties").forEach { rule ->
            assertTrue(
                "$rule is not in .gitignore. Without it a release keystore or its passwords " +
                    "can be committed, and git history is not something a later commit undoes.",
                rules.contains(rule),
            )
        }
    }

    @Test
    fun `release artifacts are gitignored`() {
        val rules = gitignoreRules()
        listOf("*.apk", "*.aab").forEach { rule ->
            assertTrue(
                "$rule is not in .gitignore; a signed release artifact can be committed.",
                rules.contains(rule),
            )
        }
    }

    // --- the release build type stays distinct from debug ----------------------------------------

    @Test
    fun `release never falls back to debug signing`() {
        val code = buildScriptCode()
        // The release block is the one that matters: Android signs debug builds with the shared
        // debug key by default, and pointing release at that same config is the "make the
        // release installable" shortcut that produces an artifact Play rejects -- or worse, one
        // that installs and is trusted while being signed by a key checked into every SDK.
        val releaseBlock = Regex("""release\s*\{(.*?)\n        \}""", RegexOption.DOT_MATCHES_ALL)
            .find(code)?.groupValues?.get(1)
        requireNotNull(releaseBlock) { "could not locate the release build type in the build script" }
        assertFalse(
            "the release build type references debug signing.",
            Regex("""signingConfig\s*=\s*signingConfigs\.(getByName\(\s*"debug"|debug)""")
                .containsMatchIn(releaseBlock),
        )
        assertTrue(
            "the release build type no longer enables R8. Minification is what makes the " +
                "shipped artifact match the mapping file uploaded for crash reports.",
            Regex("""isMinifyEnabled\s*=\s*true""").containsMatchIn(releaseBlock),
        )
    }

    @Test
    fun `an absent keystore leaves release unsigned rather than debug-signed`() {
        val code = buildScriptCode()
        // Both halves of the conditional matter. `hasReleaseSigningConfig` is what makes a
        // checkout without credentials build at all, and it must gate the *assignment* -- an
        // else branch that assigned anything would be assigning the debug key.
        assertTrue(
            "release signing is no longer gated on hasReleaseSigningConfig",
            Regex("""if\s*\(hasReleaseSigningConfig\)""").containsMatchIn(code),
        )
        assertFalse(
            "the signing gate has an else branch. There is no correct fallback certificate for " +
                "a production build: unsigned is the only safe outcome when credentials are " +
                "absent, because every alternative signs with a key that is not the release key.",
            Regex("""if\s*\(hasReleaseSigningConfig\)\s*\{[^}]*\}\s*else""").containsMatchIn(code),
        )
    }

    // --- production configuration carries no development endpoint --------------------------------

    @Test
    fun `the production backend URL is https and is not a developer machine`() {
        val code = buildScriptCode()
        val prodUrl = Regex("""prodApiBaseUrl\s*=[\s\S]*?\?:\s*"([^"]+)"""")
            .find(code)?.groupValues?.get(1)
        requireNotNull(prodUrl) { "could not find the prod API base URL default" }
        assertTrue("the prod API base URL is not https: $prodUrl", prodUrl.startsWith("https://"))
        listOf("localhost", "127.0.0.1", "10.0.2.2").forEach { host ->
            assertFalse(
                "the prod API base URL points at $host, which is a developer machine.",
                prodUrl.contains(host),
            )
        }
    }

    @Test
    fun `production disallows cleartext traffic and development allows it`() {
        assertTrue(
            "the prod flavor no longer disables cleartext traffic. Production must never send " +
                "auth tokens, resumes or interview answers over plain HTTP.",
            manifest("prod").contains("""android:usesCleartextTraffic="false""""),
        )
        // Asserted so that "disable cleartext everywhere" is not mistaken for the fix: the dev
        // flavor talks to a plain-HTTP local backend and would stop working, which is how this
        // guard stays a release guard rather than a blanket policy.
        assertTrue(
            "the dev flavor no longer allows cleartext traffic; the local HTTP backend is " +
                "unreachable without it.",
            manifest("dev").contains("""android:usesCleartextTraffic="true""""),
        )
    }
}
