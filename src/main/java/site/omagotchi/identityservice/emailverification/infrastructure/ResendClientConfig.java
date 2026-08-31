package site.omagotchi.identityservice.emailverification.infrastructure;

import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import site.omagotchi.identityservice.emailverification.application.EmailVerificationProperties;

import java.net.http.HttpClient;

@Configuration(proxyBeanMethods = false)
public class ResendClientConfig {

    @Bean
    RestClient resendRestClient(
            RestClient.Builder builder,
            ResendProperties resendProperties,
            EmailVerificationProperties verificationProperties
    ) {
        validateTimeoutPolicy(resendProperties, verificationProperties);

        return builder
                .baseUrl("https://api.resend.com")
                .requestFactory(createRequestFactory(resendProperties))
                .build();
    }

    static JdkClientHttpRequestFactory createRequestFactory(ResendProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(httpClient);
        // JDK 기반 Factory의 read timeout은 응답 전체 완료까지의 상한으로 동작한다.
        requestFactory.setReadTimeout(properties.readTimeout());
        return requestFactory;
    }

    static void validateTimeoutPolicy(
            ResendProperties resendProperties,
            EmailVerificationProperties verificationProperties
    ) {
        if (resendProperties.connectTimeout().compareTo(resendProperties.readTimeout()) > 0) {
            throw new IllegalStateException(
                    "email.resend.connect-timeout은 read-timeout보다 길 수 없습니다."
            );
        }
        if (resendProperties.readTimeout().compareTo(verificationProperties.codeTtl()) >= 0) {
            throw new IllegalStateException(
                    "email.resend.read-timeout은 auth.email-verification.code-ttl보다 짧아야 합니다."
            );
        }
    }
}
