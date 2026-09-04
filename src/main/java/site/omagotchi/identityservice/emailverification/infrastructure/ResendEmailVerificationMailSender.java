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
import site.omagotchi.identityservice.emailverification.application.EmailDeliveryException;
import site.omagotchi.identityservice.emailverification.application.port.EmailVerificationMailSender;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class ResendEmailVerificationMailSender implements EmailVerificationMailSender {

    private static final Logger log =
            LoggerFactory.getLogger(ResendEmailVerificationMailSender.class);

    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    // 메일 본문 이미지는 외부에서 접근 가능한 절대 URL이 필요하다.
    // 이 서비스는 anyRequest().authenticated() 경계라 정적 자원을 공개할 수 없으므로
    // 이미지를 첨부로 동봉하고 Content-ID(cid) 로 참조한다.
    private static final String MASCOT_RESOURCE_PATH = "static/img/default.png";
    private static final String MASCOT_CONTENT_ID = "omagotchi-mascot";
    private static final String MASCOT_FILENAME = "omagotchi-mascot.png";
    private static final String MASCOT_CONTENT_TYPE = "image/png";

    private static final String CODE_CELL_TEMPLATE = """
            <td align="center" valign="middle" style="width:46px;height:66px;background:#eeeef0;\
            border-radius:10px;font-size:26px;font-weight:700;color:#111111;\
            text-align:center;">%s</td>""";

    private static final String CODE_SPACER_CELL =
            "<td style=\"width:10px;font-size:0;line-height:0;\">&nbsp;</td>";

    private static final String MASCOT_IMAGE_TAG = """
            <img src="cid:%s" width="120" height="120" alt="오마고치" \
            style="display:block;border:0;outline:none;text-decoration:none;\
            -ms-interpolation-mode:nearest-neighbor;image-rendering:pixelated;">""";

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
     * 메일 클라이언트 호환을 위해 table + inline style 로만 구성한다.
     * flex/grid/외부 CSS 는 Outlook·Gmail 에서 무시되므로 사용하지 않는다.
     */
    private String renderVerificationCodeHtml(String code, long minutes) {
        return """
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0" \
                style="background:#ffffff;margin:0;padding:0;">
                  <tr><td align="center" style="padding:32px 16px;">
                    <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0" \
                style="max-width:420px;background:#fafafa;border:1px solid #e6e6e6;border-radius:24px;">
                      <tr><td align="center" style="padding:44px 24px 40px 24px;">
                        %s
                        <div style="height:32px;line-height:32px;font-size:0;">&nbsp;</div>
                        <div style="font-size:26px;line-height:1.3;font-weight:700;color:#111111;\
                letter-spacing:-0.5px;">인증 코드</div>
                        <div style="height:34px;line-height:34px;font-size:0;">&nbsp;</div>
                        <table role="presentation" cellpadding="0" cellspacing="0" border="0" align="center">
                          <tr>%s</tr>
                        </table>
                        <div style="height:36px;line-height:36px;font-size:0;">&nbsp;</div>
                        <div style="font-size:20px;line-height:1.4;font-weight:700;color:#111111;\
                letter-spacing:-0.3px;">%d분 안에 입력하세요</div>
                      </td></tr>
                    </table>
                    <div style="max-width:420px;padding:20px 8px 0 8px;font-size:12px;\
                line-height:1.6;color:#9a9a9a;text-align:center;">
                      본인이 요청하지 않았다면 이 메일을 무시해 주세요.
                    </div>
                  </td></tr>
                </table>
                """.formatted(
                renderMascotImageTag(),
                renderCodeCells(code),
                minutes
        );
    }

    /** 이미지 자원이 없으면 img 태그 자체를 넣지 않아 깨진 이미지 아이콘을 피한다. */
    private String renderMascotImageTag() {
        if (mascotBase64 == null) {
            return "";
        }
        return MASCOT_IMAGE_TAG.formatted(MASCOT_CONTENT_ID);
    }

    /**
     * 코드 한 글자당 회색 박스 한 칸을 만든다.
     * 자리수가 6이 아니어도 레이아웃이 깨지지 않도록 길이에 의존하지 않는다.
     */
    private String renderCodeCells(String code) {
        StringBuilder cells = new StringBuilder();
        for (int index = 0; index < code.length(); index++) {
            if (index > 0) {
                cells.append(CODE_SPACER_CELL);
            }
            cells.append(CODE_CELL_TEMPLATE.formatted(escapeHtml(code.charAt(index))));
        }
        return cells.toString();
    }

    /**
     * 현재 코드는 숫자만으로 생성되지만, 생성 규칙이 바뀌더라도
     * 마크업이 주입되지 않도록 방어적으로 이스케이프한다.
     */
    private String escapeHtml(char character) {
        return switch (character) {
            case '&' -> "&amp;";
            case '<' -> "&lt;";
            case '>' -> "&gt;";
            case '"' -> "&quot;";
            case '\'' -> "&#39;";
            default -> String.valueOf(character);
        };
    }
}
