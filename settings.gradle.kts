pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        // Spring milestone repo — Spring AI 1.0 stable releases live in
        // mavenCentral, but 1.0.0-M / 1.1.0-M milestones land here first.
        // Pinned in resolution scope so the springai module can compile
        // against the latest pre-release without leaking to consumers.
        maven { url = uri("https://repo.spring.io/milestone") }
    }
}

rootProject.name = "ssrf-guard"

// Module layout
//   ssrf-guard-core         — policy / metrics / IP classification (no Spring)
//   ssrf-guard-httpclient5  — SafeDnsResolver + SafeRedirectStrategy
//   ssrf-guard-restclient   — Spring RestClient interceptor + auto-config
//   ssrf-guard-resttemplate — Spring RestTemplate interceptor + auto-config
//   ssrf-guard-webclient    — Spring WebFlux ExchangeFilterFunction + auto-config
//   ssrf-guard-feign        — Spring Cloud OpenFeign interceptor + auto-config
//   ssrf-guard-llm          — framework-agnostic JSON tool input validation (v3.1+)
//   ssrf-guard-springai     — Spring AI tool URL validation (thin adapter over -llm)
//   ssrf-guard-langchain4j  — LangChain4j tool URL validation (thin adapter over -llm, v3.1+)
//   ssrf-guard-jdkhttp      — java.net.http.HttpClient wrapper (no Spring)
//   ssrf-guard-okhttp       — OkHttp interceptor + DNS
//   ssrf-guard              — v2.0.0-compatible meta artifact (restclient + httpclient5)
//   ssrf-guard-benchmarks   — JMH benchmarks (not published; see BENCHMARKS.md)
include(
    "ssrf-guard-core",
    "ssrf-guard-httpclient5",
    "ssrf-guard-restclient",
    "ssrf-guard-resttemplate",
    "ssrf-guard-webclient",
    "ssrf-guard-feign",
    "ssrf-guard-llm",
    "ssrf-guard-springai",
    "ssrf-guard-langchain4j",
    "ssrf-guard-jdkhttp",
    "ssrf-guard-okhttp",
    "ssrf-guard",
    "ssrf-guard-benchmarks",
)
