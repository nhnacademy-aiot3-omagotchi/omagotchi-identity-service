package site.omagotchi.identityservice.email.application.port;

public final class EmailDeliveryException extends RuntimeException {

    private final Integer providerStatusCode;
    private final String providerErrorName;
    private final boolean retryable;

    public EmailDeliveryException(
            Integer providerStatusCode,
            String providerErrorName,
            boolean retryable
    ) {
        this(providerStatusCode, providerErrorName, retryable, null);
    }

    public EmailDeliveryException(
            Integer providerStatusCode,
            String providerErrorName,
            boolean retryable,
            Throwable cause
    ) {
        super("이메일 발송 제공자 호출에 실패했습니다.", cause);
        this.providerStatusCode = providerStatusCode;
        this.providerErrorName = providerErrorName;
        this.retryable = retryable;
    }

    public Integer providerStatusCode() {
        return providerStatusCode;
    }

    public String providerErrorName() {
        return providerErrorName;
    }

    public boolean retryable() {
        return retryable;
    }
}
