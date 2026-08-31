package site.omagotchi.identityservice.emailverification.application;

public class EmailDeliveryException extends RuntimeException {

    private final EmailDeliveryFailureKind failureKind;

    public EmailDeliveryException(String message, Throwable cause) {
        this(message, EmailDeliveryFailureKind.UNCLASSIFIED, cause);
    }

    private EmailDeliveryException(
            String message,
            EmailDeliveryFailureKind failureKind,
            Throwable cause
    ) {
        super(message, cause);
        this.failureKind = failureKind;
    }

    public static EmailDeliveryException rateLimited(String message, Throwable cause) {
        return new EmailDeliveryException(
                message,
                EmailDeliveryFailureKind.RATE_LIMITED,
                cause
        );
    }

    public EmailDeliveryFailureKind failureKind() {
        return failureKind;
    }
}
