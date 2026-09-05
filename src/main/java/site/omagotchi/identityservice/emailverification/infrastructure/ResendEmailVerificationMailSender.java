package site.omagotchi.identityservice.emailverification.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import site.omagotchi.identityservice.emailverification.application.EmailDeliveryException;
import site.omagotchi.identityservice.emailverification.application.port.EmailVerificationMailSender;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Component
public class ResendEmailVerificationMailSender implements EmailVerificationMailSender {

    private static final Logger log =
            LoggerFactory.getLogger(ResendEmailVerificationMailSender.class);

    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    private static final String VERIFICATION_CODE_TEMPLATE = "verification-code";
    private static final TemplateEngine TEMPLATE_ENGINE = createTemplateEngine();

    // 메일 본문 이미지는 외부에서 접근 가능한 절대 URL이 필요하다.
    // 이 서비스는 anyRequest().authenticated() 경계라 정적 자원을 공개할 수 없으므로
    // 이미지를 첨부로 동봉하고 Content-ID(cid) 로 참조한다.
    private static final String MASCOT_RESOURCE_PATH = "static/img/default.png";
    private static final String MASCOT_CONTENT_ID = "omagotchi-mascot";
    private static final String MASCOT_FILENAME = "omagotchi-mascot.png";
    private static final String MASCOT_CONTENT_TYPE = "image/png";

    private final RestClient restClient;
    private final ResendProperties properties;

    /** 마스코트 이미지의 Base64. 자원을 읽지 못하면 null 이며, 이때는 이미지 없이 발송한다. */
    private final String mascotBase64;

    public ResendEmailVerificationMailSender(
            @Qualifier("resendRestClient") RestClient restClient,
            ResendProperties properties
    ) {
        this.restClient = restClient;
        this.properties = properties;
        this.mascotBase64 = loadMascotBase64();
    }

    @Override
    public void sendVerificationCode(
            UUID challengeId,
            String recipient,
            String code,
            Duration validity
    ) {
        long minutes = validity.toMinutes();

        // Map.of 는 10쌍 제한과 null 불허가 있어 조건부 필드를 담기 어렵다.
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("from", properties.fromEmail());
        request.put("to", List.of(recipient));
        request.put("subject", "[오마고치] 이메일 인증번호");
        // HTML 차단 클라이언트를 위한 대체 본문. 절대 제거하지 않는다.
        request.put("text", "인증번호는 " + code + "입니다. " + minutes + "분 안에 입력해 주세요.");
        request.put("html", renderVerificationCodeHtml(code, minutes));

        if (mascotBase64 != null) {
            request.put("attachments", List.of(Map.of(
                    "content", mascotBase64,
                    "filename", MASCOT_FILENAME,
                    "content_type", MASCOT_CONTENT_TYPE,
                    "content_id", MASCOT_CONTENT_ID
            )));
        }

        try {
            restClient.post()
                    .uri("/emails")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                    .header(IDEMPOTENCY_KEY_HEADER, challengeId.toString())
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException exception) {
            // Resend 응답 해석은 Adapter에서 끝내고 상위에는 중립적인 실패 종류만 전달한다.
            if (exception.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                throw EmailDeliveryException.rateLimited(
                        "Resend 메일 전송 요청이 제한되었습니다.",
                        exception
                );
            }
            throw new EmailDeliveryException("Resend 메일 전송 요청에 실패했습니다.", exception);
        } catch (RestClientException exception) {
            throw new EmailDeliveryException("Resend 메일 전송 요청에 실패했습니다.", exception);
        }
    }

    /**
     * 이미지 자원 부재가 인증 메일 발송 실패로 번지지 않도록 예외를 삼키고 null 을 돌려준다.
     * 인증번호 자체는 텍스트라 이미지 없이도 사용자는 인증을 완료할 수 있다.
     */
    private String loadMascotBase64() {
        ClassPathResource resource = new ClassPathResource(MASCOT_RESOURCE_PATH);
        if (!resource.exists()) {
            log.warn("마스코트 이미지 자원을 찾지 못해 이미지 없이 메일을 발송합니다. path={}",
                    MASCOT_RESOURCE_PATH);
            return null;
        }
        try (InputStream input = resource.getInputStream()) {
            return Base64.getEncoder().encodeToString(input.readAllBytes());
        } catch (IOException exception) {
            log.warn("마스코트 이미지 자원을 읽지 못해 이미지 없이 메일을 발송합니다. path={}",
                    MASCOT_RESOURCE_PATH, exception);
            return null;
        }
    }

    /**
     * 실제 발송에 사용하는 classpath HTML 파일을 Thymeleaf로 렌더링한다.
     * 메일 클라이언트 호환을 위해 템플릿은 table + inline style 로만 구성한다.
     * flex/grid/외부 CSS 는 Outlook·Gmail 에서 무시되므로 사용하지 않는다.
     */
    private String renderVerificationCodeHtml(String code, long minutes) {
        Context context = new Context(Locale.KOREAN);
        // 코드 포인트 단위로 셀을 만들고 th:text 로 이스케이프하여 자리수와 문자에 의존하지 않는다.
        context.setVariable("codeCharacters", code.codePoints()
                .mapToObj(Character::toString)
                .toList());
        context.setVariable("validityMinutes", minutes);
        // 이미지가 없으면 img 태그 자체를 제거하여 깨진 이미지 아이콘을 피한다.
        context.setVariable("mascotAvailable", mascotBase64 != null);
        context.setVariable("mascotContentId", MASCOT_CONTENT_ID);
        return TEMPLATE_ENGINE.process(VERIFICATION_CODE_TEMPLATE, context);
    }

    /** 메일 렌더링에만 쓰므로 MVC View 설정으로 노출하지 않고 Adapter 내부에 둔다. */
    private static TemplateEngine createTemplateEngine() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("emailverification/mail/");
        resolver.setSuffix(".html");
        resolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCacheable(true);

        TemplateEngine templateEngine = new TemplateEngine();
        templateEngine.setTemplateResolver(resolver);
        return templateEngine;
    }
}
