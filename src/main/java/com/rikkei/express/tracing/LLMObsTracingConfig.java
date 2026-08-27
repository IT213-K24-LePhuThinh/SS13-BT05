package com.rikkei.express.tracing;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.semconv.ResourceAttributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * SS13-BT05 - Cấu hình Tracing & Giám sát LLMOps.
 *
 * - Micrometer Tracing (micrometer-tracing-bridge-otel) + OpenTelemetry SDK.
 * - Exporter OTLP HTTP đẩy trace/span lên Langfuse Tracing Gateway.
 * - Advisor ToolTracingAdvisor bọc mỗi tool call để đo độ trễ, đếm token,
 *   ghi status ERROR nếu tool thất bại.
 */
@Configuration
public class LLMObsTracingConfig {

    private static final Logger log = LoggerFactory.getLogger(LLMObsTracingConfig.class);

    /** OTel SDK gửi spans tới Langfuse (OTLP HTTP). */
    @Bean
    public io.opentelemetry.sdk.OpenTelemetrySdk rikkeiOpenTelemetrySdk() {
        Resource resource = Resource.getDefault().merge(Resource.builder()
                .put(ResourceAttributes.SERVICE_NAME, "rikkei-express-agent")
                .build());

        OtlpHttpSpanExporter exporter = OtlpHttpSpanExporter.builder()
                .setEndpoint("http://localhost:4340/v1/traces")  // Langfuse OTLP gateway
                .setTimeout(Duration.ofSeconds(10))
                .build();

        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .setResource(resource)
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build();

        return OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .buildAndRegisterGlobal();
    }

    /** Bridge: OTel -> Micrometer Tracer cho ứng dụng. */
    @Bean
    public io.opentelemetry.OpenTelemetry openTelemetry(io.opentelemetry.sdk.OpenTelemetrySdk sdk) {
        return sdk;
    }

    /**
     * Advisor đo lường từng tool call:
     * - độ trễ (latency) mỗi tool,
     * - token tiêu thụ (từ response usage),
     * - status ERROR nếu tool gọi lỗi.
     */
    @Bean
    public Advisor toolTracingAdvisor(Tracer tracer) {
        return new Advisor() {
            @Override
            public org.springframework.ai.chat.client.advisor.api.AdvisedResponse aroundCall(
                    org.springframework.ai.chat.client.advisor.api.AdvisedRequest request,
                    CallAdvisorChain chain) {

                long startNano = System.nanoTime();
                Span span = tracer.nextSpan().name("agent.tool-call").start();
                try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
                    span.tag("tool.input", String.valueOf(request.advisedRequest().userText()));

                    org.springframework.ai.chat.client.advisor.api.AdvisedResponse response =
                            chain.nextAroundCall(request);

                    long latencyMs = (System.nanoTime() - startNano) / 1_000_000;
                    span.tag("tool.latencyMs", String.valueOf(latencyMs));
                    span.tag("tool.responseContent", truncate(response.response().getResult().getOutput().getText(), 200));

                    // Đo token nếu response có usage
                    if (response.response().getMetadata() != null) {
                        var usage = response.response().getMetadata().get("usage");
                        if (usage != null) {
                            span.tag("tool.tokens", String.valueOf(usage));
                        }
                    }
                    log.info("Trace span 'agent.tool-call' ended | latencyMs={} | status=OK", latencyMs);
                    return response;
                } catch (Exception e) {
                    span.tag("status", "ERROR");
                    span.error(e);
                    log.error("Trace span 'agent.tool-call' ended | status=ERROR", e);
                    throw e;
                } finally {
                    span.end();
                }
            }

            @Override
            public org.springframework.ai.chat.client.advisor.api.AdvisedResponse aroundStream(
                    org.springframework.ai.chat.client.advisor.api.AdvisedRequest request,
                    StreamAdvisorChain chain) {
                return chain.nextAroundStream(request);
            }
        };
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}