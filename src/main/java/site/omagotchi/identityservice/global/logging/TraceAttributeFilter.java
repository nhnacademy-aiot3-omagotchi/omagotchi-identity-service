package site.omagotchi.identityservice.global.logging;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationFilter;
import net.ttddyy.observation.tracing.QueryContext;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.util.Set;

/** Trace 전송 전 정제된 SQL·호출 결과만 허용, URL·입력값 등 원문 제외. */
@Component
public class TraceAttributeFilter implements ObservationFilter {

    // 전용 SQL 필드는 SanitizedQueryAnalyzer의 정제 결과만 전달, Collector 허용 목록과 함께 관리.
    private static final Set<String> ALLOWED_KEYS = Set.of(
            "db.query.summary", "omagotchi.db.query.text", "db.response.status_code", "db.operation.batch.size",
            "error.type",
            "gen_ai.usage.input_tokens", "gen_ai.usage.output_tokens", "gen_ai.usage.total_tokens"
    );

    @Override
    public Observation.Context map(Observation.Context context) {
        if (context instanceof QueryContext) {
            // JDBC 분석 결과만 전용 필드로 전달. 다른 계측의 원문 SQL은 아래 허용 목록에서 제외.
            KeyValue query = context.getHighCardinalityKeyValue("db.query.text");
            if (query != null) {
                context.addHighCardinalityKeyValue(KeyValue.of("omagotchi.db.query.text", query.getValue()));
            }
            // 쿼리 종료 후 확정된 PostgreSQL 오류 코드 보존. 예외 메시지는 제외.
            if (context.getError() instanceof SQLException exception) {
                String sqlState = exception.getSQLState();
                if (sqlState != null && sqlState.matches("[0-9A-Z]{5}")) {
                    context.addHighCardinalityKeyValue(KeyValue.of("db.response.status_code", sqlState));
                }
            }
        }
        for (KeyValue keyValue : context.getHighCardinalityKeyValues()) {
            if (!ALLOWED_KEYS.contains(keyValue.getKey())) {
                context.removeHighCardinalityKeyValue(keyValue.getKey());
            }
        }
        return context;
    }
}
