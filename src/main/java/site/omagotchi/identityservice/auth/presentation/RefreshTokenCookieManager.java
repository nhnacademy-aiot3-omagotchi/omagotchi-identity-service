package site.omagotchi.identityservice.auth.presentation;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import site.omagotchi.identityservice.auth.application.dto.TokenIssueResult;
import site.omagotchi.identityservice.auth.infrastructure.RefreshTokenProperties;

import java.time.Clock;
import java.time.Duration;

@Component
@RequiredArgsConstructor
public class RefreshTokenCookieManager {

    public static final String COOKIE_NAME = "OMAGOTCHI_REFRESH";
    private static final String COOKIE_PATH = "/api/v1/auth";
    private static final String SAME_SITE = "Strict";

    private final RefreshTokenProperties properties;
    private final Clock clock;

    public ResponseCookie issue(TokenIssueResult result) {
        Duration remaining = Duration.between(clock.instant(), result.refreshTokenExpiresAt());
        if (remaining.isNegative()) {
            remaining = Duration.ZERO;
        }

        return cookie(result.refreshToken())
                .maxAge(remaining)
                .build();
    }

    public ResponseCookie expire() {
        return cookie("")
                .maxAge(Duration.ZERO)
                .build();
    }

    private ResponseCookie.ResponseCookieBuilder cookie(String value) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(properties.secure())
                .sameSite(SAME_SITE)
                .path(COOKIE_PATH);
    }
}

