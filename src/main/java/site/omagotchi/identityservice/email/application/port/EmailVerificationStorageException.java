package site.omagotchi.identityservice.email.application.port;

// 이메일 인증 저장소 명령을 실행할 수 없을 때 발생하는 exception
// 호출자가 복구·외부 오류 변환을 반드시 결정해야 함
public final class EmailVerificationStorageException extends RuntimeException {

    public EmailVerificationStorageException(Throwable cause) {
        super("이메일 인증 저장소 명령 실행에 실패했습니다.", cause);
    }
}
