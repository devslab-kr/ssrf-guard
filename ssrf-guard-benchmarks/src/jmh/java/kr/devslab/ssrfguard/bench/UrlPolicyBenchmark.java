package kr.devslab.ssrfguard.bench;

import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import kr.devslab.ssrfguard.core.HostPolicy;
import kr.devslab.ssrfguard.core.NoOpSsrfGuardMetrics;
import kr.devslab.ssrfguard.core.SsrfGuardException;
import kr.devslab.ssrfguard.core.UrlPolicy;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/**
 * Measures the cost of {@code UrlPolicy.validate(URI)} — the hot path every
 * ssrf-guard interceptor invokes once per HTTP request (e.g. the RestClient
 * interceptor calls {@code policy.validate(request.getURI())}). The full set
 * of checks each call covers:
 *
 * <ol>
 *   <li>Scheme allowlist</li>
 *   <li>Port allowlist</li>
 *   <li>IP-literal-host rejection (decimal / hex / octal / IPv6 forms)</li>
 *   <li>Userinfo rejection (the {@code user:pass@} prefix)</li>
 *   <li>Host whitelist lookup</li>
 *   <li>Metrics recording (NoOp here — Micrometer adds its own cost on top)</li>
 * </ol>
 *
 * <p>URI parsing is done at {@code @Setup} (out of the measurement window)
 * because real interceptors get a pre-parsed {@link URI} from the HTTP
 * client. This makes the number directly comparable to "what does the
 * interceptor cost on top of the call I was making anyway".
 *
 * <p>Three URL classes are measured separately because they exercise
 * different code paths:
 *
 * <ul>
 *   <li><b>allowed</b> — host hits the exact-list, all checks pass</li>
 *   <li><b>blockedIpLiteral</b> — `http://169.254.169.254/...` (AWS metadata),
 *       fails the IP-literal-host check after URL parse</li>
 *   <li><b>blockedHost</b> — `https://evil.com/`, fails the whitelist
 *       lookup after all the cheaper checks pass</li>
 * </ul>
 *
 * <p>The "blocked*" benchmarks throw {@link SsrfGuardException} on every
 * invocation — that includes exception construction in the measurement,
 * which is the realistic cost since interceptors always throw on rejection.
 * If your hot path is dominantly the allowed path, only the first number
 * matters.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Benchmark)
public class UrlPolicyBenchmark {

    private UrlPolicy policy;
    private URI allowedUri;
    private URI ipLiteralUri;
    private URI evilHostUri;

    @Setup
    public void setup() {
        HostPolicy hosts = new HostPolicy(List.of("api.partner.com", "httpbin.org"), List.of());
        policy = new UrlPolicy(
                Set.of("http", "https"),
                Set.of(-1, 80, 443),
                hosts,
                /* rejectIpLiteralHosts */ true,
                /* rejectUserInfo */ true,
                NoOpSsrfGuardMetrics.INSTANCE);

        // Pre-parse the URIs — real interceptors receive a parsed URI from
        // the HTTP client, so parsing cost shouldn't be in the measurement.
        allowedUri = URI.create("https://api.partner.com/v1/users/42");
        ipLiteralUri = URI.create("http://169.254.169.254/latest/meta-data/iam/security-credentials/");
        evilHostUri = URI.create("https://evil.com/exfiltrate");
    }

    @Benchmark
    public void allowed(Blackhole bh) {
        // Every check passes — measures the full happy path.
        policy.validate(allowedUri);
        bh.consume(allowedUri);
    }

    @Benchmark
    public void blockedIpLiteral(Blackhole bh) {
        // The AWS-metadata classic. Fails at the IP-literal-host check.
        try {
            policy.validate(ipLiteralUri);
        } catch (SsrfGuardException expected) {
            bh.consume(expected);
        }
    }

    @Benchmark
    public void blockedHost(Blackhole bh) {
        // Passes scheme / port / IP-literal / userinfo, fails the whitelist.
        // This is the most expensive blocked path because every cheaper
        // check runs first.
        try {
            policy.validate(evilHostUri);
        } catch (SsrfGuardException expected) {
            bh.consume(expected);
        }
    }
}
