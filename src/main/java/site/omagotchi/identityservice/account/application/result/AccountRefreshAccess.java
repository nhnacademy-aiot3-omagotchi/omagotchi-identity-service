package site.omagotchi.identityservice.account.application.result;

import site.omagotchi.identityservice.account.domain.AccountStatus;

// Auth가 Refresh Token 갱신 가능 여부와 거부 이유를 판단할 때 사용하는 값
public enum AccountRefreshAccess {

    // 갱신 허용
    ALLOWED,

    // 갱신 거부: 계정 비활성화
    ACCOUNT_DISABLED,

    // 갱신 거부: 탈퇴한 계정
    ACCOUNT_WITHDRAWN;

    public static AccountRefreshAccess from(AccountStatus accountStatus) {
        return switch (accountStatus) {
            case ACTIVE -> ALLOWED;
            case DISABLED -> ACCOUNT_DISABLED;
            case WITHDRAWN -> ACCOUNT_WITHDRAWN;
        };
    }
}
