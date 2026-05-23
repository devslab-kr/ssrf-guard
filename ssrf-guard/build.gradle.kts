// ssrf-guard — meta artifact. Aggregates the modules that v2.0.0 users had
// in a single jar:
//   - ssrf-guard-restclient (Spring RestClient autoconfig)
//   - ssrf-guard-httpclient5 (DnsResolver + RedirectStrategy)
//   - ssrf-guard-core (transitively)
//
// Consumers upgrading 2.x → 3.x can keep `kr.devslab:ssrf-guard:3.x` on the
// classpath and inherit everything they had before. New modules (resttemplate,
// webclient, feign, springai, okhttp, jdkhttp) are opt-in via their own
// coordinates.

base {
    archivesName.set("ssrf-guard")
}

dependencies {
    api(project(":ssrf-guard-core"))
    api(project(":ssrf-guard-httpclient5"))
    api(project(":ssrf-guard-restclient"))

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-web")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

mavenPublishing {
    coordinates(
        providers.gradleProperty("GROUP").get(),
        "ssrf-guard",
        providers.gradleProperty("VERSION").get()
    )
    pom {
        name.set("SSRF Guard")
        description.set("SSRF (Server-Side Request Forgery) protection for Spring Boot — meta artifact. Bundles ssrf-guard-core, ssrf-guard-httpclient5, and ssrf-guard-restclient for back-compat with the v2.0.0 starter coordinate. New consumers should pick the module(s) matching their HTTP client.")
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
