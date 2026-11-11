package com.lltool.core.domain.schemacompare.model;

/**
 * Thống kê tổng hợp số lượng khác biệt trong phiên so sánh Schema.
 */
public record SchemaCompareSummary(
        int totalObjects,
        int identicalCount,
        int differentCount,
        int missingInTargetCount,
        int extraInTargetCount,
        int tablesCount,
        int viewsCount,
        int proceduresCount,
        int functionsCount,
        int triggersCount,
        boolean hasDestructiveChanges
) {
    public int getTotalDifferences() {
        return differentCount + missingInTargetCount + extraInTargetCount;
    }
}

