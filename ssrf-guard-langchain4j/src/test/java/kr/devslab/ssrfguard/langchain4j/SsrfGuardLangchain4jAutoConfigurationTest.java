package kr.devslab.ssrfguard.langchain4j;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.service.tool.ToolExecutor;
import kr.devslab.ssrfguard.core.UrlPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots a Spring context with the langchain4j autoconfig active and asserts:
 *
 * <ol>
 *   <li>The autoconfig publishes the expected beans ({@code UrlPolicy},
 *       {@code SsrfGuardMetrics}, and the BeanPostProcessor).</li>
 *   <li>The BeanPostProcessor actually wraps consumer {@code @Bean ToolExecutor}
 *       declarations — every executor in the context becomes a
 *       {@code SsrfGuardedToolExecutor} without consumer code.</li>
 *   <li>A request through the wrapped executor blocks attacker URLs
 *       end-to-end. This is the "secure by default" claim we make in
 *       the README — the test holds us to it.</li>
 *   <li>{@code wrap-tool-executors=false} disables the BeanPostProcessor
 *       (consumers who want to wrap manually keep the off switch).</li>
 * </ol>
 */
class SsrfGuardLangchain4jAutoConfigurationTest {

    @SpringBootApplication
    static class TestApp {

        /** A consumer-side tool that records every invocation. */
        @Bean
        ToolExecutor fakeFetchUrlTool() {
            return (request, memoryId) -> "PRETEND-FETCHED " + request.arguments();
        }
    }

    @Nested
    @SpringBootTest(classes = TestApp.class)
    @TestPropertySource(properties = {
            "ssrf.guard.enabled=true",
            "ssrf.guard.exact-hosts=api.example.com"
            // wrap-tool-executors defaults to true
    })
    class WhenAutoWrapEnabled {

        @Autowired ApplicationContext ctx;
        @Autowired UrlPolicy policy;
        @Autowired ToolExecutor injectedExecutor;

        @Test
        void context_publishes_url_policy_and_postprocessor() {
            assertThat(policy).isNotNull();
            // The consumer's ToolExecutor came back wrapped.
            assertThat(injectedExecutor)
                    .as("autoconfig BeanPostProcessor should wrap @Bean ToolExecutor")
                    .isInstanceOf(SsrfGuardedToolExecutor.class);
        }

        @Test
        void wrapped_executor_blocks_aws_metadata_url() {
            String result = injectedExecutor.execute(
                    ToolExecutionRequest.builder()
                            .name("fetch_url")
                            .arguments("{\"url\":\"http://169.254.169.254/latest/meta-data/\"}")
                            .build(),
                    null);
            assertThat(result)
                    .as("LLM-facing output on a blocked URL")
                    .contains("\"error\":\"ssrf_blocked\"")
                    .contains("\"reason\":\"blocked_ip_literal\"");
        }

        @Test
        void wrapped_executor_allows_whitelisted_url() {
            String result = injectedExecutor.execute(
                    ToolExecutionRequest.builder()
                            .name("fetch_url")
                            .arguments("{\"url\":\"https://api.example.com/v1\"}")
                            .build(),
                    null);
            // The fake delegate echoes "PRETEND-FETCHED ..." on success —
            // if we see that string the wrap let the call through.
            assertThat(result).startsWith("PRETEND-FETCHED");
        }
    }

    @Nested
    @SpringBootTest(classes = TestApp.class)
    @TestPropertySource(properties = {
            "ssrf.guard.enabled=true",
            "ssrf.guard.exact-hosts=api.example.com",
            "ssrf.guard.langchain4j.wrap-tool-executors=false"
    })
    class WhenAutoWrapDisabled {

        @Autowired ToolExecutor injectedExecutor;

        @Test
        void executor_is_NOT_wrapped() {
            // The off switch should leave the consumer's bean untouched —
            // they presumably plan to wrap manually via
            // SsrfGuardedToolExecutors.wrap(...).
            assertThat(injectedExecutor).isNotInstanceOf(SsrfGuardedToolExecutor.class);
        }
    }
}
