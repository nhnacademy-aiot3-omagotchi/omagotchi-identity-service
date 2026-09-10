package site.omagotchi.identityservice.global.logging;

import net.ttddyy.dsproxy.StatementType;
import net.ttddyy.observation.tracing.opentelemetry.OpenTelemetryQueryAnalyzer;
import net.ttddyy.observation.tracing.opentelemetry.QueryAnalysisResult;
import net.ttddyy.observation.tracing.opentelemetry.jsqlparser.JSqlParserQueryAnalyzer;
import org.springframework.stereotype.Component;

/** 실제 값·주석을 제외한 SQL 구조 분석. 원문 SQL의 캐시 보관 제외. */
@Component
public class SanitizedQueryAnalyzer implements OpenTelemetryQueryAnalyzer {

    private static final int MAX_QUERY_LENGTH = 4096;

    private final JSqlParserQueryAnalyzer analyzer = new JSqlParserQueryAnalyzer();

    @Override
    public QueryAnalysisResult analyze(String query, boolean isBatch, StatementType statementType) {
        // Prepared SQL에도 직접 적은 값·주석의 정제 적용. 실패 시 원문 반환 방지.
        StatementType analysisType = statementType == StatementType.PREPARED
                ? StatementType.STATEMENT : statementType;
        QueryAnalysisResult result = analyzer.analyze(query, isBatch, analysisType);
        String text = result.getQueryText();
        if (text != null) {
            // IN 목록을 줄인 라이브러리 표기 ('?')의 바인딩 자리 표시로 통일.
            text = text.replace("'?'", "?");
            // DML만 허용. 미처리 문자열·달러 인용·주석 또는 긴 SQL의 본문 제외.
            if (result.getOperationName() == null
                    || !result.getOperationName().matches("^(BATCH )?(SELECT|INSERT|UPDATE|DELETE)$")
                    || text.length() > MAX_QUERY_LENGTH
                    || text.contains("'") || text.contains("$")
                    || text.contains("--") || text.contains("/*")) {
                text = null;
            }
            result.setQueryText(text);
        }
        return result;
    }
}
