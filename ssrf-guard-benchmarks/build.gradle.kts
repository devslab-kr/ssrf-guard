// ssrf-guard-benchmarks
//
// JMH micro-benchmarks for the hot paths consumers actually pay for:
//   - UrlPolicy.check(url)     — every interceptor calls this
//   - JsonToolInputGuard       — every LLM tool invocation walks this
//
// NOT published to Maven Central. Filtered out of the root build's
// subprojects { } block — see build.gradle.kts comment there.
//
// Run:
//   ./gradlew :ssrf-guard-benchmarks:jmh
//
// Output lands at:
//   ssrf-guard-benchmarks/build/results/jmh/results.txt
//
// Tune iterations / forks in the `jmh { ... }` block below. The defaults
// here (5×1s warmup, 5×1s measurement, 1 fork) are tuned for "quick enough
// to run locally during development" — for canonical numbers in
// BENCHMARKS.md bump warmup/measurement and use 3 forks.

plugins {
    java
    id("me.champeau.jmh") version "0.7.2"
    // The :ssrf-guard-core / :ssrf-guard-llm modules express their Spring /
    // SLF4J / Jackson deps via the spring-boot-dependencies BOM (managed by
    // io.spring.dependency-management in the root build's subprojects{}).
    // We're filtered out of that block intentionally — re-apply the plugin
    // here so the BOM resolves on the jmh classpath.
    id("io.spring.dependency-management") version "1.1.6"
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:3.5.6")
    }
}

dependencies {
    // The modules under measurement.
    jmh(project(":ssrf-guard-core"))
    jmh(project(":ssrf-guard-llm"))

    // Jackson for the JSON benchmark — already a transitive of -llm but
    // pinning it explicitly here so the benchmark classpath is self-evident.
    jmh("com.fasterxml.jackson.core:jackson-databind:2.18.1")
}

jmh {
    warmupIterations.set(5)
    iterations.set(5)
    fork.set(1)
    timeOnIteration.set("1s")
    warmup.set("1s")
    // Use ns/op as the canonical unit for these hot-path measurements.
    timeUnit.set("ns")
    // Restrict to our own benchmark classes (otherwise JMH may pick up
    // transitive benchmarks shipped by libraries).
    includes.set(listOf("kr\\.devslab\\.ssrfguard\\.bench\\..*"))
}

// JMH's annotation processor generates classes that fail -Xlint:all -Werror
// with "unused" / "serial" warnings. Drop the strictness for the jmh
// compilation only — runtime code lives in the modules under measurement,
// which still get the strict settings.
tasks.named<JavaCompile>("compileJmhJava").configure {
    options.compilerArgs.clear()
    options.compilerArgs.addAll(listOf("-parameters"))
}
