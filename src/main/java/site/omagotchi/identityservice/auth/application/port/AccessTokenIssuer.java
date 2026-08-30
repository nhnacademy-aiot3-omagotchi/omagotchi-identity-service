package site.omagotchi.identityservice.auth.application.port;

import site.omagotchi.identityservice.auth.application.result.IssuedAccessToken;

import java.util.UUID;

public interface AccessTokenIssuer {

    IssuedAccessToken issue(UUID accountId, String globalRole, UUID authenticationEpoch);
}
