package app.ui.results;

import app.models.QueryResultRow;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.scene.control.TableView;

import java.util.*;

/**
 * Filters of one result grid (Phase 4 follow-up 2026-11-05): every stacked / tabbed grid keeps
 * its own column filters. A column filter = optional condition + optional set of allowed values.
 */
public final class GridFilterState {

    /** Conditions of "LỌC THEO ĐIỀU KIỆN" (LLTool grid-result.js). */
    public enum Op {
        NONE("-- Không lọc điều kiện --", false),
        EQUALS("Bằng (=)", true),
        NOT_EQUALS("Khác (≠)", true),
        CONTAINS("Chứa", true),
        NOT_CONTAINS("Không chứa", true),
        STARTS_WITH("Bắt đầu bằng", true),
        ENDS_WITH("Kết thúc bằng", true),
        GREATER("Lớn hơn (>)", true),
        GREATER_EQ("Lớn hơn hoặc bằng (≥)", true),
        LESS("Nhỏ hơn (<)", true),
        LESS_EQ("Nhỏ hơn hoặc bằng (≤)", true),
        IS_NULL("Rỗng (NULL)", false),
        NOT_NULL("Không rỗng", false);

        public final String label;
        public final boolean needsValue;

        Op(String label, boolean needsValue) {
            this.label = label;
            this.needsValue = needsValue;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /** {@code allowed == null}: every value allowed. */
    public record ColumnFilter(Op op, String value, Set<String> allowed) {
        public boolean isEmpty() {
            return (op == null || op == Op.NONE) && allowed == null;
        }
    }

    public static final String NULL_KEY = "\u0000NULL";
    private static final String KEY = GridFilterState.class.getName();

    private final ObservableList<QueryResultRow> master;
    private final FilteredList<QueryResultRow> filtered;
    private final Map<Integer, ColumnFilter> filters = new HashMap<>();
    private String globalQuery;
    private Runnable onChanged;

    public GridFilterState(ObservableList<QueryResultRow> master, FilteredList<QueryResultRow> filtered) {
        this.master = master;
        this.filtered = filtered;
    }

    public static void attach(TableView<?> table, GridFilterState state) {
        table.getProperties().put(KEY, state);
    }

    public static GridFilterState of(TableView<?> table) {
        Object o = table.getProperties().get(KEY);
        return o instanceof GridFilterState s ? s : null;
    }

    public ObservableList<QueryResultRow> master() {
        return master;
    }

    public FilteredList<QueryResultRow> filtered() {
        return filtered;
    }

    public void setOnChanged(Runnable r) {
        onChanged = r;
    }

    public ColumnFilter get(int col) {
        return filters.get(col);
    }

    public boolean isFiltered(int col) {
        ColumnFilter f = filters.get(col);
        return f != null && !f.isEmpty();
    }

    public void set(int col, ColumnFilter f) {
        if (f == null || f.isEmpty()) filters.remove(col); else filters.put(col, f);
        apply();
    }

    public void clearAll() {
        filters.clear();
        apply();
    }

    public void setGlobalQuery(String q) {
        globalQuery = q;
        apply();
    }

    public void apply() {
        filtered.setPredicate(this::test);
        if (onChanged != null) onChanged.run();
    }

    private boolean test(QueryResultRow row) {
        if (row == null) return false;
        if (globalQuery != null && !globalQuery.isBlank() && !row.matchesQuery(globalQuery)) return false;
        for (Map.Entry<Integer, ColumnFilter> e : filters.entrySet()) {
            Object v = row.getValue(e.getKey());
            ColumnFilter f = e.getValue();
            if (f.allowed() != null && !f.allowed().contains(key(v))) return false;
            if (f.op() != null && f.op() != Op.NONE && !matches(f.op(), v, f.value())) return false;
        }
        return true;
    }

    public static String key(Object v) {
        return v == null ? NULL_KEY : v.toString();
    }

    private static boolean matches(Op op, Object v, String value) {
        if (op == Op.IS_NULL) return v == null || v.toString().isEmpty();
        if (op == Op.NOT_NULL) return v != null && !v.toString().isEmpty();
        if (v == null) return false;
        String s = v.toString();
        String q = value != null ? value : "";
        String sl = s.toLowerCase(Locale.ROOT), ql = q.toLowerCase(Locale.ROOT);
        return switch (op) {
            case EQUALS -> compare(s, q) == 0;
            case NOT_EQUALS -> compare(s, q) != 0;
            case CONTAINS -> sl.contains(ql);
            case NOT_CONTAINS -> !sl.contains(ql);
            case STARTS_WITH -> sl.startsWith(ql);
            case ENDS_WITH -> sl.endsWith(ql);
            case GREATER -> compare(s, q) > 0;
            case GREATER_EQ -> compare(s, q) >= 0;
            case LESS -> compare(s, q) < 0;
            case LESS_EQ -> compare(s, q) <= 0;
            default -> true;
        };
    }

    /** Numbers compare as numbers, everything else case-insensitively as text. */
    static int compare(String a, String b) {
        try {
            return new java.math.BigDecimal(a.trim()).compareTo(new java.math.BigDecimal(b.trim()));
        } catch (Exception ignored) {
            return a.compareToIgnoreCase(b);
        }
    }

    private List<String> columnNames = List.of();
    private String nullText = "NULL";

    /** Column names (index = data column) used by {@link #describe()}. */
    public void setColumnNames(List<String> names, String nullDisplay) {
        columnNames = names != null ? names : List.of();
        if (nullDisplay != null) nullText = nullDisplay;
    }

    public boolean hasFilters() {
        return !filters.isEmpty() || (globalQuery != null && !globalQuery.isBlank());
    }

    public int shownCount() {
        return filtered.size();
    }

    public int totalCount() {
        return master.size();
    }

    /**
     * Tooltip text: every active filter (column, condition, chosen values) and how many rows that
     * filter alone keeps, then the combined result (user request 2026-11-05).
     */
    public String describe() {
        StringBuilder sb = new StringBuilder();
        if (globalQuery != null && !globalQuery.isBlank()) {
            long n = master.stream().filter(r -> r.matchesQuery(globalQuery)).count();
            sb.append("• Lọc nhanh: \"").append(globalQuery).append("\" → ").append(String.format("%,d", n)).append(" dòng\n");
        }
        List<Integer> cols = new ArrayList<>(filters.keySet());
        Collections.sort(cols);
        for (int c : cols) {
            ColumnFilter f = filters.get(c);
            String name = c < columnNames.size() ? columnNames.get(c) : ("Cột " + (c + 1));
            sb.append("• ").append(name).append(": ");
            List<String> parts = new ArrayList<>();
            if (f.op() != null && f.op() != Op.NONE) {
                parts.add(f.op().label + (f.op().needsValue ? " \"" + f.value() + "\"" : ""));
            }
            if (f.allowed() != null) {
                List<String> vals = new ArrayList<>();
                for (String k : f.allowed()) vals.add(NULL_KEY.equals(k) ? "(" + nullText + ")" : (k.isEmpty() ? "(trống)" : k));
                Collections.sort(vals);
                String shown = vals.size() <= 5 ? String.join(", ", vals)
                        : String.join(", ", vals.subList(0, 5)) + " … (+" + (vals.size() - 5) + ")";
                parts.add("giá trị = " + shown);
            }
            long n = master.stream().filter(r -> testOne(f, r.getValue(c))).count();
            sb.append(String.join("; ", parts)).append(" → ").append(String.format("%,d", n)).append(" dòng\n");
        }
        sb.append("Kết quả: ").append(String.format("%,d / %,d", filtered.size(), master.size())).append(" dòng");
        return sb.toString();
    }

    private static boolean testOne(ColumnFilter f, Object v) {
        if (f.allowed() != null && !f.allowed().contains(key(v))) return false;
        return f.op() == null || f.op() == Op.NONE || matches(f.op(), v, f.value());
    }

    /** Distinct values of a column with their counts, sorted (numbers numerically). */
    public List<Map.Entry<String, Integer>> distinct(int col, int limit) {
        Map<String, Integer> counts = new HashMap<>();
        for (QueryResultRow r : master) counts.merge(key(r.getValue(col)), 1, Integer::sum);
        List<Map.Entry<String, Integer>> list = new ArrayList<>(counts.entrySet());
        list.sort((x, y) -> {
            if (x.getKey().equals(NULL_KEY)) return -1;
            if (y.getKey().equals(NULL_KEY)) return 1;
            return compare(x.getKey(), y.getKey());
        });
        return list.size() > limit ? list.subList(0, limit) : list;
    }
}
