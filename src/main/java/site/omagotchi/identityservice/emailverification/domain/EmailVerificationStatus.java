package site.omagotchi.identityservice.emailverification.domain;

public enum EmailVerificationStatus {
    OPEN,
    CONSUMED,
    EXHAUSTED,
    SUPERSEDED
}
