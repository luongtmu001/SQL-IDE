package com.lltool.core.application.tabledesigner;

import com.lltool.core.common.error.AppError;
import com.lltool.core.common.validation.ValidationResult;
import com.lltool.core.domain.tabledesigner.model.ColumnDesign;
import com.lltool.core.domain.tabledesigner.model.TableDesign;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Validates a TableDesign against relational rules and dialect invariants.
 * Fixes Bug B13 (detecting duplicate column names, empty names, and invalid type constraints).
 */
public class TableDesignValidator {

    public static final String ERR_TABLE_EMPTY = "TABLE_NAME_EMPTY";
    public static final String ERR_TABLE_TOO_LONG = "TABLE_NAME_TOO_LONG";
    public static final String ERR_NO_COLUMNS = "NO_COLUMNS";
    public static final String ERR_COL_NAME_EMPTY = "COLUMN_NAME_EMPTY";
    public static final String ERR_COL_NAME_DUPLICATE = "COLUMN_NAME_DUPLICATE";
    public static final String ERR_COL_NAME_TOO_LONG = "COLUMN_NAME_TOO_LONG";
    public static final String ERR_TYPE_EMPTY = "DATA_TYPE_EMPTY";
    public static final String ERR_INVALID_LENGTH = "INVALID_LENGTH";
    public static final String ERR_INVALID_SCALE = "INVALID_SCALE";
    public static final String ERR_MULTIPLE_IDENTITIES = "MULTIPLE_IDENTITIES";
    public static final String ERR_IDENTITY_WITH_DEFAULT = "IDENTITY_WITH_DEFAULT";

    public ValidationResult validate(TableDesign design) {
        if (design == null) {
            return ValidationResult.invalid(new AppError.ValidationError("NULL_DESIGN", "Thiết kế bảng không được rỗng", Map.of()));
        }

        List<AppError.ValidationError> errors = new ArrayList<>();
        String engine = design.getEngine() != null ? design.getEngine().toLowerCase() : "sqlserver";
        int maxIdentLen = "postgresql".equals(engine) ? 63 : 128;

        // 1. Table name checks
        if (design.getTable() == null || design.getTable().isBlank()) {
            errors.add(new AppError.ValidationError(ERR_TABLE_EMPTY, "Tên bảng không được để trống", Map.of("field", "table")));
        } else {
            String tbl = design.getTable().trim();
            if (tbl.length() > maxIdentLen) {
                errors.add(new AppError.ValidationError(ERR_TABLE_TOO_LONG,
                        "Tên bảng vượt quá độ dài tối đa (" + maxIdentLen + " ký tự)",
                        Map.of("field", "table", "length", tbl.length(), "max", maxIdentLen)));
            }
        }

        // 2. Columns checks
        List<ColumnDesign> columns = design.getColumns();
        if (columns == null || columns.isEmpty()) {
            errors.add(new AppError.ValidationError(ERR_NO_COLUMNS, "Bảng phải có ít nhất một cột", Map.of("field", "columns")));
            return ValidationResult.of(errors);
        }

        Set<String> seenNames = new HashSet<>();
        int identityCount = 0;

        for (int i = 0; i < columns.size(); i++) {
            ColumnDesign col = columns.get(i);
            String colName = col.getName() != null ? col.getName().trim() : "";
            String fieldPrefix = "columns[" + i + "]";

            if (colName.isBlank()) {
                errors.add(new AppError.ValidationError(ERR_COL_NAME_EMPTY,
                        "Tên cột thứ " + (i + 1) + " không được để trống",
                        Map.of("field", fieldPrefix + ".name", "index", i)));
            } else {
                if (colName.length() > maxIdentLen) {
                    errors.add(new AppError.ValidationError(ERR_COL_NAME_TOO_LONG,
                            "Tên cột '" + colName + "' vượt quá độ dài tối đa (" + maxIdentLen + " ký tự)",
                            Map.of("field", fieldPrefix + ".name", "column", colName, "max", maxIdentLen)));
                }

                String lower = colName.toLowerCase();
                if (seenNames.contains(lower)) {
                    errors.add(new AppError.ValidationError(ERR_COL_NAME_DUPLICATE,
                            "Tên cột '" + colName + "' bị trùng lặp trong bảng",
                            Map.of("field", fieldPrefix + ".name", "column", colName)));
                } else {
                    seenNames.add(lower);
                }
            }

            // Data type check
            String dataType = col.getDataType() != null ? col.getDataType().trim() : "";
            if (dataType.isBlank()) {
                errors.add(new AppError.ValidationError(ERR_TYPE_EMPTY,
                        "Kiểu dữ liệu của cột '" + colName + "' không được để trống",
                        Map.of("field", fieldPrefix + ".dataType", "column", colName)));
            } else {
                String dtLower = dataType.toLowerCase();
                // Decimal / Numeric check
                if (dtLower.equals("decimal") || dtLower.equals("numeric")) {
                    Integer prec = col.getLength();
                    Integer scale = col.getScale();
                    if (prec != null && prec <= 0) {
                        errors.add(new AppError.ValidationError(ERR_INVALID_LENGTH,
                                "Độ chính xác (precision) của cột '" + colName + "' phải lớn hơn 0",
                                Map.of("field", fieldPrefix + ".length", "column", colName)));
                    }
                    if (prec != null && scale != null && (scale < 0 || scale > prec)) {
                        errors.add(new AppError.ValidationError(ERR_INVALID_SCALE,
                                "Số chữ số thập phân (scale) của cột '" + colName + "' phải nằm trong khoảng từ 0 đến " + prec,
                                Map.of("field", fieldPrefix + ".scale", "column", colName)));
                    }
                }
            }

            // Identity checks
            if (col.isIdentity()) {
                identityCount++;
                if (col.getDefaultValue() != null && !col.getDefaultValue().isBlank()) {
                    errors.add(new AppError.ValidationError(ERR_IDENTITY_WITH_DEFAULT,
                            "Cột tự tăng (IDENTITY) '" + colName + "' không được có giá trị mặc định",
                            Map.of("field", fieldPrefix + ".defaultValue", "column", colName)));
                }
            }
        }

        if ("sqlserver".equals(engine) && identityCount > 1) {
            errors.add(new AppError.ValidationError(ERR_MULTIPLE_IDENTITIES,
                    "SQL Server chỉ cho phép tối đa một cột tự tăng (IDENTITY) trên mỗi bảng",
                    Map.of("identityCount", identityCount)));
        }

        return ValidationResult.of(errors);
    }
}

