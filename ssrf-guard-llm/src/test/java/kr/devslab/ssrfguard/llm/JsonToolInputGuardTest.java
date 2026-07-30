package kr.devslab.ssrfguard.llm;

import kr.devslab.ssrfguard.core.BlockReason;
import kr.devslab.ssrfguard.core.HostPolicy;
import kr.devslab.ssrfguard.core.NoOpSsrfGuardMetrics;
import kr.devslab.ssrfguard.core.SsrfGuardException;
import kr.devslab.ssrfguard.core.UrlPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Tests the framework-agnostic core of LLM tool input validation. These
 * tests intentionally do not touch any LLM framework — that's the whole
 * point of extracting this module.
 */
class JsonToolInputGuardTest {

    private static UrlPolicy policy(List<String> exact) {
        return new UrlPolicy(
                Set.of("http", "https"),
                Set.of(-1, 80, 443),
                new HostPolicy(exact, List.of()),
                true,
                true,
                NoOpSsrfGuardMetrics.INSTANCE
        );
    }

    @Test
    void returns_null_when_input_is_blank_or_not_json() {
        var guard = new JsonToolInputGuard(policy(List.of("api.example.com")));
        assertThat(guard.checkOrFormatError(null)).isNull();
        assertThat(guard.checkOrFormatError("")).isNull();
        assertThat(guard.checkOrFormatError("   ")).isNull();
        assertThat(guard.checkOrFormatError("not json at all")).isNull();
    }

    @Test
    void allows_input_without_urls() {
        var guard = new JsonToolInputGuard(policy(List.of("api.example.com")));
        assertThat(guard.checkOrFormatError("{\"query\":\"weather today\",\"limit\":5}")).isNull();
    }

    @Test
    void allows_whitelisted_url_in_top_level_field() {
        var guard = new JsonToolInputGuard(policy(List.of("api.example.com")));
        assertThat(guard.checkOrFormatError("{\"url\":\"https://api.example.com/v1\"}")).isNull();
    }

    @Test
    void blocks_aws_metadata_url_returns_structured_error() {
        var guard = new JsonToolInputGuard(policy(List.of("api.example.com")));
        String err = guard.checkOrFormatError("{\"url\":\"http://169.254.169.254/latest/meta-data/\"}");
        assertThat(err)
                .isNotNull()
                .contains("\"error\":\"ssrf_blocked\"")
                .contains("\"reason\":\"blocked_ip_literal\"")
                .contains("\"url\":\"http://169.254.169.254/latest/meta-data/\"")
                .contains("\"guidance\":");
    }

    @Test
    void blocks_nested_url_field() {
        var guard = new JsonToolInputGuard(policy(List.of("api.example.com")));
        String err = guard.checkOrFormatError("{\"request\":{\"target\":\"https://evil.com/\"},\"timeout\":5}");
        assertThat(err).contains("\"error\":\"ssrf_blocked\"");
    }

    @Test
    void blocks_url_inside_array() {
        var guard = new JsonToolInputGuard(policy(List.of("api.example.com")));
        String err = guard.checkOrFormatError("{\"urls\":[\"https://api.example.com/ok\", \"https://evil.com/bad\"]}");
        assertThat(err).contains("\"error\":\"ssrf_blocked\"");
    }

    @Test
    void blocks_obfuscated_ip_literal() {
        var guard = new JsonToolInputGuard(policy(List.of("api.example.com")));
        String err = guard.checkOrFormatError("{\"url\":\"http://2130706433/\"}");
        assertThat(err).contains("\"reason\":\"blocked_ip_literal\"");
    }

    @Test
    void throws_when_throw_on_violation_is_true() {
        var guard = new JsonToolInputGuard(policy(List.of("api.example.com")), true);
        assertThatExceptionOfType(SsrfGuardException.class)
                .isThrownBy(() -> guard.checkOrFormatError("{\"url\":\"http://10.0.0.5/\"}"))
                .matches(e -> e.reason() == BlockReason.BLOCKED_IP_LITERAL);
    }

    @Test
    void blocks_uppercase_and_mixed_case_scheme_urls() {
        // URI schemes are case-insensitive; an uppercase scheme must not let a
        // URL skip collection and bypass the policy.
        var guard = new JsonToolInputGuard(policy(List.of("api.example.com")));

        String upper = guard.checkOrFormatError("{\"url\":\"HTTP://169.254.169.254/latest/meta-data/\"}");
        assertThat(upper)
                .isNotNull()
                .contains("\"error\":\"ssrf_blocked\"")
                .contains("\"reason\":\"blocked_ip_literal\"");

        String mixed = guard.checkOrFormatError("{\"request\":{\"target\":\"HtTpS://evil.com/\"}}");
        assertThat(mixed).contains("\"error\":\"ssrf_blocked\"");
    }

    @Test
    void still_allows_whitelisted_url_with_uppercase_scheme() {
        var guard = new JsonToolInputGuard(policy(List.of("api.example.com")));
        assertThat(guard.checkOrFormatError("{\"url\":\"HTTPS://api.example.com/v1\"}")).isNull();
    }

    @Test
    void ignores_non_http_schemes() {
        // mailto:, urn:uuid:, file:// — should not trip the URL detector
        // because looksLikeUrl() only matches http(s)://
        var guard = new JsonToolInputGuard(policy(List.of("api.example.com")));
        assertThat(guard.checkOrFormatError("{\"to\":\"mailto:user@example.com\"}")).isNull();
        assertThat(guard.checkOrFormatError("{\"id\":\"urn:uuid:abc\"}")).isNull();
    }

