package site.omagotchi.identityservice.auth.presentation;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import site.omagotchi.identityservice.auth.application.AuthenticationService;
import site.omagotchi.identityservice.auth.application.result.TokenIssueResult;
import site.omagotchi.identityservice.auth.presentation.request.LoginRequest;
import site.omagotchi.identityservice.auth.presentation.response.TokenResponse;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationService authenticationService;
    private final RefreshTokenCookieFactory cookieFactory;
    private final RefreshRequestOriginValidator originValidator;

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return tokenResponse(authenticationService.login(request.email(), request.password()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(
            @RequestHeader(name = HttpHeaders.ORIGIN, required = false) String origin,
            @CookieValue(
                    name = RefreshTokenCookieFactory.COOKIE_NAME,
                    required = false
            ) String refreshToken
    ) {
        originValidator.validate(origin);
        return tokenResponse(authenticationService.refresh(refreshToken));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestHeader(name = HttpHeaders.ORIGIN, required = false) String origin,
            @CookieValue(
                    name = RefreshTokenCookieFactory.COOKIE_NAME,
                    required = false
            ) String refreshToken
    ) {
        originValidator.validate(origin);
        authenticationService.logout(refreshToken);

        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookieFactory.expire().toString())
                .build();
    }

    private ResponseEntity<TokenResponse> tokenResponse(TokenIssueResult result) {
        // Refresh Token은 HttpOnly Cookie, Access Token은 응답 본문으로 전달
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieFactory.issue(result).toString())
                .body(TokenResponse.from(result));
    }
}
