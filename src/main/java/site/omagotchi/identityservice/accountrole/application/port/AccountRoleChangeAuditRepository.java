package site.omagotchi.identityservice.accountrole.application.port;

import site.omagotchi.identityservice.accountrole.domain.AccountRoleChangeAudit;

public interface AccountRoleChangeAuditRepository {

    void append(AccountRoleChangeAudit audit);
}
