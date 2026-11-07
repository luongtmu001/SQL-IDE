package app.framework.syntax;

/** One syntax problem in a script. Lines / columns are 1-based (Monaco marker coordinates). */
public record SyntaxDiagnostic(int line, int column, int endLine, int endColumn, String message) {

    /** Monaco {@code MarkerSeverity.Error}. */
    public static final int SEVERITY_ERROR = 8;
}
