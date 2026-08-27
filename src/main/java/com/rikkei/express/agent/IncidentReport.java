package com.rikkei.express.agent;

/** Kết quả xử lý sự cố của Autonomous Agent. */
public record IncidentReport(
        Severity severity,
        int totalDelayedOrders,
        double totalCodFailureAmountVnd,
        String reportFilePath,
        String archiveStatus,
        String recommendation,
        String aiSummary
) {
    /** Đánh cờ CRITICAL nếu trên 5 đơn trễ HOẶC lỗi COD > 10.000.000 VNĐ. */
    public static Severity classify(int delayedOrders, double codFailureAmountVnd) {
        if (delayedOrders > 5 || codFailureAmountVnd > 10_000_000) {
            return Severity.CRITICAL;
        }
        if (delayedOrders >= 2 || codFailureAmountVnd > 2_000_000) {
            return Severity.MEDIUM;
        }
        return Severity.LOW;
    }
}