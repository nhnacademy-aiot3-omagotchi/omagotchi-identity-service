package site.omagotchi.identityservice.account.application;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.identityservice.account.application.dto.SignupCommand;
import site.omagotchi.identityservice.account.domain.Account;
import site.omagotchi.identityservice.account.domain.PasswordPolicy;
import site.omagotchi.identityservice.account.infrastructure.AccountStore;

@Service
@RequiredArgsConstructor
public class SignupAccountUseCase {

    private final AccountReader accountReader;
    private final AccountStore accountStore;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public Account execute(SignupCommand command) {
        // 비밀번호 정책 검증
        PasswordPolicy.validate(command.password());

        // account 생성 및 email 중복 여부 검사
        Account account = Account.register(
                command.email(),
                passwordEncoder.encode(command.password()),
                command.name()
        );
        accountReader.ensureEmailAvailable(account.getEmail());

        // account 저장
        return accountStore.save(account);
    }
}
