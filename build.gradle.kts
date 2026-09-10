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

listOf("MavenCentralCare", "SemverV2BuildMetadata").forEach { scheme ->
    val run = tasks.register<Test>("tck$scheme") {
        description = "Runs the backpatch TCK against $scheme"
        group = "verification"
        testClassesDirs = sourceSets.test.get().output.classesDirs
        classpath = sourceSets.test.get().runtimeClasspath
        useJUnitPlatform()
        maxHeapSize = "2g"
        filter.includeTestsMatching("org.finos.osera.providers.$scheme")
        reports.html.outputLocation = layout.buildDirectory.dir("reports/tck/$scheme")
        reports.junitXml.outputLocation = layout.buildDirectory.dir("test-results/tck/$scheme")
        // Failures are the evaluation, not a broken build.
        ignoreFailures = true
    }
    tck { dependsOn(run) }
}

// The Gradle running the TCK also drives the tooling models behind GradleTest.
tasks.withType<Test>().configureEach {
    systemProperty("org.openrewrite.test.gradleVersion", gradle.gradleVersion)
}
