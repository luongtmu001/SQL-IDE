/*
 * What SQL allows at the caret: the grammar slot the engine's generic candidate lists do not know about.
 *
 * resolve() reads the tokens before the caret and answers with a rule, or null to leave the slot to the engine:
 *   only    these keywords, in this order (plus the raw candidates of the types in `keep`)
 *   boost   the engine's candidates, with these keywords first
 *   tables  tables/views as after FROM, with `first` keywords on top
 *   columns the columns of `table`
 *   expr    an expression: columns, variables, functions and very few keywords
 *   clause  regenerate the engine's candidates as if the clause were `clause`
 *   none    nothing is valid (a name the user makes up)
 *
 * `auto: true`: the next word is predictable, so the popup may open by itself after a space (nothing preselected).
 */
(function (window) {
    "use strict";

    const words = (s) => s.split("|");

    // ------------------------------------------------------------------ keyword lists
    const STATEMENTS = {
        sqlserver: words("SELECT|INSERT INTO|UPDATE|DELETE FROM|WITH|DECLARE|SET|EXEC|IF|BEGIN|BEGIN TRY|BEGIN TRANSACTION|COMMIT|ROLLBACK|WHILE|RETURN|PRINT|MERGE|CREATE|ALTER|DROP|TRUNCATE TABLE|USE|GO|THROW|RAISERROR|WAITFOR|GRANT|REVOKE|DENY|BACKUP|RESTORE|DBCC|OPEN|FETCH|CLOSE|DEALLOCATE|BREAK|CONTINUE|GOTO|ELSE|END|END TRY|BEGIN CATCH|END CATCH"),
        postgresql: words("SELECT|INSERT INTO|UPDATE|DELETE FROM|WITH|DECLARE|SET|CALL|IF|BEGIN|COMMIT|ROLLBACK|SAVEPOINT|RETURN|PERFORM|RAISE|EXECUTE|DO|MERGE|CREATE|ALTER|DROP|TRUNCATE TABLE|EXPLAIN|ANALYZE|VACUUM|COPY|GRANT|REVOKE|SHOW|RESET|LOCK|LISTEN|NOTIFY|LOOP|FOR|FOREACH|WHILE|EXIT|CONTINUE|GET DIAGNOSTICS|ELSE|ELSIF|ELSEIF|END|END IF|END LOOP|EXCEPTION|WHEN"),
        sqlite: words("SELECT|INSERT INTO|REPLACE INTO|UPDATE|DELETE FROM|WITH|BEGIN|COMMIT|ROLLBACK|SAVEPOINT|CREATE|ALTER|DROP|EXPLAIN|PRAGMA|VACUUM|ATTACH|DETACH|ANALYZE"),
        common: words("SELECT|INSERT INTO|UPDATE|DELETE FROM|WITH|CREATE|ALTER|DROP|BEGIN|COMMIT|ROLLBACK")
    };
    const SET_ON_OFF = words("NOCOUNT|XACT_ABORT|ANSI_NULLS|QUOTED_IDENTIFIER|ANSI_WARNINGS|ANSI_PADDING|ARITHABORT|CONCAT_NULL_YIELDS_NULL|NUMERIC_ROUNDABORT|IDENTITY_INSERT|STATISTICS|SHOWPLAN_ALL|SHOWPLAN_TEXT|SHOWPLAN_XML|NOEXEC|PARSEONLY|FMTONLY|IMPLICIT_TRANSACTIONS");
    const SET_OPTIONS = {
        sqlserver: words("NOCOUNT|XACT_ABORT|TRANSACTION ISOLATION LEVEL|ANSI_NULLS|QUOTED_IDENTIFIER|LOCK_TIMEOUT|ROWCOUNT|DEADLOCK_PRIORITY|IDENTITY_INSERT|STATISTICS IO|STATISTICS TIME|STATISTICS XML|ANSI_WARNINGS|ANSI_PADDING|ARITHABORT|CONCAT_NULL_YIELDS_NULL|LANGUAGE|DATEFORMAT|DATEFIRST|TEXTSIZE|CONTEXT_INFO|IMPLICIT_TRANSACTIONS|NOEXEC|PARSEONLY|FMTONLY|SHOWPLAN_ALL|SHOWPLAN_TEXT|SHOWPLAN_XML"),
        postgresql: words("search_path|LOCAL|SESSION|TIME ZONE|TRANSACTION|ROLE|client_encoding|statement_timeout|lock_timeout|work_mem|client_min_messages|application_name|CONSTRAINTS"),
        sqlite: [], common: []
    };
    const ISOLATION = words("READ UNCOMMITTED|READ COMMITTED|REPEATABLE READ|SNAPSHOT|SERIALIZABLE");
    const CREATE_OBJECTS = {
        sqlserver: words("TABLE|VIEW|PROCEDURE|FUNCTION|TRIGGER|INDEX|OR ALTER|UNIQUE|CLUSTERED|NONCLUSTERED|SCHEMA|DATABASE|SEQUENCE|SYNONYM|TYPE|LOGIN|USER|ROLE|STATISTICS"),
        postgresql: words("TABLE|VIEW|FUNCTION|PROCEDURE|TRIGGER|INDEX|OR REPLACE|UNIQUE|TEMP TABLE|TEMPORARY TABLE|UNLOGGED TABLE|MATERIALIZED VIEW|SCHEMA|DATABASE|SEQUENCE|TYPE|DOMAIN|EXTENSION|ROLE|USER|AGGREGATE|POLICY|RULE"),
        sqlite: words("TABLE|VIEW|INDEX|UNIQUE INDEX|TRIGGER|TEMP TABLE|VIRTUAL TABLE"),
        common: words("TABLE|VIEW|INDEX|PROCEDURE|FUNCTION|TRIGGER|SCHEMA|DATABASE")
    };
    const ALTER_OBJECTS = {
        sqlserver: words("TABLE|VIEW|PROCEDURE|FUNCTION|TRIGGER|INDEX|DATABASE|SCHEMA|SEQUENCE|ROLE|USER|LOGIN|AUTHORIZATION"),
        postgresql: words("TABLE|VIEW|FUNCTION|PROCEDURE|TRIGGER|INDEX|DATABASE|SCHEMA|SEQUENCE|TYPE|DOMAIN|ROLE|USER|MATERIALIZED VIEW|EXTENSION"),
        sqlite: words("TABLE"),
        common: words("TABLE|VIEW|INDEX|PROCEDURE|FUNCTION|TRIGGER|SCHEMA|DATABASE")
    };
    const DROP_OBJECTS = {
        sqlserver: words("TABLE|VIEW|PROCEDURE|FUNCTION|TRIGGER|INDEX|DATABASE|SCHEMA|SEQUENCE|SYNONYM|TYPE|ROLE|USER|LOGIN|STATISTICS"),
        postgresql: words("TABLE|VIEW|FUNCTION|PROCEDURE|TRIGGER|INDEX|DATABASE|SCHEMA|SEQUENCE|TYPE|DOMAIN|ROLE|USER|MATERIALIZED VIEW|EXTENSION|AGGREGATE|POLICY|RULE"),
        sqlite: words("TABLE|VIEW|INDEX|TRIGGER"),
        common: words("TABLE|VIEW|INDEX|PROCEDURE|FUNCTION|TRIGGER|SCHEMA|DATABASE")
    };
    const ALTER_TABLE = {
        sqlserver: words("ADD|ALTER COLUMN|DROP COLUMN|DROP CONSTRAINT|ADD CONSTRAINT|WITH CHECK|WITH NOCHECK|CHECK CONSTRAINT|NOCHECK CONSTRAINT|ENABLE TRIGGER|DISABLE TRIGGER|SWITCH|REBUILD"),
        postgresql: words("ADD COLUMN|ALTER COLUMN|DROP COLUMN|ADD CONSTRAINT|DROP CONSTRAINT|RENAME TO|RENAME COLUMN|OWNER TO|SET SCHEMA|ENABLE TRIGGER|DISABLE TRIGGER|ATTACH PARTITION|DETACH PARTITION"),
        sqlite: words("ADD COLUMN|RENAME TO|RENAME COLUMN|DROP COLUMN"),
        common: words("ADD|ALTER COLUMN|DROP COLUMN|ADD CONSTRAINT|DROP CONSTRAINT")
    };
    const COLUMN_CONSTRAINTS = {
        sqlserver: words("NOT NULL|NULL|PRIMARY KEY|IDENTITY|DEFAULT|UNIQUE|CHECK|REFERENCES|CONSTRAINT|COLLATE|FOREIGN KEY|CLUSTERED|NONCLUSTERED"),
        postgresql: words("NOT NULL|NULL|PRIMARY KEY|DEFAULT|UNIQUE|CHECK|REFERENCES|CONSTRAINT|GENERATED ALWAYS AS IDENTITY|GENERATED BY DEFAULT AS IDENTITY|COLLATE"),
        sqlite: words("NOT NULL|NULL|PRIMARY KEY|AUTOINCREMENT|DEFAULT|UNIQUE|CHECK|REFERENCES|COLLATE"),
        common: words("NOT NULL|NULL|PRIMARY KEY|DEFAULT|UNIQUE|CHECK|REFERENCES|CONSTRAINT")
    };
    const TABLE_CONSTRAINTS = words("CONSTRAINT|PRIMARY KEY|FOREIGN KEY|UNIQUE|CHECK");
    const JOINS = {
        sqlserver: words("INNER JOIN|LEFT JOIN|RIGHT JOIN|FULL JOIN|CROSS JOIN|JOIN|CROSS APPLY|OUTER APPLY"),
        postgresql: words("INNER JOIN|LEFT JOIN|RIGHT JOIN|FULL JOIN|CROSS JOIN|JOIN|NATURAL JOIN"),
        sqlite: words("INNER JOIN|LEFT JOIN|CROSS JOIN|JOIN|NATURAL JOIN"),
        common: words("INNER JOIN|LEFT JOIN|RIGHT JOIN|FULL JOIN|CROSS JOIN|JOIN")
    };
    const QUERY_TAIL = {
        sqlserver: words("GROUP BY|ORDER BY|UNION|UNION ALL|EXCEPT|INTERSECT|OPTION"),
        postgresql: words("GROUP BY|ORDER BY|LIMIT|OFFSET|UNION|UNION ALL|EXCEPT|INTERSECT|FOR UPDATE|RETURNING"),
        sqlite: words("GROUP BY|ORDER BY|LIMIT|OFFSET|UNION|UNION ALL|EXCEPT|INTERSECT"),
        common: words("GROUP BY|ORDER BY|UNION|UNION ALL|EXCEPT|INTERSECT")
    };
    const AFTER_ORDER_BY = {
        sqlserver: words("OFFSET|OPTION|FOR XML|FOR JSON"),
        postgresql: words("LIMIT|OFFSET|FETCH FIRST|FOR UPDATE"),
        sqlite: words("LIMIT|OFFSET"),
        common: []
    };
    const DML_RETURN = { sqlserver: ["OUTPUT"], postgresql: ["RETURNING"], sqlite: ["RETURNING"], common: [] };
    const HINTS = words("RECOMPILE|MAXDOP|OPTIMIZE FOR|OPTIMIZE FOR UNKNOWN|HASH JOIN|LOOP JOIN|MERGE JOIN|FORCE ORDER|HASH GROUP|ORDER GROUP|FAST|MAXRECURSION|USE HINT|QUERYTRACEON");
    const DATE_PARTS = words("year|quarter|month|dayofyear|day|week|iso_week|weekday|hour|minute|second|millisecond|microsecond|nanosecond");
    const PG_EXTRACT = words("YEAR|QUARTER|MONTH|WEEK|DAY|DOW|DOY|HOUR|MINUTE|SECOND|EPOCH");
    const RAISE_LEVELS = words("NOTICE|EXCEPTION|WARNING|INFO|LOG|DEBUG");
    const EXPR_KEYWORDS = new Set(words("CASE|NULL|NOT|EXISTS|CAST|CONVERT|TRY_CAST|TRY_CONVERT|TRUE|FALSE|CURRENT_TIMESTAMP|CURRENT_DATE|CURRENT_USER|COALESCE|NULLIF"));

    /** Words that end a name: a table or alias position can never be one of these. */
    const CLAUSE_WORDS = new Set(words("SELECT|FROM|WHERE|GROUP|ORDER|HAVING|ON|JOIN|INNER|LEFT|RIGHT|FULL|CROSS|OUTER|APPLY|UNION|EXCEPT|INTERSECT|LIMIT|OFFSET|INSERT|INTO|VALUES|UPDATE|SET|DELETE|MERGE|USING|WHEN|THEN|ELSE|END|CASE|AND|OR|NOT|IS|IN|EXISTS|ASC|DESC|TRY|CATCH|COMMIT|ROLLBACK|LOOP|CONFLICT|NOTHING|MATCHED|LIKE|ILIKE|BETWEEN|AS|BY|CREATE|ALTER|DROP|TABLE|VIEW|INDEX|PROCEDURE|PROC|FUNCTION|TRIGGER|IF|WHILE|BEGIN|DECLARE|EXEC|EXECUTE|RETURN|PRINT|WITH|DISTINCT|TOP|ALL|NULL|OVER|PARTITION|ADD|COLUMN|CONSTRAINT|TRUNCATE|USE|GO|OPTION|RETURNING|OUTPUT|FETCH|FOR|DO|ON"));

    const STATEMENT_VERBS = new Set(words("SELECT|INSERT|UPDATE|DELETE|MERGE|CREATE|ALTER|DROP|TRUNCATE|DECLARE|EXEC|EXECUTE"));
    const STATEMENT_OPENERS = new Set(words("BEGIN|ELSE|TRY|CATCH|LOOP|GO|COMMIT|ROLLBACK"));
    const COMPARISON = new Set(words("=|<>|!=|>|<|>=|<=|!<|!>|LIKE|ILIKE|+|-|/|%|&|^|~|||+=|-="));

    function dialectKey(dbType) {
        const t = String(dbType || "").toLowerCase();
        if (t === "sqlserver" || t === "mssql") return "sqlserver";
        if (t === "postgresql" || t === "postgres" || t === "pg") return "postgresql";
        if (t === "sqlite") return "sqlite";
        return "common";
    }

    // ------------------------------------------------------------------ tokens

    let tokenizer = null;

    /**
     * Tokens before the caret's word, in their own statement. Parentheses closed before the caret collapse into a
     * single "()" and dotted names into one name, so patterns can look at the tail: FROM dbo.[Order Details] od -> FROM N N.
     */
    function readSegment(text) {
        tokenizer = tokenizer || new window.SqlTokenizer();
        const raw = tokenizer.tokenize(text).filter((t) =>
            t.type !== "WHITESPACE" && t.type !== "COMMENT_LINE" && t.type !== "COMMENT_BLOCK" && t.type !== "EOF");

        // the stack holds the output of every open parenthesis
        const stack = [{ items: [], before: "" }];
        for (const t of raw) {
            const value = String(t.value);
            const upper = value.toUpperCase();
            const top = stack[stack.length - 1];
            if (value === "(") {
                const prev = top.items.length ? top.items[top.items.length - 1] : null;
                stack.push({ items: [], before: prev ? prev.u : "", beforeName: prev ? prev.name : false });
            } else if (value === ")") {
                if (stack.length > 1) {
                    stack.pop();
                    stack[stack.length - 1].items.push({ u: "()", v: "()", name: false, value: true });
                }
            } else if (value === ";") {
                top.items.length = 0;
            } else if (upper === "GO" && stack.length === 1) {
                top.items.length = 0;
            } else if (value === "." && top.items.length && top.items[top.items.length - 1].name) {
                top.items[top.items.length - 1].dot = true;
            } else {
                const prev = top.items.length ? top.items[top.items.length - 1] : null;
                const isWord = t.type === "IDENTIFIER" || t.type === "KEYWORD" || t.type === "QUOTED_IDENTIFIER"
                    || /^[\[`"]/.test(value) || /^[A-Za-z_#]/.test(value);
                const name = isWord && !CLAUSE_WORDS.has(upper);
                if (prev && prev.dot) {           // a.b -> one name
                    prev.dot = false;
                    prev.v = prev.v + "." + value;
                    prev.u = "N";
                    prev.last = value;
                    continue;
                }
                const isValue = name || t.type === "NUMBER" || t.type === "STRING" || t.type === "PARAMETER"
                    || upper === "NULL" || upper === "*";
                top.items.push({ u: name ? "N" : upper, raw: upper, v: value, name, value: isValue, last: value });
            }
        }
        const open = stack.length > 1 ? stack[stack.length - 1] : null;
        return { seg: stack[stack.length - 1].items, open, depth: stack.length - 1, outer: stack.length > 1 ? stack[stack.length - 2].items : [] };
    }

    const K = (list, extra) => Object.assign({ kind: "only", list }, extra || {});

    /** The verb of the statement the end of `seg` belongs to, and where it is. */
    function lastVerb(seg, end) {
        for (let i = (end === undefined ? seg.length : end) - 1; i >= 0; i--) {
            if (STATEMENT_VERBS.has(seg[i].u)) return { verb: seg[i].u, at: i };
        }
        return { verb: "", at: -1 };
    }

    function lastIndexOf(seg, names, from) {
        for (let i = from === undefined ? seg.length - 1 : from; i >= 0; i--) {
            if (names.includes(seg[i].u)) return i;
        }
        return -1;
    }

    /** seg ends with `pattern`; "N" is a name, "()" a closed parenthesis, "N?" an optional name. */
    function endsWith(seg, pattern) {
        let i = seg.length - 1;
        for (let p = pattern.length - 1; p >= 0; p--) {
            const want = pattern[p];
            if (want === "N?") {
                if (i >= 0 && seg[i].u === "N") i--;
                continue;
            }
            if (i < 0) return false;
            if (Array.isArray(want) ? !want.includes(seg[i].u) : seg[i].u !== want) return false;
            i--;
        }
        return true;
    }

    /** seg is, after keyword index `at`, only `name [AS] [alias]`. Answers how many names, or -1. */
    function namesAfter(seg, at) {
        const rest = seg.slice(at + 1);
        if (rest.length === 0) return 0;
        if (rest[0].u !== "N" && rest[0].u !== "()") return -1;
        if (rest.length === 1) return 1;
        if (rest.length === 2 && rest[1].u === "N") return 2;
        if (rest.length === 2 && rest[1].u === "()") return 1;       // table function or hint
        if (rest.length === 3 && rest[1].u === "AS" && rest[2].u === "N") return 2;
        if (rest.length === 3 && rest[1].u === "N" && rest[2].u === "()") return 2;
        return -1;
    }

    /** A predicate like `a = 1`, `a IS NULL`, `a IN (...)`, `EXISTS (...)` is complete at the end of seg. */
    function predicateComplete(seg, from) {
        const n = seg.length;
        if (n - 1 < from) return false;
        const L = seg[n - 1], L2 = n >= 2 ? seg[n - 2] : null, L3 = n >= 3 ? seg[n - 3] : null;
        if (L.u === "NULL" && L2 && (L2.u === "IS" || (L2.u === "NOT" && L3 && L3.u === "IS"))) return true;
        if (L.u === "()" && L2 && (L2.u === "IN" || L2.u === "EXISTS")) return true;
        if (L.u === "()" && L2 && L2.u === "NOT") return true;
        if (!L.value || !L2) return false;
        if (COMPARISON.has(L2.u)) return true;
        // BETWEEN a AND b
        if (L2.u === "AND" && n >= 4 && seg[n - 4].u === "BETWEEN") return true;
        return false;
    }

    const isValueEnd = (t) => t && (t.value || t.u === "()" || t.u === "*");

    function hasFromAfterCaret(context) {
        const full = context.fullSql || "";
        const after = full.substring(context.cursorIndex || 0, context.statementRange ? context.statementRange.end : full.length);
        return /\bFROM\b/i.test(after);
    }

    function isCaseOpen(seg) {
        let open = 0;
        for (const t of seg) {
            if (t.u === "CASE") open++;
            else if (t.u === "END" && open > 0) open--;
        }
        return open > 0;
    }

    // ------------------------------------------------------------------ the slots

    function insideParens(state, d, context) {
        const { seg, open, outer } = state;
        const fn = open.before;
        const L = seg.length ? seg[seg.length - 1] : null;
        const fnName = open.beforeName && outer.length ? String(outer[outer.length - 1].last || "").toUpperCase() : fn;

        if (fn === "OVER") {
            if (!L) return K(words("PARTITION BY|ORDER BY"), { auto: true });
            if (L.u === "PARTITION" || L.u === "ORDER") return K(["BY"], { auto: true });
            if (L.u === "BY" || L.u === ",") return { kind: "expr" };
            const last = lastIndexOf(seg, ["PARTITION", "ORDER"]);
            if (last >= 0 && seg[last].u === "PARTITION") return K(["ORDER BY", ","]);
            if (last >= 0 && seg[last].u === "ORDER") return K(words("ASC|DESC|,|ROWS BETWEEN|RANGE BETWEEN"));
            return { kind: "expr" };
        }
        if (fn === "OPTION" && (!L || L.u === ",")) return K(HINTS, { auto: true });
        if (fn === "EXISTS" && !L) return K(["SELECT"], { auto: true });
        if ((fn === "FROM" || fn === "JOIN" || fn === "APPLY" || fn === "AS") && !L) return K(words("SELECT|WITH"), { auto: true });
        if (fn === "IN" && !L) return { kind: "expr", first: ["SELECT"] };

        if (!L && (fnName === "DATEADD" || fnName === "DATEDIFF" || fnName === "DATEDIFF_BIG" || fnName === "DATEPART" || fnName === "DATENAME" || fnName === "DATETRUNC" || fnName === "DATE_BUCKET")) {
            return K(DATE_PARTS, { auto: true });
        }
        if (!L && fnName === "EXTRACT") return K(PG_EXTRACT, { auto: true });

        // CREATE TABLE t ( ... )
        const verb = lastVerb(outer);
        if (verb.verb === "CREATE" && outer[verb.at + 1] && (outer[verb.at + 1].u === "TABLE" || outer[outer.length - 2] && outer[outer.length - 2].u === "TABLE")) {
            const lastComma = lastIndexOf(seg, [","]);
            const col = seg.slice(lastComma + 1);
            if (col.length === 0) return K(TABLE_CONSTRAINTS);
            if (col.length === 1) return null;     // the engine offers data types
            if (col[0].u === "N") return K(COLUMN_CONSTRAINTS[d], { auto: true });
            return null;
        }
        // CREATE INDEX ix ON t ( ... )
        if (verb.verb === "CREATE" && outer.length >= 2 && outer[outer.length - 2].u === "ON" && outer[outer.length - 1].u === "N") {
            return { kind: "columns", table: outer[outer.length - 1].v };
        }

        if (verb.verb === "CREATE" || verb.verb === "ALTER") return null;

        // A subquery has its own verb: the statement rules apply to it. A plain call takes an expression.
        if (context.isDataTypePosition) return null;
        if (lastVerb(seg).at < 0 && open.beforeName) return { kind: "expr", functionArgument: true };
        return "statement";
    }

    function statementSlot(state, d, context) {
        const { seg } = state;
        const word = context.currentWord || "";
        if (seg.length === 0) return startOfStatement(d, word, context);

        const n = seg.length;
        const L = seg[n - 1], L2 = n >= 2 ? seg[n - 2] : null, L3 = n >= 3 ? seg[n - 3] : null;
        const verbInfo = lastVerb(seg);
        const verb = verbInfo.verb;

        if (STATEMENT_OPENERS.has(L.u) && L.u !== "BEGIN" && L.u !== "ELSE" && !((L.u === "COMMIT" || L.u === "ROLLBACK") && n === 1)) return startOfStatement(d, word, context);

        // ---- control flow
        if (L.u === "BEGIN") {
            return K((d === "sqlserver" ? words("TRY|TRANSACTION|TRAN|CATCH") : words("TRANSACTION")).concat(STATEMENTS[d]), { keep: ["snippet"] });
        }
        if (L.u === "ELSE" && !isCaseOpen(seg)) return K(STATEMENTS[d], { keep: ["snippet", "procedure"] });
        if (L.u === "END" && !isCaseOpen(seg)) {
            return K((d === "sqlserver" ? words("TRY|CATCH") : words("IF|LOOP")).concat(STATEMENTS[d]), { keep: ["snippet"] });
        }
        if ((L.u === "COMMIT" || L.u === "ROLLBACK") && n === 1) return K(d === "sqlserver" ? words("TRANSACTION|TRAN") : words("TRANSACTION|TO SAVEPOINT"));
        if (L.u === "IF" || L.u === "WHILE") {
            if (L2 && L2.u === "DROP") return null;
            return { kind: "expr", first: words("EXISTS|NOT EXISTS|NOT") };
        }
        if (L.u === "EXISTS" && L2 && L2.u !== "IF") return K(["("], { auto: true });
        if (L.u === "RETURN" || L.u === "PRINT") return { kind: "expr" };
        if (L.u === "USE") return { kind: "clause", clause: "USE", keep: ["database"] };

        // IF <condition> <here>
        const ifAt = lastIndexOf(seg, ["IF", "WHILE"]);
        if (ifAt >= 0 && verbInfo.at < ifAt && !isCaseOpen(seg)) {
            if (d === "postgresql" && predicateComplete(seg, ifAt + 1)) return K(words("THEN|AND|OR"), { auto: true });
            if (predicateComplete(seg, ifAt + 1)) return K(words("BEGIN|AND|OR").concat(STATEMENTS[d]), { auto: true });
            if (L.u === "AND" || L.u === "OR" || COMPARISON.has(L.u)) return { kind: "expr" };
        }

        // ---- session options
        const setAt = lastIndexOf(seg, ["SET"]);
        if (setAt >= 0 && verb !== "UPDATE" && verb !== "MERGE") {
            const after = seg.slice(setAt + 1);
            if (after.length === 0) return K(SET_OPTIONS[d], { keep: ["scalar_variable"], auto: true });
            if (after.length === 1 && SET_ON_OFF.includes(String(after[0].last || after[0].u).toUpperCase())) return K(words("ON|OFF"), { auto: true });
            const text = after.map((t) => String(t.raw || t.u)).join(" ");
            if (text === "TRANSACTION") return K(d === "sqlserver" ? ["ISOLATION LEVEL"] : words("ISOLATION LEVEL|READ ONLY|READ WRITE"), { auto: true });
            if (text === "TRANSACTION ISOLATION") return K(["LEVEL"], { auto: true });
            if (text === "TRANSACTION ISOLATION LEVEL") return K(ISOLATION, { auto: true });
            if (after.length === 2 && String(after[0].raw) === "STATISTICS") return K(words("ON|OFF"), { auto: true });
            if (COMPARISON.has(L.u)) return { kind: "expr" };
            return "engine";
        }

        // ---- CASE
        if (L.u === "CASE") return { kind: "expr", first: ["WHEN"] };
        if (isCaseOpen(seg)) {
            const at = lastIndexOf(seg, ["WHEN", "THEN", "ELSE"]);
            if (at >= 0) {
                if (at === n - 1) return { kind: "expr" };
                if (isValueEnd(L) && (seg[at].u === "THEN" || seg[at].u === "ELSE" || predicateComplete(seg, at + 1))) {
                    if (seg[at].u === "WHEN") return K(words("THEN|AND|OR"), { auto: true });
                    if (seg[at].u === "THEN") return K(words("WHEN|ELSE|END"), { auto: true });
                    return K(["END"], { auto: true });
                }
                if (COMPARISON.has(L.u) || L.u === "AND" || L.u === "OR") return { kind: "expr" };
                return "engine";
            }
        }

        // ---- join words waiting for the rest
        const fromAt = lastIndexOf(seg, ["FROM", "JOIN", "APPLY"]);
        if (L.u === "GROUP" || L.u === "ORDER" || L.u === "PARTITION") return K(["BY"], { auto: true });
        if (fromAt >= 0 && verbInfo.at < fromAt + 1) {
            if (L.u === "INNER") return K(["JOIN"], { auto: true });
            if (L.u === "LEFT" || L.u === "RIGHT" || L.u === "FULL") return K(words("JOIN|OUTER JOIN"), { auto: true });
            if (L.u === "OUTER") return K(d === "sqlserver" ? words("APPLY|JOIN") : ["JOIN"], { auto: true });
            if (L.u === "CROSS") return K(d === "sqlserver" ? words("JOIN|APPLY") : ["JOIN"], { auto: true });
            if (L.u === "NATURAL") return K(["JOIN"], { auto: true });
        }
        if (L.u === "UNION") return K(words("ALL|SELECT"), { auto: true });
        if (L.u === "ALL" && L2 && L2.u === "UNION") return K(["SELECT"], { auto: true });
        if (L.u === "EXCEPT" || L.u === "INTERSECT") return K(["SELECT"], { auto: true });

        // ---- DDL
        const ddl = ddlSlot(seg, d, n, L, L2, L3, verbInfo);
        if (ddl !== undefined) return ddl;

        // ---- WITH cte
        if (L.u === "WITH" && n === 1) return { kind: "none" };
        if (seg[0].u === "WITH" && verb === "" ) {
            if (L.u === "N" && L2 && L2.u === "WITH") return K(words("AS|("), { auto: true });
            if (L.u === "AS") return K(["("], { auto: true });
            if (L.u === "()") return K(words("AS|,|SELECT"), { auto: true });
        }

        // ---- MERGE
        if (verb === "MERGE") {
            const m = mergeSlot(seg, n, L, L2, verbInfo);
            if (m !== undefined) return m;
        }

        // ---- INSERT
        if (verb === "INSERT") {
            const into = lastIndexOf(seg, ["INTO"]);
            const base = into >= 0 ? into : verbInfo.at;
            if (L.u === "VALUES") return K(["("], { auto: true });
            if (n - 1 === base + 1 && L.u === "N") return K(words("(|VALUES|SELECT|DEFAULT VALUES|EXEC").concat(DML_RETURN[d]), { auto: true });
            if (n - 1 === base + 2 && L.u === "()") return K(words("VALUES|SELECT|DEFAULT VALUES|EXEC").concat(DML_RETURN[d]), { auto: true });
            const valuesAt = lastIndexOf(seg, ["VALUES"]);
            if (valuesAt >= 0) {
                if (L.u === "()") {
                    return K(d === "postgresql" ? words(",|ON CONFLICT|RETURNING") : d === "sqlite" ? words(",|ON CONFLICT|RETURNING") : [","], { auto: true, next: true });
                }
                if (L.u === "ON") return K(["CONFLICT"], { auto: true });
                if (L.u === "CONFLICT" || (L.u === "()" && L2 && L2.u === "CONFLICT")) return K(words("DO NOTHING|DO UPDATE SET|(|ON CONSTRAINT"), { auto: true });
                if (L.u === "DO") return K(words("NOTHING|UPDATE SET"), { auto: true });
                if (L.u === "NOTHING") return K(["RETURNING"], { auto: true });
                if (L.u === "," && L2 && L2.u === "()") return K(["("], { auto: true });
            }
            const set = lastIndexOf(seg, ["SET"]);
            if (set >= 0 && set === n - 1) return "engine";
        }

        // ---- UPDATE
        if (verb === "UPDATE") {
            const at = verbInfo.at;
            const rest = n - at - 1;
            if (L.u === "UPDATE") return { kind: "clause", clause: "UPDATE", keep: "tables" };
            const setIdx = lastIndexOf(seg, ["SET"]);
            if (setIdx < at) {
                if (rest === 1 && L.u === "N") return K(words("SET|AS"), { auto: true });
                if (rest === 2 && L.u === "N") return K(["SET"], { auto: true });
                if (rest === 3 && L.u === "N" && L2.u === "AS") return K(["SET"], { auto: true });
            } else if (lastIndexOf(seg, ["WHERE", "FROM", "JOIN"]) < setIdx) {
                if (L.u === "SET" || L.u === ",") return "engine";
                if (COMPARISON.has(L.u)) return { kind: "expr", first: words("NULL|DEFAULT") };
                if (isValueEnd(L) && L2 && (COMPARISON.has(L2.u) || L2.u === "()" || L.u === "()")) {
                    return K([",", "WHERE", "FROM"].concat(DML_RETURN[d]), { auto: true, next: true });
                }
            }
        }

        // ---- SELECT list
        if (verb === "SELECT" && lastIndexOf(seg, ["FROM", "INTO"]) < verbInfo.at) {
            const at = verbInfo.at;
            const cols = n - at - 1;
            if (L.u === "TOP") return { kind: "none" };
            if (cols >= 1 && L2 && L2.u === "TOP") return "engine";      // SELECT TOP 10 <list>
            if (cols === 0 || L.u === "DISTINCT" || L.u === "," || L.u === "ALL") return "engine";
            if (L.u === "AS") return { kind: "none" };
            if (COMPARISON.has(L.u) || L.u === "AND" || L.u === "OR") return { kind: "expr" };
            if (isValueEnd(L)) {
                const hasFrom = hasFromAfterCaret(context);
                const isStar = L.u === "*";
                const list = isStar
                    ? (hasFrom ? [","] : words("FROM|INTO|,"))
                    : (hasFrom ? words("AS|,") : words("FROM|AS|,|INTO"));
                // a name typed after the expression is an alias: nothing above matches, so the popup closes
                return K(list, { auto: isStar || !L2 || L2.u !== "SELECT" ? isStar : false });
            }
        }
        if (L.u === "INTO" && verb === "SELECT") return { kind: "none" };

        // ---- DELETE
        if (L.u === "DELETE") return { kind: "tables", first: ["FROM"], auto: true };
        if (L.u === "TRUNCATE") return { kind: "tables", first: ["TABLE"], auto: true };
        if (L.u === "MERGE") return { kind: "tables", first: ["INTO"], auto: true };

        // ---- after a table in FROM / JOIN
        if (fromAt >= 0 && verbInfo.at < fromAt) {
            const count = namesAfter(seg, fromAt);
            if (count >= 1) {
                const isJoin = seg[fromAt].u === "JOIN";
                const list = isJoin
                    ? (d === "postgresql" || d === "sqlite" ? words("ON|USING|AS") : words("ON|AS|WITH"))
                    : ["WHERE"].concat(JOINS[d], count === 1 ? ["AS"] : [], d === "sqlserver" ? ["WITH"] : [], QUERY_TAIL[d], verb === "DELETE" ? DML_RETURN[d] : []);
                return K(list.filter((w, i, a) => a.indexOf(w) === i), { auto: true, next: !isJoin });
            }
            if (count === 0) {
                if (d === "postgresql" && seg[fromAt].u !== "APPLY") return { kind: "add", list: ["LATERAL"] };
                return "engine";
            }
        }
        // USING (a) after a join, or a list after ON
        const lastClause = lastIndexOf(seg, ["WHERE", "ON", "HAVING", "GROUP", "ORDER", "SET"]);
        if (lastClause > fromAt && lastClause >= 0) {
            const c = seg[lastClause].u;
            if (c === "ON" || c === "WHERE" || c === "HAVING") {
                if (L.u === "ON" || L.u === "WHERE" || L.u === "HAVING" || L.u === "AND" || L.u === "OR" || L.u === "NOT") return "engine";
                if (COMPARISON.has(L.u)) return { kind: "expr" };
                if (L.u === "BETWEEN") return { kind: "expr" };
                if (L.u === "IS") return "engine";
                if (L.u === "IN") return K(["("], { auto: true });
                if (L2 && L2.u === "BETWEEN") return K(["AND"], { auto: true });
                if (predicateComplete(seg, lastClause + 1)) {
                    const tail = c === "ON"
                        ? ["AND", "OR", "WHERE"].concat(JOINS[d], QUERY_TAIL[d])
                        : c === "WHERE"
                            ? ["AND", "OR"].concat(QUERY_TAIL[d], verb === "UPDATE" || verb === "DELETE" ? DML_RETURN[d] : [])
                            : ["AND", "OR"].concat(QUERY_TAIL[d].filter((w) => w !== "GROUP BY"));
                    return K(tail.filter((w, i, a) => a.indexOf(w) === i), { auto: true, next: c !== "ON" });
                }
                return "engine";
            }
            if (c === "GROUP") {
                if (L.u === "BY" || L.u === ",") return "engine";
                if (isValueEnd(L)) return K([","].concat(["HAVING"], QUERY_TAIL[d].filter((w) => w !== "GROUP BY"), d === "sqlserver" ? ["WITH ROLLUP", "WITH CUBE"] : words("ROLLUP|CUBE")), { auto: true, next: true });
            }
            if (c === "ORDER") {
                if (L.u === "ASC" || L.u === "DESC") return K([","].concat(AFTER_ORDER_BY[d]), { auto: true, next: true });
            }
        }

        // ---- EXEC: only its parameters and variables
        if (context.execContext && context.execContext.isParameterPosition) {
            return { kind: "keepOnly", types: ["parameter", "scalar_variable"], expression: L.u === "=" };
        }
        if (L.u === "=" && lastIndexOf(seg, ["DECLARE"]) >= 0) return { kind: "expr" };
        if (L2 && L2.u === "DECLARE" && d === "sqlserver") return { kind: "add", list: words("TABLE|CURSOR") };

        // ---- PostgreSQL statements
        if (d === "postgresql") {
            if (L.u === "RAISE" || L.v && String(L.raw) === "RAISE") return K(RAISE_LEVELS, { auto: true });
            if (String(L.raw) === "GET") return K(["DIAGNOSTICS"], { auto: true });
            if (String(L.raw) === "EXPLAIN") return K(words("ANALYZE|VERBOSE|SELECT|INSERT|UPDATE|DELETE|WITH"), { auto: true });
        }
        return "engine";
    }

    function ddlSlot(seg, d, n, L, L2, L3, verbInfo) {
        for (let i = n - 2; i >= 0; i--) {
            if (seg[i].u === "ALTER" && seg[i + 1].u === "TABLE") {
                const rest = seg.slice(i + 2);
                if (rest.length === 0) return undefined;
                if (rest.length === 1 && rest[0].u === "N") return K(ALTER_TABLE[d], { auto: true });
                if (rest[0].u !== "N") return undefined;
                const table = rest[0].v;
                if (L.u === "ADD") return K(d === "postgresql" || d === "sqlite" ? words("COLUMN|CONSTRAINT") : ["CONSTRAINT"]);
                if (L.u === "DROP") return K(words("COLUMN|CONSTRAINT"), { auto: true });
                if (L.u === "ALTER") return K(["COLUMN"], { auto: true });
                if (L.u === "COLUMN" && L2 && (L2.u === "ALTER" || L2.u === "DROP")) return { kind: "columns", table };
                if (L.u === "CONSTRAINT") return { kind: "none" };
                return "engine";
            }
        }
        if (L.u === "CREATE") return K(CREATE_OBJECTS[d], { auto: true });
        if (L.u === "ALTER" && verbInfo.at === n - 1 && !(L2 && (L2.u === "OR" || L2.u === "N"))) return K(ALTER_OBJECTS[d], { auto: true });
        if (L.u === "DROP" && verbInfo.at === n - 1) return K(DROP_OBJECTS[d], { auto: true });

        const verb = verbInfo.verb, at = verbInfo.at;
        if (verb === "CREATE") {
            if (L.u === "OR") return K(d === "sqlserver" ? ["ALTER"] : ["REPLACE"], { auto: true });
            const text = seg.slice(at + 1).map((t) => t.raw || t.u);
            let i = 0;
            if (text[0] === "OR") i = 2;
            if (i === 2 && text.length === 2) return K(words("PROCEDURE|FUNCTION|TRIGGER|VIEW"), { auto: true });
            if (text[i] === "UNIQUE" && text.length === i + 1) return K(d === "sqlserver" ? words("INDEX|CLUSTERED|NONCLUSTERED") : ["INDEX"], { auto: true });
            if ((text[i] === "CLUSTERED" || text[i] === "NONCLUSTERED") && text.length === i + 1) return K(words("INDEX"), { auto: true });
            const objectWords = ["TABLE", "VIEW", "PROCEDURE", "PROC", "FUNCTION", "TRIGGER", "SCHEMA", "DATABASE", "SEQUENCE", "SYNONYM", "TYPE", "INDEX", "LOGIN", "USER", "ROLE", "EXTENSION", "DOMAIN"];
            const objAt = lastIndexOf(seg, ["TABLE", "VIEW", "PROCEDURE", "PROC", "FUNCTION", "TRIGGER", "INDEX", "SCHEMA", "DATABASE", "SEQUENCE", "SYNONYM", "TYPE", "LOGIN", "USER", "ROLE"]);
            const objWord = objAt >= 0 ? seg[objAt].u : "";
            if (objAt === n - 1 && objectWords.includes(objWord)) return { kind: "none" };     // a name to make up
            if (L.u === "N" && objAt === n - 2) {
                if (objWord === "INDEX" || objWord === "TRIGGER") return K(["ON"], { auto: true });
                if (objWord === "TABLE") return K(d === "postgresql" ? words("(|AS|PARTITION OF") : ["("], { auto: true });
                if (objWord === "VIEW") return K(d === "sqlserver" ? words("AS|WITH") : ["AS"], { auto: true });
                if (objWord === "PROCEDURE" || objWord === "PROC") return K(d === "sqlserver" ? words("@|AS|WITH") : ["("], {});
                if (objWord === "FUNCTION") return K(["("], { auto: true });
            }
            if (L.u === "ON" && (objWord === "INDEX" || objWord === "TRIGGER")) return { kind: "tables" };
            if (objWord === "TRIGGER" && L.u === "N" && L2 && L2.u === "ON") {
                return K(d === "sqlserver" ? words("AFTER|INSTEAD OF|FOR") : words("BEFORE|AFTER|INSTEAD OF"), { auto: true });
            }
            if ((objWord === "FUNCTION" || objWord === "PROCEDURE") && L.u === "()") {
                return K(d === "sqlserver" ? words("RETURNS|AS") : words("RETURNS|LANGUAGE|AS"), { auto: true });
            }
            if (String(L.raw) === "RETURNS") return { kind: "add", list: d === "sqlserver" ? ["TABLE"] : words("TABLE|SETOF|VOID|TRIGGER") };
            if (String(L.raw) === "LANGUAGE") return K(words("plpgsql|sql"), { auto: true });
            if (L.u === "AS" && (objWord === "PROCEDURE" || objWord === "PROC" || objWord === "FUNCTION" || objWord === "TRIGGER")) return K(words("BEGIN").concat(STATEMENTS[d]), { auto: true });
            if (L.u === "AS" && objWord === "VIEW") return K(["SELECT"], { auto: true });
        }
        if (verb === "DROP") {
            const rest = seg.slice(at + 1).map((t) => t.raw || t.u);
            if (rest.length === 1) return { kind: "boost", list: ["IF EXISTS"], auto: true };
            if (rest.length === 2 && rest[1] === "IF") return K(["EXISTS"], { auto: true });
        }
        return undefined;
    }

    function mergeSlot(seg, n, L, L2, verbInfo) {
        const at = verbInfo.at;
        if (L.u === "MERGE") return undefined;
        const usingAt = lastIndexOf(seg, ["USING"]);
        if (L.u === "INTO" && at === n - 2) return { kind: "tables" };
        if (usingAt < at) {
            const rest = seg.slice(at + 1).filter((t) => t.u !== "INTO");
            if (rest.length >= 1 && rest.every((t) => t.u === "N" || t.u === "AS")) return K(words("USING|AS"), { auto: true });
            return undefined;
        }
        if (L.u === "USING") return { kind: "tables" };
        const onAt = lastIndexOf(seg, ["ON"]);
        if (onAt < usingAt) {
            if (seg.slice(usingAt + 1).every((t) => t.u === "N" || t.u === "AS" || t.u === "()")) return K(words("ON|AS"), { auto: true });
            return undefined;
        }
        if (L.u === "WHEN") return K(words("MATCHED|NOT MATCHED"), { auto: true });
        if (L.u === "MATCHED" || (L.raw === "MATCHED")) return K(words("THEN|AND"), { auto: true });
        if (L.u === "THEN") return K(words("UPDATE SET|DELETE|INSERT"), { auto: true });
        if (L.u === "NOT" && L2 && L2.u === "WHEN") return K(["MATCHED"], { auto: true });
        if (lastIndexOf(seg, ["WHEN"]) < onAt && predicateComplete(seg, onAt + 1)) return K(words("WHEN MATCHED|WHEN NOT MATCHED|AND"), { auto: true });
        return undefined;
    }

    function startOfStatement(d, word, context) {
        if (context && context.isInCatchBlock) return null;
        return K(STATEMENTS[d], { keep: word ? ["snippet", "procedure", "function"] : ["snippet"], prefixOnly: true, start: true });
    }

    // ------------------------------------------------------------------ public

    /** alias.[ : the analyzer does not see the qualifier behind an opening bracket or quote. */
    function columnsAfterQuotedQualifier(context) {
        const full = context.fullSql || "";
        const caret = typeof context.cursorIndex === "number" ? context.cursorIndex : full.length;
        const m = /(?:^|[\s,(=<>])(\[[^\]]+\]|"[^"]+"|\w+)\.[\["][^\]"]*$/.exec(full.substring(Math.max(0, caret - 300), caret));
        if (!m) return null;
        const q = m[1].replace(/^[\["]|[\]"]$/g, "");
        const map = context.aliasMap || {};
        const target = map[q.toLowerCase()];
        if (target) {
            const name = target.table || target.object || target.name;
            return { kind: "columns", table: (target.schema ? target.schema + "." : "") + name, quoted: true };
        }
        const after = full.substring(caret);
        const escaped = q.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
        const m2 = new RegExp("(\\[[^\\]]+\\]|\"[^\"]+\"|[\\w.]+)\\s+(?:AS\\s+)?" + escaped + "\\b", "i").exec(after);
        return { kind: "columns", table: m2 ? m2[1] : q, quoted: true };
    }

    /** Rule for the caret, or null when the engine's own logic applies. Never throws: a typing path. */
    function resolve(context, tabCtx) {
        try {
            if (!context || context.isInsideComment || context.isInsideString) return null;
            const quotedColumn = columnsAfterQuotedQualifier(context);
            if (quotedColumn) return quotedColumn;
            if (context.isQuotedIdentifier) return null;
            if (context.qualifier || context.triggerChar === ".") return null;
            const word = context.currentWord || "";
            if (word[0] === "@" || word[0] === "#") return null;
            const dbType = (tabCtx && tabCtx.dbType) || context.dbType;
            const d = dialectKey(dbType);
            const full = context.fullSql || "";
            const caret = typeof context.cursorIndex === "number" ? context.cursorIndex : full.length;
            if (!word && full.charAt(caret - 1) === "*") return null;
            const state = readSegment(full.substring(0, Math.max(0, caret - word.length)));

            let rule;
            if (state.depth > 0) {
                rule = insideParens(state, d, context);
                if (rule === "statement") rule = state.seg.length ? statementSlot(state, d, context) : null;
            } else {
                rule = statementSlot(state, d, context);
            }
            if (rule === "engine" || rule === undefined || rule === null) return null;
            rule.dialect = d;
            return rule;
        } catch (e) {
            if (typeof console !== "undefined") console.warn("[SqlCompletionRules] " + e);
            return null;
        }
    }

    function keywordItem(name, rank) {
        const followed = name !== "," && name !== "(" && name !== "@";
        return {
            name, label: name, type: "keyword", kind: "keyword", level: 1, displayType: "Keyword",
            insertText: name, _ruleRank: rank, followSpace: followed
        };
    }

    function markFirst(raw, list) {
        const out = list.map(keywordItem);
        const set = new Set(list.map((s) => s.toUpperCase()));
        return out.concat(raw.filter((c) => !(c.type === "keyword" && set.has(String(c.name).toUpperCase()))));
    }

    const OBJECT_TYPES = new Set(words("table|view|cte|subquery|synonym|temp_table|table_variable|table_function|schema|database|linked_server|foreign_server|pseudo_table"));

    function asExpression(raw, rule) {
        const allow = rule.functionArgument ? new Set(words("DISTINCT|ALL|CASE|NULL|NOT|CAST|CONVERT|TRY_CAST|TRY_CONVERT|TRUE|FALSE")) : EXPR_KEYWORDS;
        const out = raw.filter((c) => {
            if (c.type === "star_expansion" && !rule.functionArgument) return false;
            if (OBJECT_TYPES.has(c.type) || c.type === "procedure") return false;
            if (c.type !== "keyword" && c.type !== "syntax_operator") return true;
            return allow.has(String(c.name).toUpperCase()) || c.name === "*";
        }).map((c) => (c.type === "keyword" ? Object.assign({}, c, { isExpected: false, isFollowUp: false }) : c));
        return rule.first ? markFirst(out, rule.first) : out;
    }

    /**
     * Applies a rule. `generate(clauseOverrides)` asks the engine for its candidates, with parts of the context replaced.
     * Only asks when the rule needs them: a pure keyword slot costs no metadata.
     */
    async function apply(rule, context, generate) {
        switch (rule.kind) {
            case "none":
                return [];
            case "only": {
                const list = rule.list.map(keywordItem);
                // The statement may be finished: while a word is being typed, the next statement can start here.
                if (rule.next && context.currentWord && rule.dialect === "sqlserver") {
                    STATEMENTS.sqlserver.forEach((w) => {
                        if (!rule.list.includes(w)) list.push(keywordItem(w, list.length));
                    });
                }
                if (!rule.keep) return list;
                const raw = await generate(null);
                const keep = new Set(rule.keep);
                const word = String(context.currentWord || "").toLowerCase();
                return list.concat((raw || []).filter((c) => keep.has(c.type)
                    && (!rule.prefixOnly || c.type === "snippet" || (word && String(c.name).toLowerCase().startsWith(word)))));
            }
            case "boost":
                return markFirst(await generate(null) || [], rule.list);
            case "add":
                return (await generate(null) || []).concat(rule.list.map((w) => Object.assign(keywordItem(w, 0), { _ruleRank: undefined, level: 3 })));
            case "tables": {
                const raw = await generate({ clause: "FROM", position: "table_name", isDataTypePosition: false });
                const out = (raw || []).filter((c) => OBJECT_TYPES.has(c.type) && c.type !== "cte");
                return rule.first ? markFirst(out, rule.first) : out;
            }
            case "clause": {
                const raw = await generate({ clause: rule.clause });
                if (rule.keep === "tables") return (raw || []).filter((c) => OBJECT_TYPES.has(c.type));
                if (Array.isArray(rule.keep)) {
                    const keep = new Set(rule.keep);
                    return (raw || []).filter((c) => keep.has(c.type));
                }
                return raw || [];
            }
            case "columns": {
                const parts = String(rule.table).match(/\[[^\]]+\]|"[^"]+"|`[^`]+`|[^.]+/g) || [""];
                const table = parts[parts.length - 1].replace(/^[\["`]|[\]"`]$/g, "");
                const schema = parts.length > 1 ? parts[parts.length - 2].replace(/^[\["`]|[\]"`]$/g, "") : "";
                const raw = await generate({
                    clause: "WHERE", position: "column_name", qualifier: table, fullQualifier: rule.table,
                    queryTables: [{ table, schema, alias: "" }], aliasMap: {},
                    resolvedObject: { object: table, schema, type: "table", name: table }
                });
                return (raw || []).filter((c) => c.type === "column" || c.type === "pk_column");
            }
            case "keepOnly": {
                const keep = new Set(rule.types);
                return (await generate(null) || []).filter((c) => keep.has(c.type));
            }
            case "expr":
                return asExpression(await generate(null) || [], rule);
            default:
                return generate(null);
        }
    }

    window.SqlCompletionRules = { resolve, apply };
})(typeof window !== "undefined" ? window : globalThis);
