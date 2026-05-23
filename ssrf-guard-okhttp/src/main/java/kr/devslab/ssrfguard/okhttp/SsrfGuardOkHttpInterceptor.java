package kr.devslab.ssrfguard.okhttp;

import kr.devslab.ssrfguard.core.UrlPolicy;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * OkHttp {@link Interceptor} that runs the {@link UrlPolicy} on every
 * outbound request URL <i>before</i> dispatch. Pair with
 * {@link SsrfGuardOkHttpDns} for the second-layer DNS-time private-IP
 * check.
 *
 * <pre>{@code
 * OkHttpClient client = new OkHttpClient.Builder()
 *     .addInterceptor(new SsrfGuardOkHttpInterceptor(urlPolicy))
 *     .dns(new SsrfGuardOkHttpDns(hostPolicy, true))
 *     .build();
 * }</pre>
 */
public final class SsrfGuardOkHttpInterceptor implements Interceptor {

    private final UrlPolicy policy;

    public SsrfGuardOkHttpInterceptor(UrlPolicy policy) {
        this.policy = policy;
    }

    @NotNull
    @Override
    public Response intercept(@NotNull Chain chain) throws IOException {
        Request request = chain.request();
        policy.validate(request.url().uri());
        return chain.proceed(request);
    }

    public UrlPolicy policy() {
        return policy;
    }
}
