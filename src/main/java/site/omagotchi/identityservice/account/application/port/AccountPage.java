package site.omagotchi.identityservice.account.application.port;

import site.omagotchi.identityservice.account.domain.Account;

import java.util.List;

/** Application 계층이 Spring Data Page 타입에 의존하지 않도록 하는 조회 결과 경계다. */
public record AccountPage(List<Account> content, long totalElements) {

    public AccountPage {
        content = content == null ? List.of() : List.copyOf(content);
        if (totalElements < 0) {
            throw new IllegalArgumentException("totalElements는 음수일 수 없습니다.");
        }
        if (content.size() > totalElements) {
            throw new IllegalArgumentException("totalElements는 조회된 건수보다 작을 수 없습니다.");
        }
    }
}
