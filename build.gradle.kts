plugins {
    `java-library`
}

repositories {
    mavenCentral()
    maven {
        name = "codegenome"
        url = uri("https://artifacts.codegenomeproject.org/maven")
        credentials {
            username = providers.gradleProperty("codegenomeUsername").orNull ?: System.getenv("CODEGENOME_USERNAME")
            password = providers.gradleProperty("codegenomePassword").orNull ?: System.getenv("CODEGENOME_TOKEN")
        }
        content {
            includeGroupAndSubgroups("org.openrewrite")
            includeGroupAndSubgroups("io.moderne")
        }
    }
}

// The matrix measures whatever OpenRewrite currently does, so a cached snapshot would silently
// report yesterday's answer.
configurations.all {
    resolutionStrategy.cacheDynamicVersionsFor(0, "seconds")
    resolutionStrategy.cacheChangingModulesFor(0, "seconds")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

dependencies {
    implementation(platform("org.openrewrite:rewrite-bom:latest.integration"))
    implementation("org.openrewrite:rewrite-core")
    implementation("org.openrewrite:rewrite-maven")
    implementation("org.openrewrite:rewrite-gradle")
    implementation("org.openrewrite:rewrite-groovy")
    implementation("org.openrewrite:rewrite-kotlin")
    implementation("org.openrewrite:rewrite-toml")
    implementation("org.openrewrite:rewrite-javascript")
    implementation("org.openrewrite:rewrite-json")
    implementation("org.openrewrite:rewrite-properties")
    implementation("org.openrewrite.recipe:rewrite-java-dependencies:latest.integration")

    testImplementation(platform("org.junit:junit-bom:latest.release"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.junit.jupiter:junit-jupiter-params")
    testImplementation("org.junit.platform:junit-platform-suite")
    testImplementation("org.openrewrite:rewrite-test")
    testImplementation("org.assertj:assertj-core:latest.release")
    testImplementation("org.jspecify:jspecify:latest.release")
    testImplementation("com.squareup.okhttp3:mockwebserver3:latest.release")
    testImplementation("com.squareup.okhttp3:okhttp-tls:latest.release")
    testImplementation("org.apache.commons:commons-compress:latest.release")
    // Real Gradle builds behind GradleTest's markers; the model compiles against gradleApi(), so we supply it too.
    testImplementation("org.openrewrite.gradle.tooling:model:latest.integration")
    testImplementation(gradleApi())
    // GroovyTemplate builds on JavaTemplate, which needs a JDK-specific parser at runtime.
    testRuntimeOnly("org.openrewrite:rewrite-java-25")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Each ProviderVersioning implementation is a @Suite; a TCK run is that class. The IDE runs
// whatever is clicked through this task: a suite, or a test class on its own under the default
// scheme in junit-platform.properties. Unfiltered, it runs both, so prefer `tck` from the CLI.
tasks.test {
    useJUnitPlatform()
    maxHeapSize = "2g"
    ignoreFailures = true
}

val tck by tasks.registering {
    description = "Runs the backpatch TCK once per provider versioning scheme"
    group = "verification"
}
tasks.check { dependsOn(tck) }

listOf(
    "providers.OseraMavenCentralCare",
    "providers.SemverV2BuildMetadata",
    "providers.npm.NextPatchPrerelease"
).forEach { scheme ->
    val name = scheme.substringAfterLast('.')
    val run = tasks.register<Test>("tck$name") {
        description = "Runs the backpatch TCK against $name"
        group = "verification"
        testClassesDirs = sourceSets.test.get().output.classesDirs
        classpath = sourceSets.test.get().runtimeClasspath
        useJUnitPlatform()
        maxHeapSize = "2g"
        filter.includeTestsMatching("org.finos.osera.$scheme")
        reports.html.outputLocation = layout.buildDirectory.dir("reports/tck/$name")
        reports.junitXml.outputLocation = layout.buildDirectory.dir("test-results/tck/$name")
        // Failures are the evaluation, not a broken build.
        ignoreFailures = true
    }
    tck { dependsOn(run) }
}

// The Gradle running the TCK also drives the tooling models behind GradleTest.
tasks.withType<Test>().configureEach {
    systemProperty("org.openrewrite.test.gradleVersion", gradle.gradleVersion)
}

// Renovate and Dependabot are not JVM tools and are not on Maven Central. They are installed by
// their own package managers under build/, and the cells that need them are skipped when the
// toolchain is absent rather than reported as a failed remediation.
val renovateVersion = "41.173.1"
val dependabotVersion = "0.394.0"
val renovateHome = layout.buildDirectory.dir("renovate")
val dependabotGems = layout.buildDirectory.dir("dependabot/gems")

// Node arrives through a version manager and Ruby through Homebrew, so neither is on the daemon's
// PATH. A login shell knows where they are; -Posera.<tool> overrides when it doesn't.
fun locate(command: String): String? =
    if (command.startsWith("/")) command.takeIf { File(it).canExecute() }
    else runCatching {
        val shell = System.getenv("SHELL") ?: "/bin/sh"
        val process = ProcessBuilder(shell, "-lc", "command -v $command").start()
        process.inputStream.bufferedReader().readText().trim().takeIf { process.waitFor() == 0 && it.isNotEmpty() }
    }.getOrNull()

fun tool(name: String, vararg candidates: String) = providers.gradleProperty("osera.$name")
    .orElse(providers.provider { candidates.firstNotNullOfOrNull(::locate) })

val node = tool("node", "node")
val npm = tool("npm", "npm")
val ruby = tool("ruby", "/usr/local/opt/ruby/bin/ruby", "/opt/homebrew/opt/ruby/bin/ruby", "ruby")

val installRenovate by tasks.registering(Exec::class) {
    description = "Installs Renovate, one half of the remediation matrix that is not a recipe"
    onlyIf { npm.isPresent }
    outputs.dir(renovateHome)
    workingDir(renovateHome)
    doFirst {
        // Without a manifest of its own, npm walks up the tree, finds one that has nothing to do
        // with this, and reports itself up to date having installed nothing.
        val home = renovateHome.get().asFile
        home.mkdirs()
        home.resolve("package.json").writeText(
            """
            {
              "name": "osera-renovate",
              "private": true,
              "dependencies": { "renovate": "$renovateVersion" }
            }
            """.trimIndent()
        )
    }
    commandLine(npm.getOrElse("npm"), "install", "--silent", "--no-audit", "--no-fund")
}

// macOS ships Ruby 2.6, which the Dependabot gems reject. Testing only that a ruby exists lets the
// gem install fail the whole build rather than skipping the cells it feeds.
fun rubyIsUsable(path: String): Boolean = runCatching {
    ProcessBuilder(path, "-e", "exit(RUBY_VERSION.split('.')[0].to_i >= 3 ? 0 : 1)")
        .redirectErrorStream(true)
        .start()
        .let { it.inputStream.readAllBytes(); it.waitFor() == 0 }
}.getOrDefault(false)

val installDependabot by tasks.registering(Exec::class) {
    description = "Installs Dependabot's Maven, Gradle and npm gems, the other half"
    onlyIf { ruby.isPresent && rubyIsUsable(ruby.get()) }
    outputs.dir(dependabotGems)
    environment("GEM_HOME", dependabotGems.get().asFile.absolutePath)
    // The gem next to the chosen ruby, not whichever one is first on the path.
    commandLine(
        ruby.map { File(File(it).parentFile, "gem").path }.getOrElse("gem"),
        "install", "--no-document", "--silent",
        "dependabot-maven:$dependabotVersion", "dependabot-gradle:$dependabotVersion",
        "dependabot-npm_and_yarn:$dependabotVersion"
    )
}

tasks.withType<Test>().configureEach {
    // npm's own resolution, and the npx that starts the recipe's RPC server, read config from the
    // file named here. MockNpmRegistry writes its URL into it once it has a port; the path has to
    // be fixed because the environment of a child process cannot be changed after the JVM starts.
    val npmrc = layout.buildDirectory.file("npm/osera.npmrc").get().asFile
    doFirst { npmrc.parentFile.mkdirs() }
    systemProperty("osera.npmrc", npmrc.absolutePath)
    environment("npm_config_userconfig", npmrc.absolutePath)
    dependsOn(installRenovate, installDependabot)
    systemProperty("osera.renovate", renovateHome.get().asFile.absolutePath)
    systemProperty("osera.dependabot", dependabotGems.get().asFile.absolutePath)
    // What each external tool decided, in its own words, next to the report that summarises it.
    systemProperty("osera.tools.log", layout.buildDirectory.dir("tool-logs").get().asFile.absolutePath)
    node.orNull?.let { systemProperty("osera.node", it) }
    ruby.orNull?.let { systemProperty("osera.ruby", it) }
}
