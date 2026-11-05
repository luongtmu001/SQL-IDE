package app.ui.editor;

import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.util.Collection;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SqlSyntaxHighlighter {

    private static final String[] KEYWORDS = new String[] {
        "SELECT", "FROM", "WHERE", "INSERT", "INTO", "UPDATE", "DELETE",
        "CREATE", "ALTER", "DROP", "TRUNCATE", "TABLE", "VIEW", "INDEX",
        "JOIN", "INNER", "LEFT", "RIGHT", "FULL", "OUTER", "CROSS", "ON",
        "GROUP", "BY", "ORDER", "HAVING", "LIMIT", "OFFSET", "FETCH", "FIRST", "ROWS", "ONLY",
        "UNION", "ALL", "INTERSECT", "EXCEPT", "DISTINCT",
        "AND", "OR", "NOT", "IN", "IS", "NULL", "LIKE", "ILIKE", "BETWEEN", "EXISTS",
        "CASE", "WHEN", "THEN", "ELSE", "END", "AS",
        "VALUES", "SET", "DEFAULT", "PRIMARY", "KEY", "FOREIGN", "REFERENCES", "CONSTRAINT", "CHECK", "UNIQUE",
        "BEGIN", "COMMIT", "ROLLBACK", "TRANSACTION", "SAVEPOINT",
        "CALL", "EXEC", "EXECUTE", "FUNCTION", "PROCEDURE", "RETURNS", "RETURN",
        "GRANT", "REVOKE", "CASCADE", "RESTRICT", "WITH", "RECURSIVE"
    };

    private static final String[] DATATYPES = new String[] {
        "INT", "INTEGER", "BIGINT", "SMALLINT", "TINYINT",
        "VARCHAR", "CHAR", "TEXT", "NVARCHAR", "NCHAR",
        "BOOLEAN", "BOOL",
        "DATE", "TIME", "TIMESTAMP", "TIMESTAMPTZ", "INTERVAL",
        "NUMERIC", "DECIMAL", "FLOAT", "REAL", "DOUBLE", "PRECISION",
        "SERIAL", "BIGSERIAL", "UUID", "JSON", "JSONB", "BYTEA", "BLOB", "CLOB"
    };

    private static final String[] FUNCTIONS = new String[] {
        "COUNT", "SUM", "AVG", "MIN", "MAX",
        "COALESCE", "NULLIF", "GREATEST", "LEAST",
        "CONCAT", "SUBSTRING", "TRIM", "LTRIM", "RTRIM", "LOWER", "UPPER", "LENGTH", "REPLACE",
        "NOW", "CURRENT_TIMESTAMP", "CURRENT_DATE", "CURRENT_TIME", "DATE_TRUNC", "EXTRACT", "AGE",
        "ROUND", "CEIL", "FLOOR", "ABS", "MOD", "POWER", "SQRT",
        "ROW_NUMBER", "RANK", "DENSE_RANK", "NTILE", "LEAD", "LAG"
    };

    private static final String KEYWORD_PATTERN = "\\b(" + String.join("|", KEYWORDS) + ")\\b";
    private static final String DATATYPE_PATTERN = "\\b(" + String.join("|", DATATYPES) + ")\\b";
    private static final String FUNCTION_PATTERN = "\\b(" + String.join("|", FUNCTIONS) + ")\\b";
    private static final String STRING_PATTERN = "'([^'\\\\]|\\\\.)*'";
    private static final String COMMENT_SINGLE_PATTERN = "--[^\n]*";
    private static final String COMMENT_MULTI_PATTERN = "/\\*[\\s\\S]*?\\*/";
    private static final String NUMBER_PATTERN = "\\b\\d+(\\.\\d+)?\\b";

    private static final Pattern PATTERN = Pattern.compile(
        "(?<KEYWORD>" + KEYWORD_PATTERN + ")"
        + "|(?<DATATYPE>" + DATATYPE_PATTERN + ")"
        + "|(?<FUNCTION>" + FUNCTION_PATTERN + ")"
        + "|(?<STRING>" + STRING_PATTERN + ")"
        + "|(?<COMMENTSINGLE>" + COMMENT_SINGLE_PATTERN + ")"
        + "|(?<COMMENTMULTI>" + COMMENT_MULTI_PATTERN + ")"
        + "|(?<NUMBER>" + NUMBER_PATTERN + ")",
        Pattern.CASE_INSENSITIVE
    );

    public static StyleSpans<Collection<String>> computeHighlighting(String text) {
        Matcher matcher = PATTERN.matcher(text);
        int lastKwEnd = 0;
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();

        while (matcher.find()) {
            String styleClass =
                matcher.group("KEYWORD") != null ? "sql-keyword" :
                matcher.group("DATATYPE") != null ? "sql-datatype" :
                matcher.group("FUNCTION") != null ? "sql-function" :
                matcher.group("STRING") != null ? "sql-string" :
                matcher.group("COMMENTSINGLE") != null || matcher.group("COMMENTMULTI") != null ? "sql-comment" :
                matcher.group("NUMBER") != null ? "sql-number" :
                null;

            spansBuilder.add(Collections.emptyList(), matcher.start() - lastKwEnd);
            spansBuilder.add(Collections.singleton(styleClass), matcher.end() - matcher.start());
            lastKwEnd = matcher.end();
        }

        spansBuilder.add(Collections.emptyList(), text.length() - lastKwEnd);
        return spansBuilder.create();
    }
}

