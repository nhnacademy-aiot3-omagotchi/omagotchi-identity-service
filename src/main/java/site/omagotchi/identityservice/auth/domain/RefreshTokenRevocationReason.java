package site.omagotchi.identityservice.auth.domain;

public enum RefreshTokenRevocationReason {

    // 로그아웃
    LOGOUT,

    // 재사용 감지
    REUSE_DETECTED,

    // 계정 비활성화됨
    ACCOUNT_DISABLED,

    // 계정 탈퇴됨
    ACCOUNT_WITHDRAWN
}
