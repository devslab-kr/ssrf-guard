// ssrf-guard-springai — Spring AI Tool URL validation.
//
// The hot SSRF surface in 2025+ is LLM agents that take a URL as a tool
// parameter and fetch it. ChatGPT, Perplexity, every RAG pipeline ever —
// they all have a "fetch_url" / "search_web" tool that's a one-line SSRF
// vector if not gated.
//
// This module wraps Spring AI ToolCallback instances so URL-shaped arguments
// are validated against the same UrlPolicy as the regular HTTP clients
// before the tool actually executes.

base {
    archivesName.set("ssrf-guard-springai")
}

dependencies {
    api(project(":ssrf-guard-core"))
    api("org.springframework.boot:spring-boot-autoconfigure")

    // Spring AI Tool API — 1.0 stable. The starter pulls in spring-ai-core
    // transitively, where @Tool / ToolCallback live.
    // Spring AI 1.0 GA artifacts. The original spring-ai-core was renamed
    // — model abstractions (ToolCallback, ToolDefinition, @Tool) now live
    // in spring-ai-model, and ChatClient/ChatModel are in spring-ai-client-chat.
    compileOnly("org.springframework.ai:spring-ai-model:1.0.7")

    // Jackson for parsing the JSON tool input.
    compileOnly("com.fasterxml.jackson.core:jackson-databind")

    compileOnly("io.micrometer:micrometer-core")
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.ai:spring-ai-model:1.0.7")
    testImplementation("org.springframework.ai:spring-ai-client-chat:1.0.7")
    testImplementation("com.fasterxml.jackson.core:jackson-databind")
    testImplementation("io.micrometer:micrometer-core")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

mavenPublishing {
    coordinates(
        providers.gradleProperty("GROUP").get(),
        "ssrf-guard-springai",
        providers.gradleProperty("VERSION").get()
    )
    pom {
        name.set("SSRF Guard — Spring AI Tool URL Validation")
        description.set("Wraps Spring AI ToolCallback instances so URL-shaped arguments are validated against an SSRF policy before LLM-driven tool execution. Closes the SSRF surface that LLM agents (RAG pipelines, fetch_url tools) introduce.")
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
