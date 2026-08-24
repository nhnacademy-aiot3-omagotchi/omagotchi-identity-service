package site.omagotchi.identityservice.auth.application.session;

// 비밀번호·계정 변경 Use Case가 사용자 전체 Refresh Session 폐기를 요청하는 사유
public enum RefreshSessionRevocationReason {

    PASSWORD_CHANGED,

    PASSWORD_RESET,

    ACCOUNT_DISABLED,

    ACCOUNT_WITHDRAWN
}
