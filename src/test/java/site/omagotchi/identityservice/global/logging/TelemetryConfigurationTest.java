package site.omagotchi.identityservice.global.logging;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import net.ttddyy.observation.boot.autoconfigure.DataSourceObservationAutoConfiguration;
import net.ttddyy.observation.boot.autoconfigure.opentelemetry.DataSourceObservationOpenTelemetryAutoConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.micrometer.metrics.autoconfigure.CompositeMeterRegistryAutoConfiguration;
import org.springframework.boot.micrometer.metrics.autoconfigure.MetricsAutoConfiguration;
import org.springframework.boot.micrometer.metrics.autoconfigure.export.prometheus.PrometheusMetricsExportAutoConfiguration;
import org.springframework.boot.micrometer.observation.autoconfigure.ObservationAutoConfiguration;
import org.springframework.boot.micrometer.tracing.autoconfigure.MicrometerTracingAutoConfiguration;
import org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.OpenTelemetryTracingAutoConfiguration;
import org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.otlp.OtlpTracingAutoConfiguration;
import org.springframework.boot.opentelemetry.autoconfigure.OpenTelemetrySdkAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import site.omagotchi.identityservice.global.config.JdbcObservationConfig;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("애플리케이션 설정의 메트릭·Trace 연결")
class TelemetryConfigurationTest {

    @Test
    @DisplayName("동일 요청의 Histogram·부모 자식 Span 생성과 전송 전 원문 제외")
    void recordsMetricsAndRelatedSpans() throws Exception {
        // Given: 실제 설정과 자동 구성, Network 전송만 대체한 Exporter
        List<SpanData> spans = new CopyOnWriteArrayList<>();
        SpanExporter exporter = mock(SpanExporter.class);
        when(exporter.export(anyCollection())).thenAnswer(invocation -> {
            spans.addAll(invocation.<Collection<SpanData>>getArgument(0));
            return CompletableResultCode.ofSuccess();
        });
        when(exporter.shutdown()).thenReturn(CompletableResultCode.ofSuccess());
        when(exporter.flush()).thenReturn(CompletableResultCode.ofSuccess());
        // DB 연결만 대체, 실제 DataSource 장식·쿼리 분석·Trace 연결은 자동 구성 사용
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        Statement statement = mock(Statement.class);
        PreparedStatement preparedStatement = mock(PreparedStatement.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getURL()).thenReturn("jdbc:postgresql://localhost:5432/observability_test");
        when(metadata.getDatabaseProductName()).thenReturn("PostgreSQL");
        when(connection.createStatement()).thenReturn(statement);
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeBatch()).thenThrow(new SQLException("secret-db-error", "23505", 1234));

        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withConfiguration(AutoConfigurations.of(
                        MetricsAutoConfiguration.class, CompositeMeterRegistryAutoConfiguration.class,
                        PrometheusMetricsExportAutoConfiguration.class, ObservationAutoConfiguration.class,
                        MicrometerTracingAutoConfiguration.class, OpenTelemetrySdkAutoConfiguration.class,
                        OpenTelemetryTracingAutoConfiguration.class, OtlpTracingAutoConfiguration.class,
                        DataSourceObservationAutoConfiguration.class, DataSourceObservationOpenTelemetryAutoConfiguration.class))
                .withUserConfiguration(TraceAttributeFilter.class, JdbcObservationConfig.class)
                .withBean(SpanExporter.class, () -> exporter)
                .withBean(DataSource.class, () -> dataSource)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    ObservationRegistry registry = context.getBean(ObservationRegistry.class);

                    // When
                    Observation.createNotStarted("http.server.requests", registry)
                            .lowCardinalityKeyValue("uri", "/probe/{id}")
                            .highCardinalityKeyValue("http.url", "https://example.test/?token=secret")
                            .observe(() -> Observation.createNotStarted("probe.child", registry).observe(() -> {
                                try (Connection jdbcConnection = context.getBean(DataSource.class).getConnection();
                                     Statement jdbcStatement = jdbcConnection.createStatement();
                                     PreparedStatement jdbcPrepared = jdbcConnection.prepareStatement(
                                             "UPDATE accounts SET nickname = 'secret-inline' WHERE email = ?")) {
                                    jdbcStatement.executeQuery("SELECT 'secret-jdbc'");
                                    jdbcPrepared.setString(1, "secret-email");
                                    jdbcPrepared.executeUpdate();
                                    jdbcPrepared.addBatch();
                                    jdbcPrepared.setString(1, "secret-other-email");
                                    jdbcPrepared.addBatch();
                                    assertThatThrownBy(jdbcPrepared::executeBatch).isInstanceOf(SQLException.class);
                                } catch (SQLException exception) {
                                    throw new IllegalStateException(exception);
                                }
                            }));
                    context.getBean(SdkTracerProvider.class).forceFlush().join(5, TimeUnit.SECONDS);

                    // Then
                    SpanData parent = spans.stream().filter(span -> span.getName().equals("http.server.requests"))
                            .findFirst().orElseThrow();
                    SpanData child = spans.stream().filter(span -> span.getName().equals("probe.child"))
                            .findFirst().orElseThrow();
                    assertThat(child.getTraceId()).isEqualTo(parent.getTraceId());
                    assertThat(child.getParentSpanId()).isEqualTo(parent.getSpanId());
                    assertThat(spans).anySatisfy(span -> {
                        assertThat(span.getAttributes().get(AttributeKey.stringKey("db.system.name")))
                                .isEqualTo("postgresql");
                        assertThat(span.getParentSpanId()).isEqualTo(child.getSpanId());
                        assertThat(span.getAttributes().get(AttributeKey.stringKey("db.query.text")))
                                .isEqualTo("SELECT ?");
                    });
                    assertThat(spans).anySatisfy(span -> {
                        assertThat(span.getAttributes().get(AttributeKey.stringKey("db.query.text")))
                                .isEqualTo("UPDATE accounts SET nickname = ? WHERE email = ?");
                        assertThat(span.getAttributes().get(AttributeKey.stringKey("db.operation.batch.size")))
                                .isEqualTo("2");
                        assertThat(span.getAttributes().get(AttributeKey.stringKey("db.response.status_code")))
                                .isEqualTo("23505");
                        assertThat(span.getParentSpanId()).isEqualTo(child.getSpanId());
                    });
                    assertThat(spans).allSatisfy(span -> assertThat(span.getAttributes().asMap().toString())
                            .doesNotContain("secret-", "jdbc.params", "jdbc.query"));
                    assertThat(parent.getAttributes().asMap().toString()).doesNotContain("secret", "http.url");
                    String scrape = context.getBean(PrometheusMeterRegistry.class).scrape();
                    assertThat(scrape).contains("http_server_requests_seconds_bucket", "/probe/{id}")
                            .doesNotContain("secret");
                });
    }
}
