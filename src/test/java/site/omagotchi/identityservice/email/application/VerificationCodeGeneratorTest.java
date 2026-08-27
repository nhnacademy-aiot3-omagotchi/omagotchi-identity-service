package site.omagotchi.identityservice.email.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;

import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class VerificationCodeGeneratorTest {

    @Test
    @DisplayName("SecureRandom 결과를 앞자리 0을 포함한 6자리 코드로 변환")
    void generatesSixDigitCode() {
        SecureRandom secureRandom = mock(SecureRandom.class);
        given(secureRandom.nextInt(1_000_000)).willReturn(42_910);
        VerificationCodeGenerator generator = new VerificationCodeGenerator(secureRandom);

        String code = generator.generate();

        then(code).isEqualTo("042910");
        verify(secureRandom).nextInt(1_000_000);
    }
}
