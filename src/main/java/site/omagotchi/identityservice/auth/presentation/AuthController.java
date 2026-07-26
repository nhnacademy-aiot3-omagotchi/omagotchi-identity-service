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
import site.omagotchi.identityservice.auth.application.LoginUseCase;
import site.omagotchi.identityservice.auth.application.LogoutUseCase;
import site.omagotchi.identityservice.auth.application.RefreshTokenUseCase;
import site.omagotchi.identityservice.auth.application.dto.TokenIssueResult;
import site.omagotchi.identityservice.auth.presentation.dto.LoginRequest;
import site.omagotchi.identityservice.auth.presentation.dto.TokenResponse;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final LoginUseCase loginUseCase;
    private final RefreshTokenUseCase refreshTokenUseCase;
    private final LogoutUseCase logoutUseCase;
    private final RefreshTokenCookieManager cookieManager;
    private final RefreshRequestOriginValidator originValidator;

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return tokenResponse(loginUseCase.execute(request.email(), request.password()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(
            @RequestHeader(name = HttpHeaders.ORIGIN, required = false) String origin,
            @CookieValue(
                    name = RefreshTokenCookieManager.COOKIE_NAME,
                    required = false
            ) String refreshToken
    ) {
        originValidator.validate(origin);
        return tokenResponse(refreshTokenUseCase.execute(refreshToken));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestHeader(name = HttpHeaders.ORIGIN, required = false) String origin,
            @CookieValue(
                    name = RefreshTokenCookieManager.COOKIE_NAME,
                    required = false
            ) String refreshToken
    ) {
        originValidator.validate(origin);
        logoutUseCase.execute(refreshToken);

        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookieManager.expire().toString())
                .build();
    }

    private ResponseEntity<TokenResponse> tokenResponse(TokenIssueResult result) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieManager.issue(result).toString())
                .body(TokenResponse.from(result));
    }
}
