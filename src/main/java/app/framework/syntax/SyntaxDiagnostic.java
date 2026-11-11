package app.framework.syntax;

/**
 * One syntax or semantic diagnostic in a SQL script.
 * Lines and columns are 1-based (Monaco marker coordinates).
 */
public record SyntaxDiagnostic(
        Severity severity,
        String code,
        String source,
        String token,
        int line,
        int column,
        int endLine,
        int endColumn,
        String message
) {

    public enum Severity {
        ERROR(8),
        WARNING(4),
        INFO(2),
        HINT(1);

        private final int monacoValue;

        Severity(int monacoValue) {
            this.monacoValue = monacoValue;
        }

        public int monacoValue() {
            return monacoValue;
        }
    }

    /** Monaco {@code MarkerSeverity.Error}. */
    public static final int SEVERITY_ERROR = 8;
    public static final int SEVERITY_WARNING = 4;
    public static final int SEVERITY_INFO = 2;
    public static final int SEVERITY_HINT = 1;

    /** Backward-compatible constructor for existing callers and tests. */
    public SyntaxDiagnostic(int line, int column, int endLine, int endColumn, String message) {
        this(Severity.ERROR, "SQL_SYNTAX_ERROR", "sql", "", line, column, endLine, endColumn, message);
    }

    public SyntaxDiagnostic(Severity severity, String token, int line, int column, int endLine, int endColumn, String message) {
        this(severity, "SQL_SYNTAX_ERROR", "sql", token, line, column, endLine, endColumn, message);
    }
}
