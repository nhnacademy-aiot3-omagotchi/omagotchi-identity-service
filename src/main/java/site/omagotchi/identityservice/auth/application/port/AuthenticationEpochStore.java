package site.omagotchi.identityservice.auth.application.port;

import java.util.Optional;
import java.util.UUID;

public interface AuthenticationEpochStore {

    Optional<UUID> find(UUID accountId);

    UUID createIfAbsent(UUID accountId, UUID candidate);

    void replace(UUID accountId, UUID nextEpoch);
}
