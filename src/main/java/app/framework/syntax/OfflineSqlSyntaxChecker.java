package app.framework.syntax;

import java.util.List;

/**
 * Fast offline/client-side SQL syntax checks for basic structural errors:
 * <ul>
 *   <li>Unclosed string literals ({@code '...})</li>
 *   <li>Unclosed multiline comments ({@code /* ... *&#47;})</li>
 *   <li>Unclosed brackets ({@code [...]})</li>
 *   <li>Unbalanced parentheses ({@code (...)})</li>
 *   <li>Consecutive commas ({@code ,,}) or trailing commas before major clauses</li>
 * </ul>
 */
public final class OfflineSqlSyntaxChecker {

    private OfflineSqlSyntaxChecker() {}

    public static List<SyntaxDiagnostic> check(String sql) {
        return StructuralSqlChecks.checkBasicStructural(sql, false);
    }
}
