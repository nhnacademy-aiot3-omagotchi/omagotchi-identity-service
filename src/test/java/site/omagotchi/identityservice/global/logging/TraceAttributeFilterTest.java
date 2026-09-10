package site.omagotchi.identityservice.global.logging;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import net.ttddyy.observation.tracing.QueryContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Trace 원문 제외")
class TraceAttributeFilterTest {

    @Test
    @DisplayName("원본 URL·바인딩 값 제외, 정제된 SQL·DB 결과와 HTTP 집계 Label 유지")
    void removesRawValuesWithoutChangingMetricLabels() {
        // Given
        Observation.Context context = new QueryContext()
                .addHighCardinalityKeyValue(KeyValue.of("http.url", "https://example.test/?serviceKey=secret"))
                .addHighCardinalityKeyValue(KeyValue.of("unknown.input", "secret"))
                .addHighCardinalityKeyValue(KeyValue.of("db.query.summary", "SELECT accounts"))
                .addHighCardinalityKeyValue(KeyValue.of("db.query.text", "SELECT id FROM accounts WHERE email = ?"))
                .addHighCardinalityKeyValue(KeyValue.of("db.response.status_code", "0"))
                .addHighCardinalityKeyValue(KeyValue.of("db.operation.batch.size", "2"))
                .addHighCardinalityKeyValue(KeyValue.of("jdbc.params[0]", "secret"))
                .addHighCardinalityKeyValue(KeyValue.of("db.statement", "SELECT 'secret'"))
                .addLowCardinalityKeyValue(KeyValue.of("uri", "/api/items/{id}"));

        // When
        new TraceAttributeFilter().map(context);

        // Then
        assertThat(context.getHighCardinalityKeyValues())
                .containsExactlyInAnyOrder(
                        KeyValue.of("db.query.summary", "SELECT accounts"),
                        KeyValue.of("omagotchi.db.query.text", "SELECT id FROM accounts WHERE email = ?"),
                        KeyValue.of("db.response.status_code", "0"),
                        KeyValue.of("db.operation.batch.size", "2"));
        assertThat(context.getLowCardinalityKeyValue("uri"))
                .isEqualTo(KeyValue.of("uri", "/api/items/{id}"));
    }

    @Test
    @DisplayName("JDBC 분석을 거치지 않은 계측의 원문 SQL 제외")
    void removesSqlOutsideQueryContext() {
        // Given
        Observation.Context context = new Observation.Context()
                .addHighCardinalityKeyValue(KeyValue.of("db.query.text", "SELECT 'secret'"));

        // When
        new TraceAttributeFilter().map(context);

        // Then
        assertThat(context.getHighCardinalityKeyValues()).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "secret-value")
    @DisplayName("없거나 형식이 다른 SQL 오류 코드의 추가 기록 제외")
    void omitsInvalidSqlState(String sqlState) {
        // Given
        QueryContext context = new QueryContext();
        context.setError(new SQLException("secret-message", sqlState));

        // When
        new TraceAttributeFilter().map(context);

        // Then
        assertThat(context.getHighCardinalityKeyValues()).isEmpty();
    }
}
