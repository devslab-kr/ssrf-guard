// ssrf-guard-feign — Spring Cloud OpenFeign support. Feign declarative HTTP
// clients are the MSA-standard binding in many Spring Cloud apps; this
// module ships a RequestInterceptor that runs the URL policy before each
// remote call, plus a small auto-config that registers the interceptor
// + property binding.

base {
    archivesName.set("ssrf-guard-feign")
}

dependencies {
    api(project(":ssrf-guard-core"))

    api("org.springframework.boot:spring-boot-autoconfigure")

    // Feign 13 — comes with the Spring Cloud BOM.
    compileOnly("io.github.openfeign:feign-core")
    // spring-cloud-openfeign-core for FeignClientFactoryBean and friends.
    compileOnly("org.springframework.cloud:spring-cloud-openfeign-core")

    compileOnly("io.micrometer:micrometer-core")
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.github.openfeign:feign-core")
    testImplementation("org.springframework.cloud:spring-cloud-starter-openfeign")
    testImplementation("io.micrometer:micrometer-core")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

mavenPublishing {
    coordinates(
        providers.gradleProperty("GROUP").get(),
        "ssrf-guard-feign",
        providers.gradleProperty("VERSION").get()
    )
    pom {
        name.set("SSRF Guard — Spring Cloud OpenFeign")
        description.set("SSRF Guard auto-configuration for Spring Cloud OpenFeign. RequestInterceptor that validates every outbound Feign call against the same whitelist + IP-literal + userinfo policy.")
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
