package com.lltool.core.domain.schemacompare.model;

import java.util.Collections;
import java.util.List;

/**
 * Kết quả hoàn chỉnh của phiên so sánh Schema giữa Source và Target.
 */
public class SchemaCompareResult {

    private final SchemaSnapshot source;
    private final SchemaSnapshot target;
    private final List<SchemaObjectDiff> diffs;
    private final SchemaCompareSummary summary;

    public SchemaCompareResult(SchemaSnapshot source, SchemaSnapshot target, List<SchemaObjectDiff> diffs, SchemaCompareSummary summary) {
        this.source = source;
        this.target = target;
        this.diffs = diffs != null ? diffs : Collections.emptyList();
        this.summary = summary;
    }

    public SchemaSnapshot getSource() {
        return source;
    }

    public SchemaSnapshot getTarget() {
        return target;
    }

    public List<SchemaObjectDiff> getDiffs() {
        return Collections.unmodifiableList(diffs);
    }

    public SchemaCompareSummary getSummary() {
        return summary;
    }
}

