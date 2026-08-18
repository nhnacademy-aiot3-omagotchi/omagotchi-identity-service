package site.omagotchi.identityservice.auth.presentation;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import site.omagotchi.identityservice.auth.application.AuthenticationService;
import site.omagotchi.identityservice.auth.application.result.TokenIssueResult;
import site.omagotchi.identityservice.auth.presentation.request.LoginRequest;
import site.omagotchi.identityservice.auth.presentation.request.RefreshTokenRequest;
import site.omagotchi.identityservice.auth.presentation.response.TokenResponse;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationService authenticationService;

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return tokenResponse(authenticationService.login(request.email(), request.password()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@RequestBody RefreshTokenRequest request) {
        // null·blank·미등록 Token을 같은 인증 실패로 다루는 Application 정책
        return tokenResponse(authenticationService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody RefreshTokenRequest request) {
        // Token 부재·미등록 상태도 성공으로 다루는 멱등 로그아웃 정책
        authenticationService.logout(request.refreshToken());
        // Token 수명주기 응답의 중간 저장 방지
        return ResponseEntity.noContent()
                .cacheControl(CacheControl.noStore())
                .build();
    }

    private ResponseEntity<TokenResponse> tokenResponse(TokenIssueResult result) {
        // Access·Refresh Token을 Frontend 프로세스에만 반환하는 no-store 응답
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(TokenResponse.from(result));
    }
}
