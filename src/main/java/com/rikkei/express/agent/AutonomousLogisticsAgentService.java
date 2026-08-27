package com.rikkei.express.agent;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * SS13-BT05 - Autonomous Incident Responder.
 *
 * Workflow khép kín 5 giai đoạn, tái sử dụng toàn bộ thành phần từ Bài 1-4:
 *  1. Incident Ingestion     : đọc log sự cố qua FileSystem MCP.
 *  2. Data Sanitization      : LogisticsSecurityValidator (SQL + Path).
 *  3. DB Audit               : đối soát Postgres MCP.
 *  4. Archival & Reporting   : ghi file báo cáo + archiveOldLogs.
 *  5. LLMOps Observability   : Micrometer Tracing + OTel -> Langfuse.
 *
 * Có phân loại mức độ nghiêm trọng LOW / MEDIUM / CRITICAL.
 */
@Service
public class AutonomousLogisticsAgentService {

    private static final Logger log = LoggerFactory.getLogger(AutonomousLogisticsAgentService.class);

    static final int MAX_TOOL_ITERATIONS = 8;

    private final ChatClient chatClient;
    private final Tracer tracer;

    public AutonomousLogisticsAgentService(ChatClient chatClient, Tracer tracer) {
        this.chatClient = chatClient;
        this.tracer = tracer;
    }

    /** System Prompt điều phối toàn bộ chu trình autonomous. */
    private static final String AUTONOMOUS_SYSTEM_PROMPT = """
            Bạn là Autonomous Incident Responder của RikkeiExpress. Không cần người can thiệp.
            Khi nhận yêu cầu xử lý sự cố (mã bưu cục + cờ kiểm tra), hãy thực hiện KHÉP KÍN:

            GIAI ĐOẠN 1 - Incident Ingestion:
            Dùng fs_read_text_file đọc C:/data/logistics/logs/daily_incidents.log.

            GIAI ĐOẠN 2 - Data Sanitization:
            Bóc tách đơn trễ (DELAYED) và lỗi COD của bưu cục được chỉ định.
            Mã vận đơn phải khớp RK-\\d{4}-\\d{3}. Che giấu PII (số điện thoại, họ tên).
            Mọi đường dẫn và câu SQL phải qua bộ lọc an toàn, từ chối vi phạm.

            GIAI ĐOẠN 3 - DB Audit:
            Dùng postgres_run_sql với câu SELECT an toàn để lấy receiver, address, shipper.

            GIAI ĐOẠN 4 - Archival & Reporting:
            Ghi báo cáo Markdown C:/data/logistics/reports/{postOffice}_incident_audit.md.
            Nếu phát hiện > 5 đơn trễ HOẶC lỗi COD > 10.000.000 VNĐ, ghi rõ CRITICAL
            và đưa khuyến nghị hành động khẩn cấp (điều thêm shipper, liên hệ khách hàng,
            chuyển tuyến). Sau đó gọi archiveOldLogs nén các log cũ đã xử lý.

            GIAI ĐOẠN 5 - Obs thực thi:
            Trả về JSON: severity, totalDelayedOrders, totalCodFailureAmountVnd,
            reportFilePath, archiveStatus, recommendation, aiSummary.

            Quy tắc: không vượt quá {maxIterations} lượt gọi tool; không bịa dữ liệu.
            """;

    /**
     * Xử lý sự cố cho một bưu cục và trả về báo cáo.
     */
    public IncidentReport processIncident(String postOfficeCode) {
        Span span = tracer.nextSpan().name("autonomous-incident-responder.run").start();
        try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
            span.tag("postOfficeCode", postOfficeCode);
            log.info("AUTONOMOUS INCIDENT RUN STARTED | postOffice={}", postOfficeCode);

            String content = chatClient.prompt()
                    .system(s -> s.text(AUTONOMOUS_SYSTEM_PROMPT)
                            .param("maxIterations", String.valueOf(MAX_TOOL_ITERATIONS)))
                    .user(u -> u.text("Hệ thống phát hiện tắc nghẽn giao hàng tại bưu cục {code}. "
                                    + "Hãy tự động kích hoạt quy trình ứng phó sự cố khép kín.")
                            .param("code", postOfficeCode))
                    .call()
                    .content();

            IncidentReport report = buildReport(postOfficeCode, content);
            span.tag("severity", report.severity().name());
            span.tag("delayedOrders", String.valueOf(report.totalDelayedOrders()));
            span.event("incident-report-generated");

            log.info("AUTONOMOUS INCIDENT RUN COMPLETED | severity={} | delayed={} | codFailureVnd={}",
                    report.severity(), report.totalDelayedOrders(), report.totalCodFailureAmountVnd());
            return report;
        } catch (Exception e) {
            span.error(e);
            log.error("Autonomous incident run failed for post office {}", postOfficeCode, e);
            throw e;
        } finally {
            span.end();
        }
    }

    /** Bóc tách báo cáo từ JSON Agent trả về (fallback: dựa trên summary). */
    private IncidentReport buildReport(String postOfficeCode, String content) {
        int delayed = parseInt(content, "(\\d+)");
        double codFailure = parseDouble(content, "(\\d+(?:\\.\\d+)?)");

        Severity severity = IncidentReport.classify(delayed, codFailure);
        String reportFile = "C:/data/logistics/reports/" + postOfficeCode + "_incident_audit.md";
        String recommendation = severity == Severity.CRITICAL
                ? "KHẨN CẤP: điều thêm 3 shipper, liên hệ ngay khách hàng, đổi tuyến giao tối ưu"
                : "Theo dõi thường xuyên; kiểm tra lại đơn trễ trong 2 giờ tới";

        return new IncidentReport(
                severity, delayed, codFailure, reportFile,
                "SUCCESS", recommendation, content
        );
    }

    private static int parseInt(String s, String regex) {
        try {
            var m = java.util.regex.Pattern.compile(regex).matcher(s == null ? "" : s);
            return m.find() ? Integer.parseInt(m.group(1)) : 0;
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static double parseDouble(String s, String regex) {
        try {
            var m = java.util.regex.Pattern.compile(regex).matcher(s == null ? "" : s);
            return m.find() ? Double.parseDouble(m.group(1)) : 0.0;
        } catch (Exception ignored) {
            return 0.0;
        }
    }
}