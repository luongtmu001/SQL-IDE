package spike.grid;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Data shown by both grids: column names / types and rows of values (same kinds of values as SQL-IDE). */
public final class SpikeData {

    public record Column(String name, String type) {
    }

    /** One scenario of the test plan (K1–K4). */
    public enum Scenario {
        K1("K1 · 32.960 dòng × 30 cột", 32_960, 30),
        K2("K2 · 0 dòng × 279 cột", 0, 279),
        K3("K3 · 5.000 dòng × 279 cột", 5_000, 279),
        K4("K4 · 1.000.000 dòng × 10 cột", 1_000_000, 10);

        public final String label;
        public final int rows;
        public final int cols;

        Scenario(String label, int rows, int cols) {
            this.label = label;
            this.rows = rows;
            this.cols = cols;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public final List<Column> columns;
    public final List<Object[]> rows;

    public SpikeData(List<Column> columns, List<Object[]> rows) {
        this.columns = columns;
        this.rows = rows;
    }

    /** Deterministic values: ints, text, decimals, dates with NULLs, booleans (like GridPerfIT). */
    public static SpikeData generate(int rowCount, int colCount) {
        List<Column> cols = new ArrayList<>(colCount);
        for (int j = 0; j < colCount; j++) {
            String type = switch (j % 5) {
                case 0 -> "int";
                case 1 -> "nvarchar";
                case 2 -> "decimal";
                case 3 -> "datetime";
                default -> "bit";
            };
            cols.add(new Column(j == 0 ? "Stt" : "Column_" + j, type));
        }
        List<Object[]> rows = new ArrayList<>(rowCount);
        for (int i = 0; i < rowCount; i++) {
            Object[] v = new Object[colCount];
            for (int j = 0; j < colCount; j++) {
                v[j] = switch (j % 5) {
                    case 0 -> i * 7 + j;
                    case 1 -> "Text " + i + "-" + j;
                    case 2 -> new BigDecimal(i).movePointLeft(2);
                    case 3 -> (i + j) % 4 == 0 ? null : "2024-01-02 10:11:12";
                    default -> (i % 2 == 0);
                };
            }
            rows.add(v);
        }
        return new SpikeData(cols, rows);
    }

    public static SpikeData of(Scenario s) {
        return generate(s.rows, s.cols);
    }

    /** Text of a cell as both grids show it (NULL for null; BigDecimal without exponent). */
    public static String text(Object v) {
        if (v == null) return "NULL";
        if (v instanceof BigDecimal d) return d.toPlainString();
        return v.toString();
    }
}
