// ssrf-guard-okhttp — OkHttp Interceptor + Dns implementation.
// No Spring dep — OkHttp users typically build clients directly via
// OkHttpClient.Builder. Two extension points:
//   - Interceptor on .addInterceptor(...) for URL-level checks
//   - Dns on .dns(...) for IP-level checks (the OkHttp equivalent of
//     Apache HttpClient's DnsResolver)

base {
    archivesName.set("ssrf-guard-okhttp")
}

dependencies {
    api(project(":ssrf-guard-core"))

    compileOnly("com.squareup.okhttp3:okhttp:4.12.0")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("com.squareup.okhttp3:okhttp:4.12.0")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

mavenPublishing {
    coordinates(
        providers.gradleProperty("GROUP").get(),
        "ssrf-guard-okhttp",
        providers.gradleProperty("VERSION").get()
    )
    pom {
        name.set("SSRF Guard — OkHttp")
        description.set("OkHttp Interceptor + Dns implementations for SSRF Guard. No Spring dependency.")
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
