package site.omagotchi.identityservice.accountaudit.domain;

/** 통합 감사 뷰가 어느 원본 테이블에서 왔는지 나타낸다. */
public enum AccountPermissionChangeAuditType {

    ACCOUNT_STATUS,
    ACCOUNT_ROLE
}
