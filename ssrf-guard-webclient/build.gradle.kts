// ssrf-guard-webclient — Spring WebFlux WebClient support.
// Adds an ExchangeFilterFunction that validates each request's URI before the
// reactive pipeline emits it to the network. Pairs with reactor-netty's
// own resolver — we run the URL-level check; the DNS-time private-IP check
// lives in the JDK's default resolver pathway (recorded as a follow-up:
// reactor-netty supports custom HostnameResolverSupplier, but the surface
// area is larger and orthogonal to the SSRF check itself).

base {
    archivesName.set("ssrf-guard-webclient")
}

dependencies {
    api(project(":ssrf-guard-core"))
    api("org.springframework.boot:spring-boot-autoconfigure")

    // WebFlux for WebClient.
    compileOnly("org.springframework.boot:spring-boot-starter-webflux")

    compileOnly("io.micrometer:micrometer-core")
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webflux")
    testImplementation("io.micrometer:micrometer-core")
    testImplementation("io.projectreactor:reactor-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

mavenPublishing {
    coordinates(
        providers.gradleProperty("GROUP").get(),
        "ssrf-guard-webclient",
        providers.gradleProperty("VERSION").get()
    )
    pom {
        name.set("SSRF Guard — Spring WebClient (WebFlux)")
        description.set("SSRF Guard auto-configuration for Spring WebFlux WebClient. ExchangeFilterFunction validates outbound URIs against the same whitelist + IP-literal + userinfo policy as the RestClient/RestTemplate modules.")
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
