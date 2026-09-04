package site.omagotchi.identityservice.account.application.port;

import site.omagotchi.identityservice.account.domain.AccountStatusChangeAudit;

public interface AccountStatusChangeAuditRepository {

    void append(AccountStatusChangeAudit audit);
}
