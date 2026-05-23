// ssrf-guard-core — policy types, NetUtil, metrics interface.
// Goal: usable from any HTTP client wrapper without dragging Spring in.
//
// Compile classpath:
//   - Spring Boot's @ConfigurationProperties (so consumers can bind YAML).
//     Pulled via spring-boot — autoconfigure is NOT included; this module
//     intentionally registers no AutoConfiguration imports of its own. The
//     properties class becomes useful when something downstream applies
//     @EnableConfigurationProperties.
//   - Micrometer's API (compileOnly) — the SsrfGuardMetrics interface has a
//     Micrometer implementation; consumers who don't want metrics never see
//     the dep on their classpath.

base {
    archivesName.set("ssrf-guard-core")
}

dependencies {
    // Spring Boot's @ConfigurationProperties — pulled in transitively so
    // consumers binding `ssrf.guard.*` get IDE completion.
    api("org.springframework.boot:spring-boot")

    // SLF4J for structured logging on blocked requests. Already on every
    // Spring Boot classpath.
    api("org.slf4j:slf4j-api")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // Auto-configuration metadata processor — generates spring-configuration-
    // metadata.json for IDE completion on ssrf.guard.* properties.
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    // Micrometer for metrics — compileOnly, runtime gated by class presence.
    compileOnly("io.micrometer:micrometer-core")

    // Silences "cannot find javax.annotation.Nonnull" warnings from Spring's
    // @Nullable. Not exposed.
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.micrometer:micrometer-core")
    testImplementation("org.assertj:assertj-core")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

mavenPublishing {
    coordinates(
        providers.gradleProperty("GROUP").get(),
        "ssrf-guard-core",
        providers.gradleProperty("VERSION").get()
    )
    pom {
        name.set("SSRF Guard — Core")
        description.set("Core policy + NetUtil + metrics interface for SSRF Guard. Use one of the client-specific modules to actually intercept requests.")
        url.set(providers.gradleProperty("POM_URL"))
        inceptionYear.set(providers.gradleProperty("POM_INCEPTION_YEAR"))
        licenses {
            license {
                name.set(providers.gradleProperty("POM_LICENSE_NAME"))
                url.set(providers.gradleProperty("POM_LICENSE_URL"))
                distribution.set(providers.gradleProperty("POM_LICENSE_DIST"))
            }
        }
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
        scm {
            url.set(providers.gradleProperty("POM_SCM_URL"))
            connection.set(providers.gradleProperty("POM_SCM_CONNECTION"))
            developerConnection.set(providers.gradleProperty("POM_SCM_DEV_CONNECTION"))
        }
        issueManagement {
            system.set(providers.gradleProperty("POM_ISSUE_SYSTEM"))
            url.set(providers.gradleProperty("POM_ISSUE_URL"))
        }
    }
}
