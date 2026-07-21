package site.omagotchi.identityservice.account.domain;

public enum AccountStatus {

    // 활성 상태
    ACTIVE,

    // 로그인 실패 등의 이유로 계정 일시 잠금 상태
    LOCKED,

    // 제한된 보안 운영 절차에 의한 전역 비활성화 상태
    DISABLED,

    // 사용자에 의한 탈퇴 상태
    WITHDRAWN
}
