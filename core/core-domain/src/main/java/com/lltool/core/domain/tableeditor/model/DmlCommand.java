package com.lltool.core.domain.tableeditor.model;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * Parameterized DML Command for safe execution via PreparedStatement with separate dialect preview (DE-2).
 */
public record DmlCommand(
        String sql,
        List<DmlParam> params,
        String previewSql,
        DataRowChange.Type type,
        int targetRowId
) implements Serializable {

    public record DmlParam(Object value, int sqlType) implements Serializable {}

    public DmlCommand(String sql, List<DmlParam> params, String previewSql) {
        this(sql, params, previewSql, null, 0);
    }

    @Override
    public List<DmlParam> params() {
        return params != null ? Collections.unmodifiableList(params) : Collections.emptyList();
    }
}

