package devs.lab.ssrf.config;

import devs.lab.ssrf.security.SafeDnsResolver;
import devs.lab.ssrf.security.SafeRedirectStrategy;
import devs.lab.ssrf.security.SsrfGuardInterceptor;
import devs.lab.ssrf.security.SsrfGuardProperties;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@AutoConfiguration
@ConditionalOnClass(RestClient.class)
@EnableConfigurationProperties(SsrfGuardProperties.class)
@ConditionalOnProperty(prefix = "ssrf.guard", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SsrfGuardAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    SafeDnsResolver ssrfSafeDnsResolver(SsrfGuardProperties props) {
        return new SafeDnsResolver(props.getExactHosts(), props.getSuffixes(), props.isBlockPrivateNetworks());
    }

    @Bean
    @ConditionalOnMissingBean
    CloseableHttpClient ssrfHttpClient(SafeDnsResolver dns, SsrfGuardProperties props) {
        var builder = HttpClients.custom()
                .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                        .setDnsResolver(dns)
                        .build())
                .disableAutomaticRetries();

        if (props.isFollowRedirects()) {
            builder.setRedirectStrategy(new SafeRedirectStrategy(dns, props.getAllowedSchemes()));
        } else {
            builder.disableRedirectHandling();
        }

        return builder.build();
    }

    @Bean
    @ConditionalOnMissingBean(name = "ssrfHttpRequestFactory")
    HttpComponentsClientHttpRequestFactory ssrfHttpRequestFactory(CloseableHttpClient httpClient,
                                                                  SsrfGuardProperties props) {
        var f = new HttpComponentsClientHttpRequestFactory(httpClient);
        f.setConnectTimeout((int) props.getConnectTimeout().toMillis());
        f.setReadTimeout((int) props.getReadTimeout().toMillis());
        return f;
    }

    @Bean
    @ConditionalOnMissingBean
    SsrfGuardInterceptor ssrfGuardInterceptor(SsrfGuardProperties props) {
        return new SsrfGuardInterceptor(
                props.getExactHosts(),
                props.getSuffixes(),
                props.getAllowedSchemes(),
                props.getAllowedPorts()
        );
    }

    /**
     * Boot의 RestClientAutoConfiguration 이 제공하는 RestClient.Builder 를
     * 전역 커스터마이즈 → 모든 RestClient 에 동일 보안 정책 적용
     */
    @Bean
    @ConditionalOnMissingBean(name = "ssrfRestClientCustomizer")
    RestClientCustomizer ssrfRestClientCustomizer(
            HttpComponentsClientHttpRequestFactory factory,
            SsrfGuardInterceptor interceptor
    ) {
        return builder -> builder
                .requestFactory(factory)
                .requestInterceptor(interceptor);
    }
}