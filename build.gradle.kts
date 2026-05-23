// Single-module Gradle build. The on-disk jar filename and the published Maven
// artifact id are both `ssrf-guard` (the short name picked for v2.0.0 to match
// the api-log starter convention; the legacy 1.x coordinate was
// `com.devs.lab:ssrf-guard-spring-boot-starter`).

plugins {
    `java-library`
    jacoco
    id("org.springframework.boot") version "3.5.6" apply false
    id("io.spring.dependency-management") version "1.1.6"
    id("com.vanniktech.maven.publish") version "0.30.0"
}

group = providers.gradleProperty("GROUP").get()
version = providers.gradleProperty("VERSION").get()

base {
    // Local build/libs/*.jar filename (Vanniktech pins the Maven coordinate
    // separately via mavenPublishing.coordinates(...) below).
    archivesName.set("ssrf-guard")
}

// Vanniktech's javadoc jar task hardcodes its archive base name to
// `<project>-maven-javadoc` and only sets it inside its plugin's own
// afterEvaluate. Without this override the GitHub Release ends up with a
// confusing `ssrf-guard-maven-javadoc-X.Y.Z-javadoc.jar`. Configure inside
// afterEvaluate so we're the last writer.
afterEvaluate {
    tasks.named<AbstractArchiveTask>("mavenPlainJavadocJar").configure {
        archiveBaseName.set("ssrf-guard")
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    // -parameters: keep AOP-readable param names. -Xlint enabled but the
    // noisy categories (classfile/processing/serial) are excluded so -Werror
    // stays usable for real code issues without tripping on annotation-
    // processor noise.
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

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:3.5.6")
    }
}

dependencies {
    // Pulled in transitively for every consumer:
    api("org.springframework.boot:spring-boot-autoconfigure")
    api("org.springframework.boot:spring-boot-starter")

    // Apache HttpClient 5 — the wrapped RestClient runs on top of this.
    api("org.apache.httpcomponents.client5:httpclient5")

    // Lombok — compile + annotation-processor only.
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // Auto-configuration metadata processor.
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    // Spring Web — RestClient lives here. compileOnly so pure-WebFlux consumers
    // (who don't need RestClient at all) aren't forced to pull spring-web.
    // The autoconfig is gated by @ConditionalOnClass(RestClient.class) so its
    // absence is silent.
    compileOnly("org.springframework.boot:spring-boot-starter-web")

    // Silences cosmetic "cannot find javax.annotation.Nonnull" warnings from
    // resolving Spring's @Nullable. Not exposed to consumers.
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-web")
    testImplementation("org.assertj:assertj-core")

    // MockWebServer drives the integration tests against a real socket so the
    // SSRF interceptor + DNS resolver + redirect strategy are exercised on a
    // genuine HTTP stack rather than mocked.
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")

    // Explicit launcher pin. JUnit Jupiter 5.11+ requires
    // junit-platform-launcher >= 1.11; Gradle 8.10 still bundles 1.10.x.
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = false
    }
    systemProperty("file.encoding", "UTF-8")
    finalizedBy(tasks.jacocoTestReport)
}

jacoco {
    toolVersion = "0.8.13"
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
}

// Same lesson as api-log: subproject publications under the Spring Boot BOM
// have no explicit dep versions, so Gradle's module-metadata validator
// rejects them. `versionMapping { allVariants { fromResolutionResult() } }`
// pins the BOM-resolved versions into the generated .module and .pom so
// downstream consumers don't need our BOM.
publishing {
    publications.withType<MavenPublication>().configureEach {
        versionMapping {
            allVariants {
                fromResolutionResult()
            }
        }
    }
}

mavenPublishing {
    coordinates(
        providers.gradleProperty("GROUP").get(),
        "ssrf-guard",
        providers.gradleProperty("VERSION").get()
    )

    pom {
        developers {
            developer {
                id.set(providers.gradleProperty("POM_DEVELOPER_ID"))
                name.set(providers.gradleProperty("POM_DEVELOPER_NAME"))
                url.set(providers.gradleProperty("POM_DEVELOPER_URL"))
                email.set(providers.gradleProperty("POM_DEVELOPER_EMAIL"))
                organization.set(providers.gradleProperty("POM_ORGANIZATION_NAME"))
                organizationUrl.set(providers.gradleProperty("POM_ORGANIZATION_URL"))
            }
        }

        organization {
            name.set(providers.gradleProperty("POM_ORGANIZATION_NAME"))
            url.set(providers.gradleProperty("POM_ORGANIZATION_URL"))
        }

        issueManagement {
            system.set(providers.gradleProperty("POM_ISSUE_SYSTEM"))
            url.set(providers.gradleProperty("POM_ISSUE_URL"))
        }
    }
}
