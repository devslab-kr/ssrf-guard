// ssrf-guard-langchain4j — thin adapter wiring LangChain4j's ToolExecutor
// to ssrf-guard-llm. Same security model as ssrf-guard-springai; different
// framework abstraction.
//
// Closes the same SSRF surface (LLM agent fetch_url-style tools) for the
// LangChain4j community — the other major Java LLM framework.

base {
    archivesName.set("ssrf-guard-langchain4j")
}

dependencies {
    // The framework-agnostic core. Jackson follows transitively.
    api(project(":ssrf-guard-llm"))
    api("org.springframework.boot:spring-boot-autoconfigure")

    // LangChain4j 1.15.0 — first stable line. ToolExecutor lives in the
    // main `langchain4j` artifact (the AiServices implementation),
    // ToolExecutionRequest in `langchain4j-core` follows transitively.
    compileOnly("dev.langchain4j:langchain4j:1.15.0")

    // Micrometer — compileOnly. The MetricsConfiguration inner class is
    // gated by @ConditionalOnClass(MeterRegistry.class), so this dep is
    // only ever needed at compile time (the class reference inside the
    // gated inner config) — never required at runtime by consumers.
    compileOnly("io.micrometer:micrometer-core")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("dev.langchain4j:langchain4j:1.15.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

mavenPublishing {
    coordinates(
        providers.gradleProperty("GROUP").get(),
        "ssrf-guard-langchain4j",
        providers.gradleProperty("VERSION").get()
    )
    pom {
        name.set("SSRF Guard — LangChain4j")
        description.set("SSRF Guard adapter for LangChain4j tool execution. Validates URL-shaped arguments in ToolExecutionRequest before the underlying ToolExecutor runs. Thin wrapper over ssrf-guard-llm.")
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
