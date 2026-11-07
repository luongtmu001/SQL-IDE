package app.framework.query;

/**
 * Phase 8 / D3: per-run limits taken from the Settings / toolbar.
 *
 * @param maxRows        rows kept per result set; {@code 0} = "All" (no cap)
 * @param timeoutSeconds statement timeout; {@code 0} = none
 * @param stopOnError    Settings › SQL › "Dừng khi lỗi": the first failing batch / statement stops the script
 */
public record QueryOptions(int maxRows, int timeoutSeconds, boolean stopOnError) {

    /** What the engine did before D3 (hard-coded). Used by callers that pass no options. */
    public static final int LEGACY_ROW_CAP = 10_000;

    public QueryOptions {
        if (maxRows < 0) {
            maxRows = 0;
        }
        if (timeoutSeconds < 0) {
            timeoutSeconds = 0;
        }
    }

    /** Stops on the first error (LLTool default). */
    public QueryOptions(int maxRows, int timeoutSeconds) {
        this(maxRows, timeoutSeconds, true);
    }

    public static QueryOptions legacy() {
        return new QueryOptions(LEGACY_ROW_CAP, 0, true);
    }

    /** Rows kept per result set; "All" ({@code maxRows == 0}) means no cap. */
    public int effectiveRowCap() {
        return maxRows > 0 ? maxRows : Integer.MAX_VALUE;
    }

    public boolean isUnlimited() {
        return maxRows == 0;
    }
}
