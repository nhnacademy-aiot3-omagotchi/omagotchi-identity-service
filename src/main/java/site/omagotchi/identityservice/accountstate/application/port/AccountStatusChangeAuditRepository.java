package site.omagotchi.identityservice.accountstate.application.port;

import site.omagotchi.identityservice.accountstate.domain.AccountStatusChangeAudit;

public interface AccountStatusChangeAuditRepository {

    void append(AccountStatusChangeAudit audit);
}
