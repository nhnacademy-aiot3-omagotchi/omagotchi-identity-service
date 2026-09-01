package site.omagotchi.identityservice.account.application.port;

/**
 * 허용 정렬 기준의 화이트리스트다.
 *
 * <p>외부 문자열이 Entity 필드명으로 직행하지 못하게 해 임의 컬럼 정렬을 차단한다.
 */
public enum AccountSortOption {

    CREATED_AT_DESC,

    CREATED_AT_ASC,

    EMAIL_ASC,

    NAME_ASC
}
