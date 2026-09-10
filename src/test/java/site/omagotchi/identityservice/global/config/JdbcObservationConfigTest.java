package site.omagotchi.identityservice.global.config;

import net.ttddyy.dsproxy.StatementType;
import net.ttddyy.observation.tracing.opentelemetry.OpenTelemetryQueryAnalyzer;
import net.ttddyy.observation.tracing.opentelemetry.QueryAnalysisResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JDBC 관측 SQL 정제")
class JdbcObservationConfigTest {

    private final OpenTelemetryQueryAnalyzer analyzer = new JdbcObservationConfig().openTelemetryQueryAnalyzer();

    @ParameterizedTest
    @ValueSource(strings = {
            "SELECT id FROM accounts WHERE email = 'PRIVATE_VALUE' AND id = 12345",
            "INSERT INTO accounts (email, id) VALUES ('PRIVATE_VALUE', 12345)",
            "UPDATE accounts SET email = 'PRIVATE_VALUE' WHERE id = 12345",
            "DELETE FROM accounts WHERE email IN ('PRIVATE_VALUE', 'OTHER_VALUE')",
            "SELECT id FROM accounts /* PRIVATE_VALUE */ WHERE email = ?",
            "SELECT id FROM accounts WHERE id IN (SELECT id FROM sessions WHERE token = 'PRIVATE_VALUE')"
    })
    @DisplayName("일반·Prepared SQL의 구조 유지와 직접 입력한 값·주석 제거")
    void sanitizesStatementAndPreparedSql(String sql) {
        // Given
        StatementType[] statementTypes = {StatementType.STATEMENT, StatementType.PREPARED};
        for (StatementType statementType : statementTypes) {
            // When
            QueryAnalysisResult result = analyzer.analyze(sql, false, statementType);

            // Then
            assertThat(result.getQueryText()).contains("accounts", "?")
                    .doesNotContain("PRIVATE_VALUE", "OTHER_VALUE", "12345", "/*");
            assertThat(result.getQuerySummary()).contains("accounts")
                    .doesNotContain("PRIVATE_VALUE", "OTHER_VALUE", "12345");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "SELECT FROM 'PRIVATE_VALUE'",
            "SELECT $$PRIVATE_VALUE$$",
            "SELECT $body$PRIVATE_VALUE$body$",
            "CREATE TABLE accounts (email TEXT DEFAULT 'PRIVATE_VALUE')"
    })
    @DisplayName("해석 실패·달러 인용·조회와 변경 이외 SQL의 본문 제외")
    void omitsUnsupportedSql(String sql) {
        // Given / When
        QueryAnalysisResult result = analyzer.analyze(sql, false, StatementType.PREPARED);

        // Then
        assertThat(result.getQueryText()).isNull();
    }

    @Test
    @DisplayName("긴 SQL의 본문 제외와 작업 종류 유지")
    void omitsOversizedSql() {
        // Given
        String sql = "SELECT " + "column".repeat(700) + " FROM accounts";
        // When
        QueryAnalysisResult result = analyzer.analyze(sql, false, StatementType.PREPARED);

        // Then
        assertThat(result.getQueryText()).isNull();
        assertThat(result.getOperationName()).isEqualTo("SELECT");
    }
}
