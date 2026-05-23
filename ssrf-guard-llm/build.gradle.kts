// ssrf-guard-llm — framework-agnostic core for LLM-agent tool input validation.
//
// Holds the JSON-walking / URL-extraction / policy-validation logic that
// every framework adapter (-springai, -langchain4j, future -mcp, …) shares.
// Adapters become ~30-line wrappers that delegate to ToolInputGuard and
// translate between this module's neutral API and the framework's own
// tool callback interface.
//
// Dependencies:
//   - ssrf-guard-core for UrlPolicy / SsrfGuardException / BlockReason
//   - Jackson (api) for JSON parsing — exposed so adapter modules don't
//     each have to add Jackson themselves

base {
    archivesName.set("ssrf-guard-llm")
}

dependencies {
    api(project(":ssrf-guard-core"))

    // Jackson for parsing the JSON tool input. `api` so adapter modules pick
    // it up transitively — keeps Spring AI / LangChain4j adapters from
    // having to declare Jackson themselves.
    api("com.fasterxml.jackson.core:jackson-databind")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.assertj:assertj-core")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

mavenPublishing {
    coordinates(
        providers.gradleProperty("GROUP").get(),
        "ssrf-guard-llm",
        providers.gradleProperty("VERSION").get()
    )
    pom {
        name.set("SSRF Guard — LLM Tool Input Validation")
        description.set("Framework-agnostic core for validating URL-shaped arguments in LLM tool inputs (JSON). Used by ssrf-guard-springai, ssrf-guard-langchain4j, and any custom tool dispatcher.")
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
