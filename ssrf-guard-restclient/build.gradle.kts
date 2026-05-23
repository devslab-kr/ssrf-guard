// ssrf-guard-restclient — Spring RestClient (Spring 6.1+) interceptor +
// auto-config. Wraps the autoconfigured RestClient.Builder so every
// RestClient the consumer builds inherits the SSRF policy.

base {
    archivesName.set("ssrf-guard-restclient")
}

dependencies {
    api(project(":ssrf-guard-core"))
    api(project(":ssrf-guard-httpclient5"))

    api("org.springframework.boot:spring-boot-autoconfigure")
    api("org.springframework.boot:spring-boot-starter")

    // RestClient lives in spring-web — required at runtime.
    compileOnly("org.springframework.boot:spring-boot-starter-web")

    // Micrometer — compileOnly so the dep stays opt-in. Used at runtime via
    // ObjectProvider<MeterRegistry>: when consumers have actuator on the
    // classpath we wire MicrometerSsrfGuardMetrics; otherwise NoOp.
    compileOnly("io.micrometer:micrometer-core")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-web")
    testImplementation("io.micrometer:micrometer-core")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

mavenPublishing {
    coordinates(
        providers.gradleProperty("GROUP").get(),
        "ssrf-guard-restclient",
        providers.gradleProperty("VERSION").get()
    )
    pom {
        name.set("SSRF Guard — Spring RestClient")
        description.set("SSRF Guard auto-configuration for Spring 6.1+ RestClient. Wraps the autoconfigured RestClient.Builder with whitelist + private-IP + redirect-revalidation defenses.")
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
