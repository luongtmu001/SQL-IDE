package app.ui.dataeditor;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Parses user-entered cell text into strongly-typed Java objects based on SQL types (DE-6).
 */
public final class CellValueParser {

    private CellValueParser() {}

    public static Object parse(String text, int sqlType, String typeName) {
        if (text == null || "[NULL]".equalsIgnoreCase(text.trim())) {
            return null;
        }

        String trimmed = text.trim();
        String tn = typeName != null ? typeName.toLowerCase() : "";

        // String types: return exact text
        if (isTextType(sqlType, tn)) {
            return text;
        }

        // For non-string columns, empty string is treated as NULL
        if (trimmed.isEmpty()) {
            return null;
        }

        try {
            switch (sqlType) {
                case Types.BOOLEAN, Types.BIT:
                    if ("1".equals(trimmed) || "true".equalsIgnoreCase(trimmed)) return Boolean.TRUE;
                    if ("0".equals(trimmed) || "false".equalsIgnoreCase(trimmed)) return Boolean.FALSE;
                    throw new IllegalArgumentException("Giá trị boolean phải là 1/0 hoặc true/false");

                case Types.TINYINT, Types.SMALLINT, Types.INTEGER:
                    return Integer.valueOf(trimmed);

                case Types.BIGINT:
                    return Long.valueOf(trimmed);

                case Types.FLOAT, Types.REAL, Types.DOUBLE:
                    return Double.valueOf(trimmed);

                case Types.NUMERIC, Types.DECIMAL:
                    return new BigDecimal(trimmed);

                case Types.DATE:
                    return Date.valueOf(LocalDate.parse(trimmed));

                case Types.TIME, Types.TIME_WITH_TIMEZONE:
                    return Time.valueOf(LocalTime.parse(trimmed));

                case Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE:
                    if (trimmed.contains("T")) {
                        return Timestamp.valueOf(LocalDateTime.parse(trimmed));
                    }
                    return Timestamp.valueOf(trimmed);

                default:
                    if (tn.contains("uuid")) {
                        return UUID.fromString(trimmed);
                    }
                    if (tn.contains("int")) {
                        return Integer.valueOf(trimmed);
                    }
                    if (tn.contains("numeric") || tn.contains("decimal") || tn.contains("money")) {
                        return new BigDecimal(trimmed);
                    }
                    if (tn.contains("bool") || tn.contains("bit")) {
                        if ("1".equals(trimmed) || "true".equalsIgnoreCase(trimmed)) return Boolean.TRUE;
                        if ("0".equals(trimmed) || "false".equalsIgnoreCase(trimmed)) return Boolean.FALSE;
                    }
                    return text;
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Không thể chuyển '" + text + "' sang kiểu " + (tn.isBlank() ? "SQL(" + sqlType + ")" : tn), e);
        }
    }

    private static boolean isTextType(int sqlType, String typeName) {
        if (sqlType == Types.VARCHAR || sqlType == Types.NVARCHAR ||
            sqlType == Types.CHAR || sqlType == Types.NCHAR ||
            sqlType == Types.LONGVARCHAR || sqlType == Types.LONGNVARCHAR ||
            sqlType == Types.CLOB || sqlType == Types.NCLOB) {
            return true;
        }
        return typeName.contains("char") || typeName.contains("text");
    }
}