    @Test
    void validates_whole_string_url_despite_surrounding_whitespace() {
        // " http://10.0.0.5/ " — looksLikeUrl() tolerates the whitespace but
        // java.net.URI does not; the candidate must be trimmed before parsing
        // or the URL skips validation entirely.
        var guard = new JsonToolInputGuard(policy(List.of("api.example.com")));
        String err = guard.checkOrFormatError("{\"url\":\" http://10.0.0.5/ \"}");
        assertThat(err).contains("\"error\":\"ssrf_blocked\"");
    }

    @Test
    void validates_url_whose_path_has_uri_illegal_characters() {
        // '[' is fine for browsers/HTTP clients but java.net.URI throws on
        // it. The parse retry drops the path and validates scheme://authority,
        // so illegal path characters can't smuggle a URL past the policy.
        var guard = new JsonToolInputGuard(policy(List.of("api.example.com")));
        String err = guard.checkOrFormatError("{\"url\":\"http://10.0.0.5/a[0]\"}");
        assertThat(err).contains("\"error\":\"ssrf_blocked\"");

        assertThat(guard.checkOrFormatError("{\"url\":\"https://api.example.com/a[0]\"}")).isNull();
    }

    // --- scanEmbedded ------------------------------------------------------

    @Test
    void default_guard_does_not_scan_embedded_urls() {
        // Documents the opt-in: without scanEmbedded, mid-sentence URLs pass.
        var guard = new JsonToolInputGuard(policy(List.of("api.example.com")));
        assertThat(guard.checkOrFormatError(
                "{\"prompt\":\"summarize http://169.254.169.254/latest/meta-data/ please\"}")).isNull();
    }

    @Test
    void scan_embedded_blocks_mid_sentence_url() {
        var guard = new JsonToolInputGuard(policy(List.of("api.example.com")), false, true);
        String err = guard.checkOrFormatError(
                "{\"prompt\":\"summarize http://169.254.169.254/latest/meta-data/ please\"}");
        assertThat(err)
                .isNotNull()
                .contains("\"error\":\"ssrf_blocked\"")
                .contains("\"reason\":\"blocked_ip_literal\"");
    }

    @Test
    void scan_embedded_still_flags_whole_string_urls() {
        // Strictly additive over the base scanner.
        var guard = new JsonToolInputGuard(policy(List.of("api.example.com")), false, true);
        assertThat(guard.checkOrFormatError("{\"url\":\"https://evil.com/\"}"))
                .contains("\"error\":\"ssrf_blocked\"");
        assertThat(guard.checkOrFormatError("{\"url\":\"https://api.example.com/v1\"}")).isNull();
    }

    @Test
    void scan_embedded_allows_allowlisted_mid_sentence_url() {
        var guard = new JsonToolInputGuard(policy(List.of("api.example.com")), false, true);
        assertThat(guard.checkOrFormatError(
                "{\"prompt\":\"compare https://api.example.com/a and https://api.example.com/b\"}")).isNull();
    }

    @Test
    void scan_embedded_trims_trailing_prose_punctuation() {
        // "…api.example.com." — the sentence's full stop is not part of the
        // host; without trimming, the trailing dot would fail the host
        // allowlist and false-positive.
        var guard = new JsonToolInputGuard(policy(List.of("api.example.com")), false, true);
        assertThat(guard.checkOrFormatError(
                "{\"prompt\":\"read https://api.example.com.\"}")).isNull();

        String err = guard.checkOrFormatError("{\"prompt\":\"read https://evil.com/x.\"}");
        assertThat(err).contains("\"url\":\"https://evil.com/x\"");
    }

    @Test
    void scan_embedded_keeps_balanced_parentheses_trims_unbalanced() {
        var guard = new JsonToolInputGuard(policy(List.of("api.example.com")), false, true);
        // Wiki-style balanced parens survive intact.
        assertThat(guard.checkOrFormatError(
                "{\"prompt\":\"see https://api.example.com/wiki/Foo_(bar) for details\"}")).isNull();
        // A closing paren from the surrounding sentence is trimmed.
        String err = guard.checkOrFormatError("{\"prompt\":\"(see https://evil.com/x)\"}");
        assertThat(err).contains("\"url\":\"https://evil.com/x\"");
    }

    @Test
    void scan_embedded_catches_url_glued_to_preceding_text() {
        var guard = new JsonToolInputGuard(policy(List.of("api.example.com")), false, true);
        String err = guard.checkOrFormatError("{\"prompt\":\"visit seehttp://evil.com now\"}");
        assertThat(err).contains("\"error\":\"ssrf_blocked\"");
    }

    @Test
    void scan_embedded_stops_at_quotes_and_angle_brackets() {
        var guard = new JsonToolInputGuard(policy(List.of("api.example.com")), false, true);
        String err = guard.checkOrFormatError("{\"note\":\"link: <https://evil.com/x> in markup\"}");
        assertThat(err).contains("\"url\":\"https://evil.com/x\"");
    }

    @Test
    void scan_embedded_catches_uppercase_scheme_mid_sentence() {
        var guard = new JsonToolInputGuard(policy(List.of("api.example.com")), false, true);
        String err = guard.checkOrFormatError(
                "{\"prompt\":\"fetch HTTP://169.254.169.254/latest/ now\"}");
        assertThat(err).contains("\"reason\":\"blocked_ip_literal\"");
    }

    @Test
    void scan_embedded_accessor_reflects_flag() {
        assertThat(new JsonToolInputGuard(policy(List.of())).scanEmbedded()).isFalse();
        assertThat(new JsonToolInputGuard(policy(List.of()), false, true).scanEmbedded()).isTrue();
    }
}
