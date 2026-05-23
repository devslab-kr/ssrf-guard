// ssrf-guard-jdkhttp — java.net.http.HttpClient (JDK 11+ standard).
// No Spring dependency at all — this module is for consumers who use the
// JDK's built-in client (CLI tools, Quarkus apps, Lambda functions, etc.).

base {
    archivesName.set("ssrf-guard-jdkhttp")
}

dependencies {
    api(project(":ssrf-guard-core"))

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

mavenPublishing {
    coordinates(
        providers.gradleProperty("GROUP").get(),
        "ssrf-guard-jdkhttp",
        providers.gradleProperty("VERSION").get()
    )
    pom {
        name.set("SSRF Guard — JDK HttpClient (java.net.http)")
        description.set("SSRF Guard for the JDK standard HttpClient (Java 11+). No Spring dependency — use directly with HttpClient.newBuilder().")
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
