package app.framework.query;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;

/**
 * D4: reads a JDBC cell into a value whose {@code toString()} is what the grid, copy, export and filters show
 * (LLTool {@code serialize_cell}): binary → {@code 0x…}, numerics without exponent, dates as
 * {@code yyyy-MM-dd HH:mm:ss.fff}, driver objects (PG json / interval / arrays, timestamptz, datetimeoffset…)
 * as the server's own text. Numbers stay {@link Number} and booleans {@link Boolean}, so sorting and
 * right-alignment keep working; dates become ISO-ordered text, so they sort correctly as text too.
 */
public final class CellValues {

    private CellValues() {
    }

    /** Binary cell: shown as {@code 0x0102AB}; the bytes stay available (Visualize › Image / Hex). */
    public static final class BinaryValue implements Comparable<BinaryValue> {
        private final byte[] bytes;

        public BinaryValue(byte[] bytes) {
            this.bytes = bytes != null ? bytes : new byte[0];
        }

        public byte[] bytes() {
            return bytes;
        }

        @Override
        public String toString() {
            return "0x" + HexFormat.of().withUpperCase().formatHex(bytes);
        }

        @Override
        public int compareTo(BinaryValue o) {
            return Arrays.compareUnsigned(bytes, o.bytes);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof BinaryValue b && Arrays.equals(bytes, b.bytes);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(bytes);
        }
    }

    /** DECIMAL / NUMERIC / MONEY: a {@link BigDecimal} that never prints in exponent form ({@code 1E-7}). */
    public static final class PlainDecimal extends BigDecimal {
        public PlainDecimal(BigDecimal v) {
            super(v.unscaledValue(), v.scale());
        }

        @Override
        public String toString() {
            return toPlainString();
        }
    }

    /** Types whose driver object is not useful as text: the server's own text is read instead. */
    private static final Set<String> SERVER_TEXT_TYPES = Set.of(
            "timestamptz", "timetz", "interval", "json", "jsonb", "xml", "money", "inet", "cidr", "macaddr",
            "datetimeoffset", "sql_variant", "hierarchyid", "tsvector", "tsquery", "point", "line", "lseg", "box",
            "path", "polygon", "circle", "bit varying", "varbit");

    /**
     * Reads column {@code i} of the current row.
     *
     * @param sqlType  {@code ResultSetMetaData.getColumnType}
     * @param typeName {@code ResultSetMetaData.getColumnTypeName}
     */
    public static Object read(ResultSet rs, int i, int sqlType, String typeName, ScriptRunner.Dialect dialect) throws SQLException {
        String tn = typeName != null ? typeName.toLowerCase(Locale.ROOT) : "";
        if (dialect == ScriptRunner.Dialect.SQLITE) {
            Object v = rs.getObject(i);
            return v instanceof byte[] b ? new BinaryValue(b) : v;
        }
        boolean serverText = SERVER_TEXT_TYPES.contains(tn) || tn.startsWith("_") /* PG arrays */;
        if (serverText && dialect == ScriptRunner.Dialect.SQLSERVER && (tn.equals("money") || tn.equals("smallmoney"))) {
            serverText = false; // SQL Server money is a DECIMAL: keep it numeric
        }
        if (serverText) {
            return rs.getString(i);
        }
        switch (sqlType) {
            case Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY, Types.BLOB -> {
                byte[] b = rs.getBytes(i);
                return b == null ? null : new BinaryValue(b);
            }
            case Types.DECIMAL, Types.NUMERIC -> {
                try {
                    BigDecimal d = rs.getBigDecimal(i);
                    return d == null ? null : new PlainDecimal(d);
                } catch (SQLException | NumberFormatException e) {
                    return rs.getString(i); // PostgreSQL numeric 'NaN' / 'Infinity'
                }
            }
            case Types.DATE -> {
                LocalDate d = safe(() -> rs.getObject(i, LocalDate.class));
                return d != null ? d.toString() : textOrNull(rs, i);
            }
            case Types.TIME -> {
                LocalTime t = safe(() -> rs.getObject(i, LocalTime.class));
                return t != null ? formatTime(t) : textOrNull(rs, i);
            }
            case Types.TIMESTAMP -> {
                LocalDateTime t = safe(() -> rs.getObject(i, LocalDateTime.class));
                return t != null ? formatDateTime(t) : textOrNull(rs, i);
            }
            case Types.TIMESTAMP_WITH_TIMEZONE, Types.TIME_WITH_TIMEZONE, Types.OTHER, Types.ARRAY, Types.SQLXML,
                 Types.STRUCT, Types.JAVA_OBJECT, Types.CLOB, Types.NCLOB -> {
                return rs.getString(i);
            }
            default -> {
                Object v = rs.getObject(i);
                if (v == null) return null;
                if (v instanceof byte[] b) return new BinaryValue(b);
                if (v instanceof BigDecimal d) return new PlainDecimal(d);
                String cls = v.getClass().getName();
                if (cls.startsWith("org.postgresql.") || cls.startsWith("microsoft.sql.") || cls.startsWith("com.microsoft.")
                        || v instanceof java.sql.Array || v instanceof java.sql.Clob || v instanceof java.sql.SQLXML) {
                    return rs.getString(i);
                }
                return v;
            }
        }
    }

    private interface SqlSupplier<T> {
        T get() throws SQLException;
    }

    /** {@code getObject(i, Class)} not supported by a driver / type → null (the caller falls back to text). */
    private static <T> T safe(SqlSupplier<T> s) {
        try {
            return s.get();
        } catch (SQLException | RuntimeException e) {
            return null;
        }
    }

    private static String textOrNull(ResultSet rs, int i) throws SQLException {
        return rs.getString(i);
    }

    /** {@code 2024-01-02 10:11:12}, with the fraction only when non-zero, trailing zeros cut but at least 3 digits. */
    public static String formatDateTime(LocalDateTime t) {
        return t.toLocalDate() + " " + formatTime(t.toLocalTime());
    }

    public static String formatTime(LocalTime t) {
        String base = String.format("%02d:%02d:%02d", t.getHour(), t.getMinute(), t.getSecond());
        int nanos = t.getNano();
        if (nanos == 0) return base;
        String frac = String.format("%09d", nanos);
        int end = frac.length();
        while (end > 3 && frac.charAt(end - 1) == '0') end--;
        return base + "." + frac.substring(0, end);
    }
}
