// ssrf-guard-httpclient5 — Apache HttpClient 5 DnsResolver + RedirectStrategy.
// These are what close the TOCTOU window: validate=connect consistency.
// Used directly by ssrf-guard-restclient (which wraps RestClient on HttpClient 5).

base {
    archivesName.set("ssrf-guard-httpclient5")
}

dependencies {
    api(project(":ssrf-guard-core"))

    // Apache HttpClient 5 — its DnsResolver / RedirectStrategy interfaces are
    // what we implement.
    api("org.apache.httpcomponents.client5:httpclient5")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // Spring Boot autoconfig — registers the resolver + redirect strategy as
    // beans when this module is on the classpath. Optional: the underlying
    // types work in any Java app, but Spring users get them wired up.
    compileOnly("org.springframework.boot:spring-boot-autoconfigure")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

mavenPublishing {
    coordinates(
        providers.gradleProperty("GROUP").get(),
        "ssrf-guard-httpclient5",
        providers.gradleProperty("VERSION").get()
    )
    pom {
        name.set("SSRF Guard — Apache HttpClient 5")
        description.set("Apache HttpClient 5 DnsResolver + RedirectStrategy implementations for SSRF Guard. Closes the TOCTOU window between policy check and socket connect.")
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
