// Root build — no jar. All publishable artifacts live in subprojects.
plugins {
    id("io.spring.dependency-management") version "1.1.6" apply false
    id("com.vanniktech.maven.publish") version "0.30.0" apply false
}

// Aggregate test report — `./gradlew test` at the root runs every subproject's
// tests; this collects them into a single HTML/XML for CI convenience.
val rootGroup: String = providers.gradleProperty("GROUP").get()
val rootVersion: String = providers.gradleProperty("VERSION").get()

allprojects {
    group = rootGroup
    version = rootVersion
}

subprojects {
    // All publishable subprojects share the same plugin set + Java toolchain.
    // The few subprojects that don't publish (none currently) can opt out by
    // not applying maven.publish.
    apply(plugin = "java-library")
    apply(plugin = "jacoco")
    apply(plugin = "io.spring.dependency-management")
    apply(plugin = "com.vanniktech.maven.publish")

    // Repositories are pinned in settings.gradle.kts via
    // dependencyResolutionManagement (FAIL_ON_PROJECT_REPOS). Per-subproject
    // overrides would defeat the central pin.

    configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
        withSourcesJar()
        withJavadocJar()
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        // -parameters keeps AOP-readable param names; -Xlint:all minus the
        // noisy classfile/processing/serial categories so -Werror stays
        // useful for real code issues.
        options.compilerArgs.addAll(listOf(
            "-parameters",
            "-Xlint:all,-classfile,-processing,-serial",
            "-Werror"
        ))
    }

    tasks.withType<Javadoc>().configureEach {
        options.encoding = "UTF-8"
        (options as StandardJavadocDocletOptions).apply {
            addBooleanOption("Xdoclint:none", true)
            addBooleanOption("html5", true)
            locale = "en_US"
        }
    }

    configure<io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension> {
        imports {
            mavenBom("org.springframework.boot:spring-boot-dependencies:3.5.6")
            // Spring Cloud BOM — pins feign-core / spring-cloud-openfeign for
            // the feign module. Aligned with Spring Boot 3.5.x.
            mavenBom("org.springframework.cloud:spring-cloud-dependencies:2024.0.0")
        }
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
            showStandardStreams = false
        }
        systemProperty("file.encoding", "UTF-8")
        finalizedBy(tasks.named("jacocoTestReport"))
    }

    configure<JacocoPluginExtension> {
        toolVersion = "0.8.13"
    }

    tasks.named<JacocoReport>("jacocoTestReport").configure {
        dependsOn(tasks.named("test"))
        reports {
            xml.required.set(true)
            html.required.set(true)
            csv.required.set(false)
        }
    }

    // Vanniktech's auto-generated javadoc jar hardcodes its archive base name
    // to `<project>-maven-javadoc`; without this the GitHub Release ends up
    // with names like `ssrf-guard-core-maven-javadoc-X.Y.Z-javadoc.jar`.
    // Pin it inside afterEvaluate so we win the last-writer race against the
    // plugin's own configuration phase.
    afterEvaluate {
        tasks.findByName("mavenPlainJavadocJar")?.let {
            (it as AbstractArchiveTask).archiveBaseName.set(project.name)
        }

        // Same lesson as the api-log starter: subproject publications under
        // the Spring Boot BOM have no explicit dep versions, so Gradle's
        // module-metadata validator rejects them. `versionMapping {
        // allVariants { fromResolutionResult() } }` pins the BOM-resolved
        // versions into the generated .module + .pom so downstream consumers
        // don't need our BOMs at all.
        extensions.findByType<PublishingExtension>()?.apply {
            publications.withType<MavenPublication>().configureEach {
                versionMapping {
                    allVariants {
                        fromResolutionResult()
                    }
                }
            }
        }
    }
}
