package site.omagotchi.identityservice.accountaudit.application.port;

public interface AccountPermissionChangeAuditRepository {

    /** 최근 발생 순으로 감사 기록을 페이지 조회한다. */
    AccountPermissionChangeAuditPage findRecent(int page, int size);
}
