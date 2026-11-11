package com.lltool.core.application.visualizer;

import com.lltool.core.domain.visualizer.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dialect-aware SQL AST & Query Pipeline Flow Analyzer (Phase 3 - Slice 6).
 * Analyzes SELECT queries into structured pipeline transformation stages,
 * computes exact 1-based SourceRanges for two-way editor synchronization,
 * and lays out the interactive data pipeline diagram (SV-1, SV-2, SV-3, SV-4, SV-7).
 */
public class QueryFlowAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(QueryFlowAnalyzer.class);

    private static final double STAGE_SPACING_X = 260.0;
    private static final double NODE_SPACING_Y = 110.0;
    private static final double ORIGIN_X = 80.0;
    private static final double ORIGIN_Y = 220.0;

    private static final Set<String> RESERVED_KEYWORDS = new HashSet<>(Arrays.asList(
            "WHERE", "JOIN", "INNER", "LEFT", "RIGHT", "FULL", "CROSS", "NATURAL", "OUTER",
            "ON", "GROUP", "BY", "ORDER", "HAVING", "LIMIT", "OFFSET", "FETCH", "APPLY",
            "UNION", "INTERSECT", "EXCEPT", "MINUS", "SELECT", "FROM", "WITH", "AS", "INTO"
    ));

    private static final Pattern VIEW_HEADER_PATTERN = Pattern.compile(
            "^\\s*CREATE\\s+(?:OR\\s+(?:ALTER|REPLACE)\\s+)?(?:MATERIALIZED\\s+)?VIEW\\s+[\\w\\.\\[\\]\"`]+\\s+AS\\s+",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Parses a SQL query into a comprehensive QueryFlowGraph.
     */
    public QueryFlowGraph analyze(String rawSql) {
        if (rawSql == null || rawSql.isBlank()) {
            return new QueryFlowGraph("");
        }

        // SV-7: Strip CREATE VIEW ... AS header if present
        int offsetAdjustment = 0;
        String sqlToAnalyze = rawSql;
        Matcher viewMatcher = VIEW_HEADER_PATTERN.matcher(rawSql);
        if (viewMatcher.find()) {
            offsetAdjustment = viewMatcher.end();
            sqlToAnalyze = rawSql.substring(offsetAdjustment);
        }

        // Quick check for non-SELECT statements (SV-2)
        String trimmedUpper = stripCommentsAndLeadingWhitespace(sqlToAnalyze).toUpperCase();
        if (!trimmedUpper.startsWith("SELECT") && !trimmedUpper.startsWith("WITH") && !trimmedUpper.startsWith("(")) {
            return QueryFlowGraph.unsupported(rawSql, "Chỉ hỗ trợ trực quan hóa câu lệnh SELECT và View.");
        }

        QueryFlowGraph graph = new QueryFlowGraph(rawSql);

        // Define Pipeline Stages
        graph.addStageTitle("1. Nguồn Dữ liệu");
        graph.addStageTitle("2. Kết nối (JOIN)");
        graph.addStageTitle("3. Bộ lọc (WHERE)");
        graph.addStageTitle("4. Gom nhóm (GROUP BY)");
        graph.addStageTitle("5. Sắp xếp & Giới hạn");
        graph.addStageTitle("6. Đầu ra (Output)");

        try {
            buildPipeline(rawSql, sqlToAnalyze, offsetAdjustment, graph);
        } catch (Exception ex) {
            log.error("Error building query flow pipeline: {}", ex.getMessage(), ex);
            return QueryFlowGraph.unsupported(rawSql, "Không thể phân tích cú pháp câu lệnh: " + ex.getMessage());
        }

        // Calculate layout coordinates
        calculatePipelineLayout(graph);

        return graph;
    }

    private String stripCommentsAndLeadingWhitespace(String sql) {
        int len = sql.length();
        int i = 0;
        while (i < len) {
            char c = sql.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }
            if (c == '-' && i + 1 < len && sql.charAt(i + 1) == '-') {
                i += 2;
                while (i < len && sql.charAt(i) != '\n' && sql.charAt(i) != '\r') {
                    i++;
                }
                continue;
            }
            if (c == '/' && i + 1 < len && sql.charAt(i + 1) == '*') {
                i += 2;
                while (i + 1 < len && !(sql.charAt(i) == '*' && sql.charAt(i + 1) == '/')) {
                    i++;
                }
                i += 2;
                continue;
            }
            break;
        }
        return i < len ? sql.substring(i) : "";
    }

    private void buildPipeline(String fullSql, String sql, int baseOffset, QueryFlowGraph graph) {
        int idCounter = 0;
        int stepCounter = 1;

        // 1. Check for WITH CTE clauses (SV-2)
        String workingSql = sql;
        int workingOffset = baseOffset;

        int withStart = findKeywordAtDepth0(workingSql, "WITH");
        if (withStart != -1) {
            int endOfCte = findEndOfWithClause(workingSql, withStart + 4);
            if (endOfCte > withStart) {
                String withClause = workingSql.substring(withStart, endOfCte);
                parseCtes(fullSql, withClause, workingOffset + withStart, graph, idCounter, stepCounter);
                workingSql = workingSql.substring(endOfCte);
                workingOffset += endOfCte;
            }
        }

        // 2. Split clauses at depth 0 (SV-2)
        Map<String, ClauseSpan> clauses = splitClausesAtDepth0(workingSql, workingOffset);

        ClauseSpan fromSpan = clauses.get("FROM");
        if (fromSpan == null) {
            // Value-only SELECT (e.g., SELECT 1 AS x)
            ClauseSpan selectSpan = clauses.get("SELECT");
            if (selectSpan != null) {
                QueryFlowNode outNode = new QueryFlowNode(
                        "output_" + (++idCounter),
                        QueryNodeType.OUTPUT,
                        "Kết quả đầu ra (SELECT)",
                        "Hằng số / Biểu thức"
                );
                outNode.setStageIndex(5);
                outNode.setStepIndex(stepCounter++);
                outNode.setDetails("Chiếu biểu thức: " + selectSpan.text.trim());
                outNode.setSqlFragment(selectSpan.text);
                outNode.setSourceRange(computeSourceRange(fullSql, selectSpan.startOffset, selectSpan.endOffset));
                graph.addNode(outNode);
            }
            return;
        }

        QueryFlowNode currentStream = null;

        // -------------------------------------------------------------
        // Stage 1 & 2: Sources and JOINs (SV-2, SV-3)
        // -------------------------------------------------------------
        List<TableSource> sources = parseFromAndJoins(fromSpan.text, fromSpan.startOffset);
        if (!sources.isEmpty()) {
            TableSource mainSource = sources.get(0);
            QueryFlowNode mainNode = new QueryFlowNode(
                    "source_" + (++idCounter),
                    mainSource.isSubquery ? QueryNodeType.SUBQUERY : QueryNodeType.SOURCE_TABLE,
                    mainSource.tableName,
                    (mainSource.alias != null && !mainSource.alias.isBlank()) ? "Alias: " + mainSource.alias : "Base Table"
            );
            mainNode.setStageIndex(0);
            mainNode.setStepIndex(stepCounter++);
            mainNode.setDetails(mainSource.isSubquery ? "Bảng con (Subquery)" : "Quét bảng chính (Table Scan)");
            mainNode.setSqlFragment(mainSource.rawFragment);
            mainNode.setSourceRange(computeSourceRange(fullSql, mainSource.startOffset, mainSource.endOffset));

            graph.addNode(mainNode);
            currentStream = mainNode;

            // Sequential chaining of subsequent JOINs (SV-3)
            for (int i = 1; i < sources.size(); i++) {
                TableSource joinSrc = sources.get(i);

                // Right stream table node in stage 0
                QueryFlowNode joinTableNode = new QueryFlowNode(
                        "join_src_" + (++idCounter),
                        joinSrc.isSubquery ? QueryNodeType.SUBQUERY : QueryNodeType.JOIN_TABLE,
                        joinSrc.tableName,
                        (joinSrc.alias != null && !joinSrc.alias.isBlank()) ? "Alias: " + joinSrc.alias : "Joined Table"
                );
                joinTableNode.setStageIndex(0);
                joinTableNode.setStepIndex(stepCounter++);
                joinTableNode.setDetails(joinSrc.isSubquery ? "Bảng con kết nối (Subquery)" : "Quét bảng kết nối (Table Scan)");
                joinTableNode.setSqlFragment(joinSrc.rawFragment);
                joinTableNode.setSourceRange(computeSourceRange(fullSql, joinSrc.startOffset, joinSrc.endOffset));
                graph.addNode(joinTableNode);

                // Join operator in stage 1
                QueryFlowNode joinOpNode = new QueryFlowNode(
                        "join_op_" + (++idCounter),
                        QueryNodeType.JOIN,
                        joinSrc.joinType + " JOIN",
                        !joinSrc.joinCondition.isBlank() ? joinSrc.joinCondition : "Điều kiện kết nối"
                );
                joinOpNode.setStageIndex(1);
                joinOpNode.setStepIndex(stepCounter++);
                joinOpNode.setDetails("Kết nối " + joinSrc.joinType + (!joinSrc.joinCondition.isBlank() ? ": " + joinSrc.joinCondition : ""));
                joinOpNode.setSqlFragment(joinSrc.rawFragment);
                joinOpNode.setSourceRange(computeSourceRange(fullSql, joinSrc.startOffset, joinSrc.endOffset));
                graph.addNode(joinOpNode);

                // Edges: left stream (previous operator) and right stream (joinTableNode) -> joinOpNode
                graph.addEdge(QueryFlowEdge.of(currentStream.getId(), joinOpNode.getId(), "Left Stream"));
                graph.addEdge(QueryFlowEdge.of(joinTableNode.getId(), joinOpNode.getId(), "Right Stream"));

                // Chained stream output is now this join operator
                currentStream = joinOpNode;
            }
        }

        // -------------------------------------------------------------
        // Stage 3: Where Filter (SV-2)
        // -------------------------------------------------------------
        ClauseSpan whereSpan = clauses.get("WHERE");
        if (whereSpan != null && !whereSpan.text.isBlank()) {
            QueryFlowNode filterNode = new QueryFlowNode(
                    "filter_" + (++idCounter),
                    QueryNodeType.FILTER,
                    "Điều kiện lọc (WHERE)",
                    whereSpan.text.trim()
            );
            filterNode.setStageIndex(2);
            filterNode.setStepIndex(stepCounter++);
            filterNode.setDetails("Loại bỏ các dòng không thỏa mãn điều kiện lọc");
            filterNode.setSqlFragment("WHERE " + whereSpan.text);
            filterNode.setSourceRange(computeSourceRange(fullSql, whereSpan.startOffset, whereSpan.endOffset));

            graph.addNode(filterNode);
            if (currentStream != null) {
                graph.addEdge(QueryFlowEdge.of(currentStream.getId(), filterNode.getId(), "Filtered"));
            }
            currentStream = filterNode;
        }

        // -------------------------------------------------------------
        // Stage 4: Aggregation (GROUP BY & HAVING) (SV-2)
        // -------------------------------------------------------------
        ClauseSpan groupSpan = clauses.get("GROUP BY");
        if (groupSpan != null && !groupSpan.text.isBlank()) {
            QueryFlowNode aggNode = new QueryFlowNode(
                    "aggregate_" + (++idCounter),
                    QueryNodeType.AGGREGATE,
                    "Gom nhóm (GROUP BY)",
                    groupSpan.text.trim()
            );
            aggNode.setStageIndex(3);
            aggNode.setStepIndex(stepCounter++);
            aggNode.setDetails("Tính toán hàm tổng hợp theo các khóa: " + groupSpan.text.trim());
            aggNode.setSqlFragment("GROUP BY " + groupSpan.text);
            aggNode.setSourceRange(computeSourceRange(fullSql, groupSpan.startOffset, groupSpan.endOffset));

            graph.addNode(aggNode);
            if (currentStream != null) {
                graph.addEdge(QueryFlowEdge.of(currentStream.getId(), aggNode.getId(), "Grouped Stream"));
            }
            currentStream = aggNode;
        }

        ClauseSpan havingSpan = clauses.get("HAVING");
        if (havingSpan != null && !havingSpan.text.isBlank()) {
            QueryFlowNode havingNode = new QueryFlowNode(
                    "having_" + (++idCounter),
                    QueryNodeType.FILTER,
                    "Điều kiện nhóm (HAVING)",
                    havingSpan.text.trim()
            );
            havingNode.setStageIndex(3);
            havingNode.setStepIndex(stepCounter++);
            havingNode.setDetails("Lọc các nhóm sau khi tổng hợp: " + havingSpan.text.trim());
            havingNode.setSqlFragment("HAVING " + havingSpan.text);
            havingNode.setSourceRange(computeSourceRange(fullSql, havingSpan.startOffset, havingSpan.endOffset));

            graph.addNode(havingNode);
            if (currentStream != null) {
                graph.addEdge(QueryFlowEdge.of(currentStream.getId(), havingNode.getId(), "Group Filtered"));
            }
            currentStream = havingNode;
        }

        // -------------------------------------------------------------
        // Stage 5: Sort & Limit (ORDER BY, LIMIT, OFFSET) (SV-2)
        // -------------------------------------------------------------
        ClauseSpan orderSpan = clauses.get("ORDER BY");
        ClauseSpan limitSpan = clauses.get("LIMIT");
        if (orderSpan != null || limitSpan != null) {
            String sortText = orderSpan != null ? orderSpan.text.trim() : "";
            if (limitSpan != null) {
                sortText += (!sortText.isBlank() ? " · " : "") + "LIMIT " + limitSpan.text.trim();
            }

            QueryFlowNode sortNode = new QueryFlowNode(
                    "sort_" + (++idCounter),
                    QueryNodeType.SORT,
                    "Sắp xếp & Giới hạn",
                    sortText
            );
            sortNode.setStageIndex(4);
            sortNode.setStepIndex(stepCounter++);
            sortNode.setDetails("Sắp xếp hoặc giới hạn kết quả: " + sortText);

            int startOfs = orderSpan != null ? orderSpan.startOffset : limitSpan.startOffset;
            int endOfs = limitSpan != null ? limitSpan.endOffset : orderSpan.endOffset;
            sortNode.setSqlFragment(fullSql.substring(startOfs, Math.min(fullSql.length(), endOfs)));
            sortNode.setSourceRange(computeSourceRange(fullSql, startOfs, endOfs));

            graph.addNode(sortNode);
            if (currentStream != null) {
                graph.addEdge(QueryFlowEdge.of(currentStream.getId(), sortNode.getId(), "Sorted Stream"));
            }
            currentStream = sortNode;
        }

        // -------------------------------------------------------------
        // Stage 6: Output Projection (SELECT) (SV-2)
        // -------------------------------------------------------------
        ClauseSpan selectSpan = clauses.get("SELECT");
        if (selectSpan != null) {
            String selectBody = selectSpan.text.trim();
            boolean isDistinct = selectBody.toUpperCase().startsWith("DISTINCT");
            if (isDistinct) {
                selectBody = selectBody.substring(8).trim();
            }

            String topLimit = null;
            if (selectBody.toUpperCase().startsWith("TOP")) {
                Matcher m = Pattern.compile("(?i)^TOP\\s+(\\d+)\\s*").matcher(selectBody);
                if (m.find()) {
                    topLimit = m.group(1);
                    selectBody = selectBody.substring(m.end()).trim();
                }
            }

            QueryFlowNode outputNode = new QueryFlowNode(
                    "output_" + (++idCounter),
                    QueryNodeType.OUTPUT,
                    "Kết quả đầu ra (SELECT" + (isDistinct ? " DISTINCT" : "") + ")",
                    topLimit != null ? "TOP " + topLimit + " dòng kết quả" : "Tất cả các dòng kết quả"
            );
            outputNode.setStageIndex(5);
            outputNode.setStepIndex(stepCounter++);
            outputNode.setDetails("Chiếu các cột ra kết quả: " + selectBody);
            outputNode.setSqlFragment("SELECT " + selectSpan.text);
            outputNode.setSourceRange(computeSourceRange(fullSql, selectSpan.startOffset, selectSpan.endOffset));

            graph.addNode(outputNode);
            if (currentStream != null) {
                graph.addEdge(QueryFlowEdge.of(currentStream.getId(), outputNode.getId(), "Result Stream"));
            }
        }
    }

    private int findKeywordAtDepth0(String sql, String kw) {
        int depth = 0;
        boolean inQuotes = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;
        int len = sql.length();

        for (int i = 0; i < len; i++) {
            char c = sql.charAt(i);
            if (inLineComment) {
                if (c == '\n' || c == '\r') inLineComment = false;
                continue;
            }
            if (inBlockComment) {
                if (c == '*' && i + 1 < len && sql.charAt(i + 1) == '/') {
                    inBlockComment = false;
                    i++;
                }
                continue;
            }
            if (inQuotes) {
                if (c == '\'') {
                    if (i + 1 < len && sql.charAt(i + 1) == '\'') {
                        i++;
                    } else {
                        inQuotes = false;
                    }
                }
                continue;
            }

            if (c == '-' && i + 1 < len && sql.charAt(i + 1) == '-') {
                inLineComment = true;
                i++;
                continue;
            }
            if (c == '/' && i + 1 < len && sql.charAt(i + 1) == '*') {
                inBlockComment = true;
                i++;
                continue;
            }
            if (c == '\'') {
                inQuotes = true;
                continue;
            }

            if (c == '(') {
                depth++;
                continue;
            } else if (c == ')') {
                if (depth > 0) depth--;
                continue;
            }

            if (depth == 0 && matchKeyword(sql, i, kw)) {
                return i;
            }
        }
        return -1;
    }

    private void parseCtes(String fullSql, String withClause, int offset, QueryFlowGraph graph, int idCounter, int stepCounter) {
        Pattern ctePattern = Pattern.compile("([\\w]+)\\s+AS\\s*\\(", Pattern.CASE_INSENSITIVE);
        Matcher m = ctePattern.matcher(withClause);
        while (m.find()) {
            String cteName = m.group(1);
            QueryFlowNode cteNode = new QueryFlowNode(
                    "cte_" + (++idCounter),
                    QueryNodeType.CTE,
                    cteName,
                    "Common Table Expression"
            );
            cteNode.setStageIndex(0);
            cteNode.setStepIndex(stepCounter++);
            cteNode.setDetails("Biểu thức bảng chung (CTE): " + cteName);
            int startPos = offset + m.start();
            int endPos = offset + m.end();
            cteNode.setSqlFragment(m.group(0));
            cteNode.setSourceRange(computeSourceRange(fullSql, startPos, endPos));
            graph.addNode(cteNode);
        }
    }

    private int findEndOfWithClause(String sql, int start) {
        int depth = 0;
        int len = sql.length();
        boolean inQuotes = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;

        for (int i = start; i < len; i++) {
            char c = sql.charAt(i);
            if (inLineComment) {
                if (c == '\n' || c == '\r') inLineComment = false;
                continue;
            }
            if (inBlockComment) {
                if (c == '*' && i + 1 < len && sql.charAt(i + 1) == '/') {
                    inBlockComment = false;
                    i++;
                }
                continue;
            }
            if (inQuotes) {
                if (c == '\'') {
                    if (i + 1 < len && sql.charAt(i + 1) == '\'') {
                        i++;
                    } else {
                        inQuotes = false;
                    }
                }
                continue;
            }
            if (c == '-' && i + 1 < len && sql.charAt(i + 1) == '-') {
                inLineComment = true;
                i++;
                continue;
            }
            if (c == '/' && i + 1 < len && sql.charAt(i + 1) == '*') {
                inBlockComment = true;
                i++;
                continue;
            }
            if (c == '\'') {
                inQuotes = true;
                continue;
            }

            if (c == '(') depth++;
            else if (c == ')') depth--;
            else if (depth == 0 && matchKeyword(sql, i, "SELECT")) {
                return i;
            }
        }
        return -1;
    }

    private record ClauseSpan(String keyword, String text, int startOffset, int endOffset) {}

    private Map<String, ClauseSpan> splitClausesAtDepth0(String sql, int baseOffset) {
        Map<String, ClauseSpan> result = new LinkedHashMap<>();

        // Keywords to track at depth 0
        String[] keywords = {"SELECT", "FROM", "WHERE", "GROUP BY", "HAVING", "ORDER BY", "LIMIT"};
        List<KeywordMatch> matches = new ArrayList<>();

        int depth = 0;
        boolean inQuotes = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;

        int len = sql.length();
        for (int i = 0; i < len; i++) {
            char c = sql.charAt(i);

            // Handle comments & quotes
            if (inLineComment) {
                if (c == '\n' || c == '\r') inLineComment = false;
                continue;
            }
            if (inBlockComment) {
                if (c == '*' && i + 1 < len && sql.charAt(i + 1) == '/') {
                    inBlockComment = false;
                    i++;
                }
                continue;
            }
            if (inQuotes) {
                if (c == '\'') {
                    if (i + 1 < len && sql.charAt(i + 1) == '\'') {
                        i++; // escaped quote
                    } else {
                        inQuotes = false;
                    }
                }
                continue;
            }

            if (c == '-' && i + 1 < len && sql.charAt(i + 1) == '-') {
                inLineComment = true;
                i++;
                continue;
            }
            if (c == '/' && i + 1 < len && sql.charAt(i + 1) == '*') {
                inBlockComment = true;
                i++;
                continue;
            }
            if (c == '\'') {
                inQuotes = true;
                continue;
            }

            // Depth tracking
            if (c == '(') {
                depth++;
                continue;
            } else if (c == ')') {
                if (depth > 0) depth--;
                continue;
            }

            // Only check keywords at depth 0
            if (depth == 0) {
                for (String kw : keywords) {
                    if (matchKeyword(sql, i, kw)) {
                        matches.add(new KeywordMatch(kw, i, i + kw.length()));
                        i += kw.length() - 1;
                        break;
                    }
                }
            }
        }

        for (int i = 0; i < matches.size(); i++) {
            KeywordMatch km = matches.get(i);
            int textStart = km.end;
            int textEnd = (i + 1 < matches.size()) ? matches.get(i + 1).start : len;
            String body = sql.substring(textStart, textEnd);

            result.put(km.keyword, new ClauseSpan(
                    km.keyword,
                    body,
                    baseOffset + textStart,
                    baseOffset + textEnd
            ));
        }

        return result;
    }

    private static boolean matchKeyword(String s, int pos, String kw) {
        int kwLen = kw.length();
        if (pos + kwLen > s.length()) return false;
        if (!s.substring(pos, pos + kwLen).equalsIgnoreCase(kw)) return false;

        // Check word boundaries
        if (pos > 0 && (Character.isLetterOrDigit(s.charAt(pos - 1)) || s.charAt(pos - 1) == '_')) return false;
        if (pos + kwLen < s.length() && (Character.isLetterOrDigit(s.charAt(pos + kwLen)) || s.charAt(pos + kwLen) == '_')) return false;

        return true;
    }

    private record KeywordMatch(String keyword, int start, int end) {}

    private static class TableSource {
        String tableName = "";
        String alias = "";
        String joinType = "INNER";
        String joinCondition = "";
        boolean isSubquery = false;
        String rawFragment = "";
        int startOffset;
        int endOffset;
    }

    private List<TableSource> parseFromAndJoins(String fromText, int baseOffset) {
        List<TableSource> sources = new ArrayList<>();
        if (fromText == null || fromText.isBlank()) return sources;

        // Pattern for base table / subquery and successive JOINs
        Pattern joinPattern = Pattern.compile(
                "(?i)\\b(?:(INNER|LEFT(?:\\s+OUTER)?|RIGHT(?:\\s+OUTER)?|FULL(?:\\s+OUTER)?|CROSS|NATURAL)\\s+)?JOIN\\b|\\b(CROSS|OUTER)\\s+APPLY\\b"
        );

        Matcher m = joinPattern.matcher(fromText);
        int lastPos = 0;
        String prevJoinType = null;

        while (m.find()) {
            String chunk = fromText.substring(lastPos, m.start()).trim();
            if (!chunk.isEmpty()) {
                TableSource ts = parseSingleSource(chunk, baseOffset + lastPos, prevJoinType);
                if (ts != null) sources.add(ts);
            }

            if (m.group(1) != null) {
                prevJoinType = m.group(1).toUpperCase();
            } else if (m.group(2) != null) {
                prevJoinType = m.group(2).toUpperCase() + " APPLY";
            } else {
                prevJoinType = "INNER";
            }
            lastPos = m.end();
        }

        // Remaining chunk
        if (lastPos < fromText.length()) {
            String chunk = fromText.substring(lastPos).trim();
            if (!chunk.isEmpty()) {
                TableSource ts = parseSingleSource(chunk, baseOffset + lastPos, prevJoinType);
                if (ts != null) sources.add(ts);
            }
        }

        return sources;
    }

    private TableSource parseSingleSource(String chunk, int offset, String joinType) {
        TableSource ts = new TableSource();
        ts.startOffset = offset;
        ts.endOffset = offset + chunk.length();
        ts.rawFragment = chunk;
        if (joinType != null) {
            ts.joinType = joinType;
        }

        String remaining = chunk.trim();

        // Split ON condition if present
        int onIdx = -1;
        int depth = 0;
        for (int i = 0; i < remaining.length() - 2; i++) {
            char c = remaining.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') { if (depth > 0) depth--; }
            else if (depth == 0 && matchKeyword(remaining, i, "ON")) {
                onIdx = i;
                break;
            }
        }

        if (onIdx != -1) {
            ts.joinCondition = remaining.substring(onIdx + 2).trim();
            remaining = remaining.substring(0, onIdx).trim();
        }

        // Check if subquery: (SELECT ...) AS alias
        if (remaining.startsWith("(")) {
            ts.isSubquery = true;
            ts.tableName = "(Subquery)";
            int closeParen = remaining.lastIndexOf(')');
            if (closeParen != -1 && closeParen + 1 < remaining.length()) {
                String aliasPart = remaining.substring(closeParen + 1).trim();
                ts.alias = extractAlias(aliasPart);
            }
            return ts;
        }

        // Tokenize identifiers respecting quotes/brackets: e.g. [dbo].[my table] alias
        List<String> idents = tokenizeIdentifiers(remaining);
        if (!idents.isEmpty()) {
            ts.tableName = idents.get(0);
            if (idents.size() == 2) {
                ts.alias = extractAlias(idents.get(1));
            } else if (idents.size() >= 3 && idents.get(1).equalsIgnoreCase("AS")) {
                ts.alias = extractAlias(idents.get(2));
            }
        }

        return ts;
    }

    private static List<String> tokenizeIdentifiers(String str) {
        List<String> result = new ArrayList<>();
        int len = str.length();
        int i = 0;

        StringBuilder sb = new StringBuilder();
        boolean inBracket = false;
        boolean inDoubleQuote = false;
        boolean inBacktick = false;

        while (i < len) {
            char c = str.charAt(i);

            if (inBracket) {
                sb.append(c);
                if (c == ']') {
                    inBracket = false;
                }
                i++;
                continue;
            }
            if (inDoubleQuote) {
                sb.append(c);
                if (c == '"') {
                    inDoubleQuote = false;
                }
                i++;
                continue;
            }
            if (inBacktick) {
                sb.append(c);
                if (c == '`') {
                    inBacktick = false;
                }
                i++;
                continue;
            }

            if (c == '[') {
                inBracket = true;
                sb.append(c);
                i++;
            } else if (c == '"') {
                inDoubleQuote = true;
                sb.append(c);
                i++;
            } else if (c == '`') {
                inBacktick = true;
                sb.append(c);
                i++;
            } else if (Character.isWhitespace(c)) {
                if (sb.length() > 0) {
                    result.add(cleanCompoundIdent(sb.toString()));
                    sb.setLength(0);
                }
                i++;
            } else {
                sb.append(c);
                i++;
            }
        }

        if (sb.length() > 0) {
            result.add(cleanCompoundIdent(sb.toString()));
        }

        return result;
    }

    private static String cleanCompoundIdent(String raw) {
        if (raw == null || raw.isBlank()) return "";
        // If it's a compound identifier like [dbo].[my table] or dbo."my table"
        String[] parts = raw.split("\\.");
        if (parts.length > 1) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < parts.length; i++) {
                if (i > 0) sb.append(".");
                sb.append(cleanIdent(parts[i]));
            }
            return sb.toString();
        }
        return cleanIdent(raw);
    }

    private static String extractAlias(String candidate) {
        if (candidate == null) return "";
        String clean = cleanIdent(candidate.trim());
        if (clean.equalsIgnoreCase("AS")) return "";
        // Disallow reserved keywords from becoming alias (SV-3)
        if (RESERVED_KEYWORDS.contains(clean.toUpperCase())) {
            return "";
        }
        return clean;
    }

    private static String cleanIdent(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        if ((s.startsWith("[") && s.endsWith("]")) ||
            (s.startsWith("\"") && s.endsWith("\"")) ||
            (s.startsWith("`") && s.endsWith("`"))) {
            return s.substring(1, s.length() - 1).trim();
        }
        return s;
    }

    /**
     * Calculates horizontal pipeline stage coordinates (X: stages 0 -> 5, Y: vertically centered).
     * Compresses active stages so empty stages are omitted.
     */
    public void calculatePipelineLayout(QueryFlowGraph graph) {
        if (graph == null || graph.getNodeCount() == 0) return;

        Map<Integer, List<QueryFlowNode>> stageMap = new TreeMap<>();
        for (QueryFlowNode node : graph.getNodes().values()) {
            stageMap.computeIfAbsent(node.getStageIndex(), k -> new ArrayList<>()).add(node);
        }

        int visualColumn = 0;
        for (Map.Entry<Integer, List<QueryFlowNode>> entry : stageMap.entrySet()) {
            List<QueryFlowNode> nodes = entry.getValue();
            if (nodes.isEmpty()) continue;

            double x = ORIGIN_X + (visualColumn * STAGE_SPACING_X);
            double totalHeight = (nodes.size() - 1) * NODE_SPACING_Y;
            double startY = ORIGIN_Y - (totalHeight / 2.0);

            for (int i = 0; i < nodes.size(); i++) {
                QueryFlowNode node = nodes.get(i);
                double y = startY + (i * NODE_SPACING_Y);
                node.setX(x);
                node.setY(y);
            }
            visualColumn++;
        }
    }

    /**
     * Computes 1-based SourceRange from exact offsets in fullSql, correctly accounting for CRLF and LF (SV-4).
     */
    public static SourceRange computeSourceRange(String fullSql, int startOffset, int endOffset) {
        if (fullSql == null || fullSql.isEmpty()) {
            return new SourceRange();
        }

        int safeStart = Math.max(0, Math.min(startOffset, fullSql.length()));
        int safeEnd = Math.max(safeStart, Math.min(endOffset, fullSql.length()));

        int line = 1;
        int col = 1;
        int startLine = 1, startCol = 1;
        int endLine = 1, endCol = 1;

        for (int i = 0; i < fullSql.length(); i++) {
            if (i == safeStart) {
                startLine = line;
                startCol = col;
            }
            if (i == safeEnd) {
                endLine = line;
                endCol = col;
                break;
            }

            char c = fullSql.charAt(i);
            if (c == '\r') {
                if (i + 1 < fullSql.length() && fullSql.charAt(i + 1) == '\n') {
                    // CRLF
                    i++;
                }
                line++;
                col = 1;
            } else if (c == '\n') {
                line++;
                col = 1;
            } else {
                col++;
            }
        }

        if (safeEnd >= fullSql.length()) {
            endLine = line;
            endCol = col;
        }

        return new SourceRange(startLine, startCol, endLine, endCol, safeStart, safeEnd);
    }

    /**
     * Computes 1-based SourceRange by searching fragment in full SQL.
     */
    public static SourceRange findSourceRange(String fullSql, String fragment) {
        if (fullSql == null || fragment == null || fragment.isBlank()) {
            return new SourceRange();
        }

        String cleanFrag = fragment.trim();
        int startOffset = fullSql.indexOf(cleanFrag);
        if (startOffset == -1) {
            startOffset = fullSql.toLowerCase().indexOf(cleanFrag.toLowerCase());
        }
        if (startOffset == -1) {
            return new SourceRange();
        }

        int endOffset = startOffset + cleanFrag.length();
        return computeSourceRange(fullSql, startOffset, endOffset);
    }

    /**
     * Finds the nearest QueryFlowNode corresponding to a given cursor offset.
     */
    public QueryFlowNode findNodeAtOffset(QueryFlowGraph graph, int offset) {
        if (graph == null) return null;

        QueryFlowNode bestMatch = null;
        int minSpan = Integer.MAX_VALUE;

        for (QueryFlowNode node : graph.getNodes().values()) {
            SourceRange range = node.getSourceRange();
            if (range != null && offset >= range.getStartOffset() && offset <= range.getEndOffset()) {
                int span = range.getEndOffset() - range.getStartOffset();
                if (span < minSpan) {
                    minSpan = span;
                    bestMatch = node;
                }
            }
        }

        return bestMatch;
    }

    /**
     * Returns preset sample SQL queries matching mockup for instant testing.
     */
    public static String getPresetSql(int presetIndex) {
        return switch (presetIndex) {
            case 1 -> """
                    SELECT TOP 50
                        c.id AS customer_id, c.name AS customer_name,
                        SUM(o.amount) AS total_revenue, COUNT(o.id) AS order_count
                    FROM dbo.customers c
                    INNER JOIN dbo.orders o ON o.customer_id = c.id
                    WHERE o.status = 'active' AND o.created_at >= '2026-01-01'
                    GROUP BY c.id, c.name
                    ORDER BY total_revenue DESC;
                    """;
            case 2 -> """
                    SELECT
                        p.product_id, p.product_name,
                        i.quantity_on_hand,
                        SUM(oi.subtotal) AS total_sold
                    FROM dbo.products p
                    INNER JOIN dbo.inventory i ON i.product_id = p.product_id
                    INNER JOIN dbo.order_items oi ON oi.product_id = p.product_id
                    WHERE i.quantity_on_hand < 100
                    GROUP BY p.product_id, p.product_name, i.quantity_on_hand
                    ORDER BY i.quantity_on_hand ASC;
                    """;
            default -> """
                    SELECT
                        o.order_id, o.order_date,
                        c.customer_name,
                        p.payment_status
                    FROM dbo.orders o
                    INNER JOIN dbo.customers c ON c.customer_id = o.customer_id
                    LEFT JOIN dbo.payments p ON p.order_id = o.order_id
                    WHERE p.payment_id IS NULL AND o.order_date >= '2026-01-01'
                    ORDER BY o.order_date DESC;
                    """;
        };
    }
}
