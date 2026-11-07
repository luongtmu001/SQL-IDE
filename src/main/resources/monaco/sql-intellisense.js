/*
 * Per-dialect keywords, functions and snippets, merged into the IntelliSense engine by intellisense/host.js.
 *
 * Java drives IntelliSense with  window.SqlIntellisense.setContext({ enabled, dialect, keywordCase, mru,
 *   detail, detailMode, connectionId, database, schema })
 *   dialect: "SQLSERVER" | "POSTGRESQL" | "SQLITE" | "OTHER";  keywordCase: "upper" | "lower" | "preserve";
 *   detailMode: "always" | "onDemand".
 */
(function () {
    "use strict";

    var ctx = {
        enabled: true, dialect: "OTHER", keywordCase: "upper", mru: true,
        detail: true, detailMode: "always", connectionId: null, database: null, schema: null
    };

    function words(s) { return s.split(/\s+/).filter(Boolean); }

    // ------------------------------------------------------------------ keywords
    var COMMON_KW = words(
        "SELECT FROM WHERE GROUP BY HAVING ORDER ASC DESC DISTINCT ALL AS ON USING JOIN INNER LEFT RIGHT FULL OUTER CROSS " +
        "UNION INTERSECT EXCEPT INSERT INTO VALUES UPDATE SET DELETE CREATE ALTER DROP TABLE VIEW INDEX UNIQUE " +
        "PRIMARY KEY FOREIGN REFERENCES CONSTRAINT DEFAULT NOT NULL AND OR IN EXISTS BETWEEN LIKE IS CASE WHEN THEN ELSE END " +
        "WITH RECURSIVE OVER PARTITION ROWS RANGE UNBOUNDED PRECEDING FOLLOWING CURRENT ROW EXISTS ANY SOME " +
        "BEGIN COMMIT ROLLBACK TRANSACTION TRUNCATE ADD COLUMN CHECK COLLATE ESCAPE LIMIT OFFSET");

    var KW = {
        SQLSERVER: words(
            "TOP PERCENT TIES OUTPUT INSERTED DELETED MERGE MATCHED TARGET SOURCE APPLY OUTER CROSS PIVOT UNPIVOT " +
            "DECLARE EXEC EXECUTE PROCEDURE PROC FUNCTION TRIGGER RETURN RETURNS GO IF ELSE WHILE BREAK CONTINUE GOTO WAITFOR DELAY " +
            "TRY CATCH THROW RAISERROR PRINT NOCOUNT XACT_ABORT IDENTITY SCOPE_IDENTITY NOLOCK READUNCOMMITTED HOLDLOCK ROWLOCK UPDLOCK " +
            "TABLOCK READPAST OPTION RECOMPILE MAXDOP OFFSET FETCH NEXT ONLY CURSOR OPEN CLOSE DEALLOCATE FETCH_STATUS " +
            "NVARCHAR VARCHAR NCHAR CHAR INT BIGINT SMALLINT TINYINT BIT DECIMAL NUMERIC FLOAT REAL MONEY DATETIME DATETIME2 DATE TIME " +
            "DATETIMEOFFSET UNIQUEIDENTIFIER VARBINARY XML MAX TABLE_VARIABLE SYNONYM SCHEMA SEQUENCE USE GRANT REVOKE DENY " +
            "OPENQUERY OPENROWSET BULK SPID ISOLATION LEVEL SNAPSHOT SERIALIZABLE COMMITTED NEXT VALUE"),
        POSTGRESQL: words(
            "RETURNING ILIKE SIMILAR LATERAL FILTER WITHIN ORDINALITY TABLESAMPLE DISTINCT ON CONFLICT DO NOTHING EXCLUDED " +
            "MATERIALIZED NOT UNLOGGED TEMP TEMPORARY SERIAL BIGSERIAL SMALLSERIAL TEXT BOOLEAN INTEGER BIGINT SMALLINT NUMERIC " +
            "VARCHAR TIMESTAMP TIMESTAMPTZ INTERVAL UUID JSON JSONB BYTEA ARRAY CAST FUNCTION PROCEDURE TRIGGER RETURNS LANGUAGE " +
            "PLPGSQL SQL VOLATILE STABLE IMMUTABLE SECURITY DEFINER INVOKER STRICT PARALLEL SAFE COST ROWS EXTENSION SCHEMA SEQUENCE " +
            "OWNED OWNER TABLESPACE VACUUM ANALYZE EXPLAIN VERBOSE BUFFERS COPY GRANT REVOKE PREPARE DEALLOCATE LISTEN NOTIFY " +
            "FETCH FIRST NEXT ONLY ROW OFFSET LIMIT FOR SHARE NOWAIT SKIP LOCKED"),
        SQLITE: words(
            "PRAGMA VACUUM AUTOINCREMENT WITHOUT ROWID GLOB REPLACE IGNORE ABORT FAIL ATTACH DETACH DATABASE EXPLAIN QUERY PLAN " +
            "INDEXED VIRTUAL RETURNING CONFLICT DO NOTHING UPSERT TEXT INTEGER REAL BLOB NUMERIC TEMP TEMPORARY"),
        OTHER: []
    };

    // 41+ PL/pgSQL keywords the Python version lacks
    var PLPGSQL_KW = words(
        "DECLARE BEGIN END EXCEPTION WHEN OTHERS THEN IF ELSIF ELSEIF ELSE LOOP WHILE FOR FOREACH IN REVERSE EXIT CONTINUE RETURN NEXT QUERY " +
        "RAISE NOTICE WARNING INFO LOG DEBUG USING ERRCODE HINT DETAIL MESSAGE ASSERT PERFORM EXECUTE GET DIAGNOSTICS STACKED ROW_COUNT " +
        "RESULT_OID PG_CONTEXT CURSOR OPEN CLOSE FETCH MOVE SCROLL NO RETURNS SETOF TABLE RECORD ROWTYPE TYPE ALIAS CONSTANT COLLATE " +
        "FOUND SQLSTATE SQLERRM STRICT SLICE ARRAY INTO CASE WHEN NULL");

    // ------------------------------------------------------------------ functions
    var FN_COMMON = words(
        "COUNT SUM AVG MIN MAX COALESCE NULLIF CAST ABS ROUND CEILING FLOOR POWER SQRT UPPER LOWER LTRIM RTRIM TRIM SUBSTRING REPLACE " +
        "LEN LENGTH CONCAT ROW_NUMBER RANK DENSE_RANK NTILE LAG LEAD FIRST_VALUE LAST_VALUE");

    var FN = {
        SQLSERVER: words(
            "GETDATE GETUTCDATE SYSDATETIME SYSUTCDATETIME CURRENT_TIMESTAMP DATEADD DATEDIFF DATEDIFF_BIG DATEPART DATENAME EOMONTH " +
            "DATEFROMPARTS YEAR MONTH DAY ISNULL IIF CHOOSE TRY_CAST CONVERT TRY_CONVERT TRY_PARSE PARSE FORMAT STRING_AGG STRING_SPLIT " +
            "STUFF CHARINDEX PATINDEX LEFT RIGHT REPLICATE REVERSE SPACE QUOTENAME CONCAT_WS NEWID NEWSEQUENTIALID SCOPE_IDENTITY " +
            "IDENT_CURRENT @@IDENTITY @@ROWCOUNT @@ERROR @@TRANCOUNT @@FETCH_STATUS @@SERVERNAME @@VERSION @@SPID DB_NAME DB_ID OBJECT_ID " +
            "OBJECT_NAME SCHEMA_NAME SCHEMA_ID USER_NAME SUSER_SNAME SYSTEM_USER ERROR_NUMBER ERROR_SEVERITY ERROR_STATE ERROR_PROCEDURE " +
            "ERROR_LINE ERROR_MESSAGE OPENJSON JSON_VALUE JSON_QUERY JSON_MODIFY ISJSON FOR_JSON ISNUMERIC ISDATE COLUMNPROPERTY " +
            "COL_NAME COL_LENGTH APP_NAME HOST_NAME TRIM LEN DATALENGTH CUME_DIST PERCENT_RANK PERCENTILE_CONT PERCENTILE_DISC"),
        POSTGRESQL: words(
            "NOW CURRENT_DATE CURRENT_TIME CURRENT_TIMESTAMP LOCALTIMESTAMP CLOCK_TIMESTAMP STATEMENT_TIMESTAMP DATE_TRUNC DATE_PART EXTRACT " +
            "AGE MAKE_DATE MAKE_TIMESTAMP TO_CHAR TO_DATE TO_TIMESTAMP TO_NUMBER STRING_AGG ARRAY_AGG JSON_AGG JSONB_AGG JSON_BUILD_OBJECT " +
            "JSONB_BUILD_OBJECT JSONB_SET JSONB_EXTRACT_PATH JSONB_ARRAY_ELEMENTS JSONB_EACH JSON_EXTRACT_PATH_TEXT UNNEST ARRAY_LENGTH " +
            "ARRAY_APPEND ARRAY_CAT ARRAY_REMOVE GENERATE_SERIES REGEXP_REPLACE REGEXP_MATCHES REGEXP_SPLIT_TO_TABLE SPLIT_PART POSITION " +
            "LEFT RIGHT LPAD RPAD INITCAP MD5 ENCODE DECODE GEN_RANDOM_UUID RANDOM SETSEED CURRVAL NEXTVAL SETVAL LASTVAL PG_SLEEP " +
            "PG_BACKEND_PID PG_TYPEOF PG_SIZE_PRETTY PG_TOTAL_RELATION_SIZE PG_RELATION_SIZE PG_GET_VIEWDEF PG_GET_FUNCTIONDEF " +
            "PG_GET_CONSTRAINTDEF PG_GET_INDEXDEF CURRENT_USER SESSION_USER CURRENT_SCHEMA CURRENT_DATABASE VERSION TO_JSON TO_JSONB " +
            "ROW_TO_JSON GREATEST LEAST BOOL_AND BOOL_OR EVERY MODE PERCENTILE_CONT PERCENTILE_DISC CUME_DIST PERCENT_RANK NTH_VALUE"),
        SQLITE: words(
            "DATE TIME DATETIME JULIANDAY STRFTIME UNIXEPOCH IFNULL IIF INSTR SUBSTR PRINTF GROUP_CONCAT TOTAL RANDOM RANDOMBLOB HEX QUOTE " +
            "TYPEOF LAST_INSERT_ROWID CHANGES TOTAL_CHANGES JSON JSON_EXTRACT JSON_OBJECT JSON_ARRAY JSON_EACH LIKELY UNLIKELY ZEROBLOB"),
        OTHER: []
    };

    // ------------------------------------------------------------------ snippets (label, insertText, detail)
    var SNIP = {
        COMMON: [
            ["sel", "SELECT ${1:*}\nFROM ${2:table}\nWHERE ${3:1 = 1}", "SELECT … FROM … WHERE"],
            ["selc", "SELECT COUNT(*)\nFROM ${1:table}", "SELECT COUNT(*)"],
            ["ins", "INSERT INTO ${1:table} (${2:columns})\nVALUES (${3:values})", "INSERT … VALUES"],
            ["upd", "UPDATE ${1:table}\nSET ${2:column} = ${3:value}\nWHERE ${4:condition}", "UPDATE … SET … WHERE"],
            ["del", "DELETE FROM ${1:table}\nWHERE ${2:condition}", "DELETE … WHERE"],
            ["cte", "WITH ${1:cte} AS (\n\tSELECT ${2:*}\n\tFROM ${3:table}\n)\nSELECT *\nFROM ${1:cte}", "Common table expression"],
            ["case", "CASE WHEN ${1:condition} THEN ${2:result}\n\tELSE ${3:other}\nEND", "CASE expression"],
            ["rn", "ROW_NUMBER() OVER (PARTITION BY ${1:col} ORDER BY ${2:col})", "ROW_NUMBER window"]
        ],
        SQLSERVER: [
            ["trycatch", "BEGIN TRY\n\t${1:-- statements}\nEND TRY\nBEGIN CATCH\n\tSELECT ERROR_NUMBER() AS ErrorNumber, ERROR_MESSAGE() AS ErrorMessage;\nEND CATCH", "TRY … CATCH"],
            ["proc", "CREATE PROCEDURE ${1:dbo.proc_name}\n\t@${2:param} ${3:INT}\nAS\nBEGIN\n\tSET NOCOUNT ON;\n\t${4:-- body}\nEND", "CREATE PROCEDURE"],
            ["seltop", "SELECT TOP (${1:100}) *\nFROM ${2:table}", "SELECT TOP"],
            ["declare", "DECLARE @${1:name} ${2:INT} = ${3:0};", "DECLARE variable"],
            ["while", "WHILE ${1:condition}\nBEGIN\n\t${2:-- body}\nEND", "WHILE loop"]
        ],
        POSTGRESQL: [
            ["func", "CREATE OR REPLACE FUNCTION ${1:schema.func_name}(${2:p_arg integer})\nRETURNS ${3:void}\nLANGUAGE plpgsql\nAS $$\nDECLARE\n\t${4:v_var integer;}\nBEGIN\n\t${5:-- body}\nEND;\n$$;", "CREATE FUNCTION (plpgsql)"],
            ["trigfunc", "CREATE OR REPLACE FUNCTION ${1:trg_func}()\nRETURNS trigger\nLANGUAGE plpgsql\nAS $$\nBEGIN\n\t${2:-- NEW / OLD}\n\tRETURN NEW;\nEND;\n$$;", "Trigger function"],
            ["do", "DO $$\nDECLARE\n\t${1:v_var integer;}\nBEGIN\n\t${2:-- body}\nEND;\n$$;", "DO block"],
            ["exc", "EXCEPTION\n\tWHEN ${1:OTHERS} THEN\n\t\tRAISE NOTICE '%', SQLERRM;", "EXCEPTION handler"],
            ["raise", "RAISE ${1:NOTICE} '${2:message %}', ${3:value};", "RAISE"],
            ["forloop", "FOR ${1:rec} IN ${2:SELECT * FROM table} LOOP\n\t${3:-- body}\nEND LOOP;", "FOR … LOOP"],
            ["upsert", "INSERT INTO ${1:table} (${2:cols})\nVALUES (${3:vals})\nON CONFLICT (${4:key}) DO UPDATE SET ${5:col} = EXCLUDED.${5:col}", "INSERT … ON CONFLICT"]
        ],
        SQLITE: [
            ["pragma", "PRAGMA ${1:table_info}(${2:table});", "PRAGMA"]
        ],
        OTHER: []
    };

    // ------------------------------------------------------------------ helpers
    function uniq(list) {
        var seen = Object.create(null), out = [];
        for (var i = 0; i < list.length; i++) {
            if (!seen[list[i]]) { seen[list[i]] = 1; out.push(list[i]); }
        }
        return out;
    }

    var cache = {};   // per dialect: static item descriptors (monaco ranges are added at request time)

    function itemsFor(dialect) {
        var d = KW[dialect] ? dialect : "OTHER";
        if (cache[d]) return cache[d];
        var kw = COMMON_KW.concat(KW[d] || []);
        if (d === "POSTGRESQL") kw = kw.concat(PLPGSQL_KW);
        var fn = FN_COMMON.concat(FN[d] || []);
        var sn = SNIP.COMMON.concat(SNIP[d] || []);
        cache[d] = { kw: uniq(kw), fn: uniq(fn), sn: sn };
        return cache[d];
    }

    window.SqlIntellisense = {
        setContext: function (c) {
            if (!c) return;
            if (typeof c.enabled === "boolean") ctx.enabled = c.enabled;
            if (c.dialect) ctx.dialect = String(c.dialect).toUpperCase();
            if (c.keywordCase) ctx.keywordCase = c.keywordCase;
            if (typeof c.mru === "boolean") ctx.mru = c.mru;
            if (typeof c.detail === "boolean") ctx.detail = c.detail;
            if (c.detailMode) ctx.detailMode = c.detailMode;
            ctx.connectionId = c.connectionId || null;
            ctx.database = c.database || null;
            ctx.schema = c.schema || null;
            if (window.editorBridge && window.editorBridge.applyIntellisenseOptions) {
                window.editorBridge.applyIntellisenseOptions(ctx);
            }
            if (window.IntelliSenseHost) window.IntelliSenseHost.contextChanged(ctx);
        },
        context: function () { return ctx; },
        _itemsFor: itemsFor
    };
})();
