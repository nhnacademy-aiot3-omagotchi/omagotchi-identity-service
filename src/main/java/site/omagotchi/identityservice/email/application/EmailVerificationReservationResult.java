package site.omagotchi.identityservice.email.application;

// OTP Challenge와 재발송 쿨다운의 선점 결과 반환
public record EmailVerificationReservationResult(
        boolean reserved,
        long remainingCooldownSeconds
) {

    public EmailVerificationReservationResult {
        if (reserved && remainingCooldownSeconds != 0) {
            throw new IllegalArgumentException("선점 성공 시 남은 쿨다운은 0이어야 합니다.");
        }
        if (!reserved && remainingCooldownSeconds < 1) {
            throw new IllegalArgumentException("선점 실패 시 남은 쿨다운은 1초 이상이어야 합니다.");
        }
    }

    public static EmailVerificationReservationResult acquired() {
        return new EmailVerificationReservationResult(true, 0);
    }

    public static EmailVerificationReservationResult cooldown(long remainingCooldownSeconds) {
        return new EmailVerificationReservationResult(false, remainingCooldownSeconds);
    }
}
