/**
 * completion-engine.js - SQL IntelliSense Completion Engine (Phase 6 & 8)
 * 
 * Implements:
 * - Completion Pipeline: Tokenize -> Statement -> Context -> Scope -> Aliases -> Metadata -> Generate -> Filter -> Rank -> Return (Section 21, 22)
 * - Candidate Sources: Keywords, Scope symbols, Aliases, Tables, Views, Columns, Functions, Procedures, Types, Snippets, CTEs, Variables (Section 23, 39, 40, 46, 47, 51)
 * - Context-Aware Routing: FROM/JOIN (tables, CTEs, temp tables), SELECT/WHERE/ON/ORDER BY (columns, variables), EXEC (procedures, parameters) (Section 24-29, 51)
 * - Multi-Stage Filtering: exact prefix -> prefix -> substring -> fuzzy (Section 31)
 * - Contextual Ranking: CompletionRanker with weighted contextual signals (Section 30)
 * - Request Cancellation: requestId / AbortController concurrency management (Section 33)
 * - Function Signatures & Documentation (Section 51)
 * - Level 3 Memoization: Seamless caching via AppMetadataCache
 */

(function(window) {
    'use strict';

    // ─────────────────────────────────────────────────────────────────────────────
    // 0. RESERVED SQL KEYWORDS & BUILT-IN FUNCTION SIGNATURES (Section 51 & 9.2)
    // ─────────────────────────────────────────────────────────────────────────────
    const RESERVED_KEYWORDS = new Set([
        'SELECT', 'FROM', 'WHERE', 'JOIN', 'INNER', 'LEFT', 'RIGHT', 'FULL', 'CROSS', 'OUTER',
        'ON', 'GROUP', 'HAVING', 'ORDER', 'BY', 'UNION', 'ALL', 'INTERSECT', 'EXCEPT',
        'INSERT', 'INTO', 'VALUES', 'UPDATE', 'SET', 'DELETE', 'TRUNCATE', 'MERGE',
        'CREATE', 'ALTER', 'DROP', 'TABLE', 'VIEW', 'INDEX', 'PROCEDURE', 'PROC', 'FUNCTION',
        'TRIGGER', 'SCHEMA', 'DATABASE', 'DEFAULT', 'CHECK', 'CONSTRAINT', 'PRIMARY', 'KEY',
        'FOREIGN', 'REFERENCES', 'IDENTITY', 'NULL', 'NOT', 'AND', 'OR', 'AS', 'DISTINCT',
        'TOP', 'LIMIT', 'OFFSET', 'CASE', 'WHEN', 'THEN', 'ELSE', 'END', 'OVER', 'WITH',
        'EXEC', 'EXECUTE', 'USER', 'FILE', 'COLUMN', 'DECLARE', 'ROWCOUNT', 'STATUS',
        'PERCENT', 'TIES', 'OUTPUT', 'APPLY', 'LATERAL', 'WINDOW', 'QUALIFY', 'PARTITION'
    ]);

    // ─────────────────────────────────────────────────────────────────────────────
    // 0. PARAMETER DOCUMENTATION & SIGNATURE HELP METADATA (Phase 6)
    // ─────────────────────────────────────────────────────────────────────────────
    const PARAMETER_DOCS = {
        "DATEADD": {
            "datepart": "The part of date (year, quarter, month, dayofyear, day, week, weekday, hour, minute, second, millisecond) to add to.",
            "number": "An expression that can resolve to an int that is added to a datepart of date.",
            "date": "An expression that can resolve to a time, date, smalldatetime, datetime, datetime2, or datetimeoffset value."
        },
        "DATEDIFF": {
            "datepart": "The units in which DATEDIFF reports the difference between startdate and enddate (year, month, day, hour, etc.).",
            "startdate": "The starting date expression.",
            "enddate": "The ending date expression."
        },
        "DATEPART": {
            "datepart": "The specific part of the date (year, month, day, hour, etc.) to return.",
            "date": "The date expression."
        },
        "DATENAME": {
            "datepart": "The specific part of the date (e.g. month, weekday) to return as a string.",
            "date": "The date expression."
        },
        "DATEFROMPARTS": {
            "year": "Integer expression specifying a year.",
            "month": "Integer expression specifying a month, from 1 to 12.",
            "day": "Integer expression specifying a day, from 1 to 31."
        },
        "EOMONTH": {
            "start_date": "Date expression specifying the date for which to return the last day of the month.",
            "month_to_add": "Optional integer expression specifying the number of months to add to start_date."
        },
        "SUBSTRING": {
            "expression": "A character, binary, text, ntext, or image expression.",
            "start": "An integer specifying where the returned characters start (1-based index).",
            "length": "A positive integer specifying how many characters or bytes will be returned."
        },
        "REPLACE": {
            "string_expression": "The string expression to be searched.",
            "string_pattern": "The substring to be found.",
            "string_replacement": "The replacement string."
        },
        "STUFF": {
            "character_expression": "An expression of character data to modify.",
            "start": "An integer value that specifies the location to begin deletion and insertion.",
            "length": "An integer that specifies the number of characters to delete.",
            "replacewith_expression": "An expression of character data that replaces length characters."
        },
        "CHARINDEX": {
            "expressiontofind": "The character expression containing the sequence to find.",
            "expressiontosearch": "The character expression to search.",
            "start_location": "Optional integer expression at which the search begins (1-based index)."
        },
        "PATINDEX": {
            "pattern": "A character expression that contains the sequence to find. Wildcards (% and _) can be used.",
            "expression": "An expression, usually a column, that is searched for the specified pattern."
        },
        "CONVERT": {
            "data_type": "The target data type (with optional length, precision, or scale).",
            "expression": "Any valid expression to convert.",
            "style": "Optional integer expression specifying how CONVERT will translate expression."
        },
        "CAST": {
            "expression as data_type": "Syntax: CAST(expression AS data_type)"
        },
        "ISNULL": {
            "check_expression": "The expression to be checked for NULL.",
            "replacement_value": "The expression to be returned if check_expression is NULL."
        },
        "COALESCE": {
            "expression1": "The first expression to evaluate.",
            "expression2": "The second expression to evaluate if expression1 is NULL.",
            "...": "Additional expressions evaluated in order until a non-NULL value is found."
        },
        "NULLIF": {
            "expression1": "The first expression.",
            "expression2": "The second expression. Returns NULL if both expressions are equal; otherwise returns expression1."
        },
        "IIF": {
            "boolean_expression": "The boolean expression to evaluate.",
            "true_value": "Value returned if boolean_expression evaluates to true.",
            "false_value": "Value returned if boolean_expression evaluates to false."
        },
        "CHOOSE": {
            "index": "An integer expression that represents a 1-based index into the list of items.",
            "val_1": "First value in the list.",
            "val_2": "Second value in the list."
        },
        "ROUND": {
            "numeric_expression": "An expression of the exact numeric or approximate numeric data type category.",
            "length": "The precision to which numeric_expression is to be rounded.",
            "function": "Optional integer. When 0 or omitted, rounds. When any other value, truncates."
        },
        "FLOOR": {
            "numeric_expression": "A numeric expression to round down to the largest integer."
        },
        "CEILING": {
            "numeric_expression": "A numeric expression to round up to the smallest integer."
        },
        "ABS": {
            "numeric_expression": "A numeric expression to return the absolute value for."
        },
        "CONCAT": {
            "string_value1": "The first string value to concatenate.",
            "string_value2": "The second string value to concatenate.",
            "string_valuen": "Additional string values to concatenate."
        },
        "CONCAT_WS": {
            "separator": "An expression of any character type that separates the concatenated values.",
            "argument1": "The first value to concatenate.",
            "argument2": "The second value to concatenate."
        },
        "STRING_AGG": {
            "expression": "An expression of any type. Null values are ignored.",
            "separator": "A literal or variable of character type that separates concatenated values."
        },
        "STRING_SPLIT": {
            "string": "An expression of any character type (e.g. nvarchar, varchar) to split.",
            "separator": "A single character literal used as separator for splitting."
        },
        "LEFT": {
            "character_expression": "An expression of character or binary data.",
            "integer_expression": "A positive integer specifying how many characters will be returned from the left."
        },
        "RIGHT": {
            "character_expression": "An expression of character or binary data.",
            "integer_expression": "A positive integer specifying how many characters will be returned from the right."
        },
        "TRIM": {
            "string": "A string expression from which characters will be removed."
        },
        "LTRIM": {
            "character_expression": "A character expression from which leading blanks will be removed."
        },
        "RTRIM": {
            "character_expression": "A character expression from which trailing blanks will be removed."
        },
        "UPPER": {
            "character_expression": "A character expression to convert to uppercase."
        },
        "LOWER": {
            "character_expression": "A character expression to convert to lowercase."
        },
        "LEN": {
            "string_expression": "A string expression whose length (excluding trailing spaces) will be returned."
        },
        "LENGTH": {
            "string": "A string expression whose character count will be returned."
        },
        "COUNT": {
            "expression": "The expression or column to count, or * to count all rows."
        },
        "SUM": {
            "expression": "An expression of exact or approximate numeric type to sum."
        },
        "AVG": {
            "expression": "An expression of exact or approximate numeric type to average."
        },
        "MIN": {
            "expression": "An expression whose minimum value will be returned."
        },
        "MAX": {
            "expression": "An expression whose maximum value will be returned."
        }
    };

    function parseSignatureParameters(fnName, signatureStr, docStr) {
        if (!signatureStr) return [];
        const openParen = signatureStr.indexOf('(');
        const closeParen = signatureStr.lastIndexOf(')');
        if (openParen === -1 || closeParen === -1 || closeParen <= openParen) {
            return [];
        }
        const inside = signatureStr.substring(openParen + 1, closeParen).trim();
        if (!inside) return [];

        const rawParts = inside.split(',');
        const fnDocs = PARAMETER_DOCS[fnName.toUpperCase()] || {};

        return rawParts.map((raw, idx) => {
            let clean = raw.trim().replace(/^[\[\]]+|[\[\]]+$/g, '').trim();
            clean = clean.replace(/^[\[\]]+|[\[\]]+$/g, '').trim();
            const paramKey = clean.toLowerCase().replace(/[^a-z0-9_]/g, '');
            const doc = fnDocs[paramKey] || fnDocs[clean.toLowerCase()] || `Parameter ${idx + 1} (${clean}) of ${fnName}.`;
            return {
                label: clean,
                documentation: doc
            };
        });
    }

    const FUNCTION_SIGNATURES = {
        "ISNULL": {
            signature: "ISNULL(check_expression, replacement_value)",
            documentation: "Replaces NULL with the specified replacement value."
        },
        "COALESCE": {
            signature: "COALESCE(expression1, expression2, ...)",
            documentation: "Evaluates the arguments in order and returns the current value of the first expression that initially doesn't evaluate to NULL."
        },
        "NULLIF": {
            signature: "NULLIF(expression1, expression2)",
            documentation: "Returns a null value if the two specified expressions are equal."
        },
        "DATEADD": {
            signature: "DATEADD(datepart, number, date)",
            documentation: "Returns a specified date with the specified number interval (signed integer) added to a specified datepart of that date."
        },
        "DATEDIFF": {
            signature: "DATEDIFF(datepart, startdate, enddate)",
            documentation: "Returns the count of the specified datepart boundaries crossed between the specified startdate and enddate."
        },
        "DATEPART": {
            signature: "DATEPART(datepart, date)",
            documentation: "Returns an integer that represents the specified datepart of the specified date."
        },
        "DATENAME": {
            signature: "DATENAME(datepart, date)",
            documentation: "Returns a character string that represents the specified datepart of the specified date."
        },
        "GETDATE": {
            signature: "GETDATE()",
            documentation: "Returns the current database system timestamp as a datetime value without the database time zone offset."
        },
        "NOW": {
            signature: "NOW()",
            documentation: "Returns the current date and time."
        },
        "CURRENT_TIMESTAMP": {
            signature: "CURRENT_TIMESTAMP",
            documentation: "Returns the current date and time as a TIMESTAMP value."
        },
        "SUBSTRING": {
            signature: "SUBSTRING(expression, start, length)",
            documentation: "Returns part of a character, binary, text, or image expression."
        },
        "REPLACE": {
            signature: "REPLACE(string_expression, string_pattern, string_replacement)",
            documentation: "Replaces all occurrences of a specified string value with another string value."
        },
        "TRIM": {
            signature: "TRIM([characters FROM ] string)",
            documentation: "Removes the space character OR other specified characters from the start and end of a string."
        },
        "LTRIM": {
            signature: "LTRIM(character_expression)",
            documentation: "Returns a character expression after it removes leading blanks."
        },
        "RTRIM": {
            signature: "RTRIM(character_expression)",
            documentation: "Returns a character expression after it removes trailing blanks."
        },
        "UPPER": {
            signature: "UPPER(character_expression)",
            documentation: "Returns a character expression with lowercase character data converted to uppercase."
        },
        "LOWER": {
            signature: "LOWER(character_expression)",
            documentation: "Returns a character expression with uppercase character data converted to lowercase."
        },
        "LEN": {
            signature: "LEN(string_expression)",
            documentation: "Returns the number of characters of the specified string expression, excluding trailing blanks."
        },
        "LENGTH": {
            signature: "LENGTH(string)",
            documentation: "Returns the length of a string in characters or bytes."
        },
        "CHARINDEX": {
            signature: "CHARINDEX(expressionToFind, expressionToSearch [, start_location])",
            documentation: "Searches an expression for another expression and returns its starting position if found."
        },
        "CAST": {
            signature: "CAST(expression AS data_type)",
            documentation: "Converts an expression of one data type to another."
        },
        "CONVERT": {
            signature: "CONVERT(data_type, expression [, style])",
            documentation: "Converts an expression of one data type to another."
        },
        "COUNT": {
            signature: "COUNT(expression | *)",
            documentation: "Returns the number of items found in a group."
        },
        "SUM": {
            signature: "SUM(expression)",
            documentation: "Returns the sum of all the values, or only the DISTINCT values, in the expression."
        },
        "AVG": {
            signature: "AVG(expression)",
            documentation: "Returns the average of the values in a group. It ignores null values."
        },
        "MIN": {
            signature: "MIN(expression)",
            documentation: "Returns the minimum value in the expression."
        },
        "MAX": {
            signature: "MAX(expression)",
            documentation: "Returns the maximum value in the expression."
        },
        "ROW_NUMBER": {
            signature: "ROW_NUMBER() OVER ([PARTITION BY ...] ORDER BY ...)",
            documentation: "Numbers the output of a result set starting at 1 for the first row in each partition."
        },
        "RANK": {
            signature: "RANK() OVER ([PARTITION BY ...] ORDER BY ...)",
            documentation: "Returns the rank of each row within the partition of a result set."
        },
        "DENSE_RANK": {
            signature: "DENSE_RANK() OVER ([PARTITION BY ...] ORDER BY ...)",
            documentation: "Returns the rank of rows within the partition without any gaps in the ranking."
        },
        "ROUND": {
            signature: "ROUND(numeric_expression, length [, function])",
            documentation: "Returns a numeric value, rounded to the specified length or precision."
        },
        "FLOOR": {
            signature: "FLOOR(numeric_expression)",
            documentation: "Returns the largest integer less than or equal to the specified numeric expression."
        },
        "CEILING": {
            signature: "CEILING(numeric_expression)",
            documentation: "Returns the smallest integer greater than or equal to the specified numeric expression."
        },
        "ABS": {
            signature: "ABS(numeric_expression)",
            documentation: "Returns the absolute (positive) value of the specified numeric expression."
        },
        "IIF": {
            signature: "IIF(boolean_expression, true_value, false_value)",
            documentation: "Returns one of two values, depending on whether the boolean expression evaluates to true or false."
        },
        "CONCAT": {
            signature: "CONCAT(string_value1, string_value2 [, string_valueN])",
            documentation: "Returns a string resulting from the concatenation of two or more string values."
        },
        "STRING_AGG": {
            signature: "STRING_AGG(expression, separator) [WITHIN GROUP (ORDER BY ...)]",
            documentation: "Concatenates the values of string expressions and places separator values between them."
        },
        "DATEFROMPARTS": {
            signature: "DATEFROMPARTS(year, month, day)",
            documentation: "Returns a date value for the specified year, month, and day."
        },
        "EOMONTH": {
            signature: "EOMONTH(start_date [, month_to_add])",
            documentation: "Returns the last day of the month containing a specified date, with an optional offset."
        },
        "LEFT": {
            signature: "LEFT(character_expression, integer_expression)",
            documentation: "Returns the specified number of characters from the start of a character string."
        },
        "RIGHT": {
            signature: "RIGHT(character_expression, integer_expression)",
            documentation: "Returns the specified number of characters from the end of a character string."
        },
        "STUFF": {
            signature: "STUFF(character_expression, start, length, replaceWith_expression)",
            documentation: "Deletes a specified length of characters and inserts new characters at a specified starting point."
        },
        "PATINDEX": {
            signature: "PATINDEX(pattern, expression)",
            documentation: "Returns the starting position of the first occurrence of a pattern in a specified expression."
        },
        "CONCAT_WS": {
            signature: "CONCAT_WS(separator, argument1, argument2 [, argumentN])",
            documentation: "Returns a string resulting from the concatenation of two or more string values with a separator."
        },
        "STRING_SPLIT": {
            signature: "STRING_SPLIT(string, separator)",
            documentation: "Splits a character expression into rows of substrings based on a specified separator character."
        },
        "CHOOSE": {
            signature: "CHOOSE(index, val_1, val_2 [, val_N])",
            documentation: "Returns the item at the specified index from a list of values."
        }
    };

    // Pre-calculate structured parameters for all built-in function signatures
    Object.keys(FUNCTION_SIGNATURES).forEach(fn => {
        const item = FUNCTION_SIGNATURES[fn];
        if (!item.parameters) {
            item.parameters = parseSignatureParameters(fn, item.signature, item.documentation);
        }
    });

    // ─────────────────────────────────────────────────────────────────────────────
    // 1. CANONICAL INTELLISENSE PIPELINE CLASSES (Section 30, 31, Spec RC13)
    // ─────────────────────────────────────────────────────────────────────────────
    // Resolved RC13: CandidateFilter, CompletionRanker, and CandidateGenerator are
    // canonically implemented in candidate-filter.js, completion-ranker.js, and
    // candidate-generator.js. Fallbacks are provided only in standalone environments.
    const CandidateFilter = (typeof window !== 'undefined' && window.CandidateFilter) || class CandidateFilter {
        filter(candidates) { return candidates || []; }
    };
    const CompletionRanker = (typeof window !== 'undefined' && window.CompletionRanker) || class CompletionRanker {
        rank(candidates) { return candidates || []; }
    };
    const CandidateGenerator = (typeof window !== 'undefined' && window.CandidateGenerator) || class CandidateGenerator {
        async generate() { return []; }
    };

    /**
     * CompletionEngine - Primary Completion Service (Section 21, 22)
     */
    class CompletionEngine {
        constructor(dataProvider, metadataCache) {
            this.dataProvider = dataProvider || (window.AppIntelliSense ? window.AppIntelliSense.getDataProvider() : null);
            this.metadataCache = metadataCache || window.AppMetadataCache || null;
            const GeneratorClass = (typeof window !== 'undefined' && window.CandidateGenerator) || CandidateGenerator;
            const FilterClass = (typeof window !== 'undefined' && window.CandidateFilter) || CandidateFilter;
            const RankerClass = (typeof window !== 'undefined' && window.CompletionRanker) || CompletionRanker;
            this.generator = new GeneratorClass(this.dataProvider, this.metadataCache);
            this.filter = new FilterClass();
            this.ranker = new RankerClass();
            this._currentRequestId = 0;
        }

        setDataProvider(provider) {
            this.dataProvider = provider;
            this.generator.dataProvider = provider;
        }

        setMetadataCache(cache) {
            this.metadataCache = cache;
            this.generator.metadataCache = cache;
        }

        cancelPending() {
            this._currentRequestId++;
        }

        static cleanIdentifier(name) {
            if (!name) return "";
            name = name.trim();
            if (name.length === 0) return "";

            if (name.startsWith('[')) {
                if (name.endsWith(']')) {
                    return name.substring(1, name.length - 1).replace(/\]\]/g, ']');
                }
                return name.substring(1).replace(/\]\]/g, ']');
            }

            if (name.startsWith('"')) {
                if (name.endsWith('"') && name.length >= 2) {
                    return name.substring(1, name.length - 1).replace(/""/g, '"');
                }
                return name.substring(1).replace(/""/g, '"');
            }

            if (name.startsWith('`')) {
                if (name.endsWith('`') && name.length >= 2) {
                    return name.substring(1, name.length - 1).replace(/``/g, '`');
                }
                return name.substring(1).replace(/``/g, '`');
            }

            return name;
        }

        static needsQuoting(name, dialect = 'mssql') {
            if (!name) return false;
            const clean = name.trim();
            if (clean.length === 0) return false;

            if ((clean.startsWith('[') && clean.endsWith(']')) ||
                (clean.startsWith('"') && clean.endsWith('"')) ||
                (clean.startsWith('`') && clean.endsWith('`'))) {
                return false;
            }

            const isPg = (dialect === 'postgres' || dialect === 'postgresql' || dialect === 'pg');

            // Starts with digit
            if (/^\d/.test(clean)) {
                return true;
            }

            // Non-standard characters or whitespace
            if (/[^a-zA-Z0-9_@$#]/.test(clean)) {
                return true;
            }

            // Reserved SQL keyword
            if (RESERVED_KEYWORDS.has(clean.toUpperCase())) {
                return true;
            }

            return false;
        }

        static quoteIdentifier(name, dialect = 'mssql') {
            if (!name) return "";
            const clean = name.trim();
            if ((clean.startsWith('[') && clean.endsWith(']')) ||
                (clean.startsWith('"') && clean.endsWith('"')) ||
                (clean.startsWith('`') && clean.endsWith('`'))) {
                return clean;
            }

            const isPg = (dialect === 'postgres' || dialect === 'postgresql' || dialect === 'pg');
            const isMysql = (dialect === 'mysql' || dialect === 'mariadb');

            if (isPg) {
                return `"${clean.replace(/"/g, '""')}"`;
            } else if (isMysql) {
                return `\`${clean.replace(/`/g, '``')}\``;
            } else {
                return `[${clean.replace(/\]/g, ']]')}]`;
            }
        }

        static generateTableAlias(tableName, existingAliases = new Set()) {
            if (!tableName) return 't';
            const clean = CompletionEngine.cleanIdentifier(tableName);
            // 1. Check PascalCase/camelCase with multiple capital letters (e.g. OrderDetails -> od)
            const caps = clean.match(/[A-Z]/g);
            let candidate = '';
            if (caps && caps.length >= 2) {
                candidate = caps.join('').toLowerCase();
            } else if (clean.includes('_')) {
                const parts = clean.split('_').filter(Boolean);
                if (parts.length >= 2) {
                    candidate = parts.map(p => p[0]).join('').toLowerCase();
                }
            }
            if (!candidate) {
                candidate = clean.charAt(0).toLowerCase();
            }
            if (!/^[a-z_]/i.test(candidate)) {
                candidate = 't';
            }

            const aliasSet = existingAliases instanceof Set 
                ? existingAliases 
                : new Set(Array.isArray(existingAliases) ? existingAliases.map(a => String(a).toLowerCase()) : []);

            let finalAlias = candidate;
            let counter = 2;
            while (aliasSet.has(finalAlias.toLowerCase())) {
                finalAlias = `${candidate}${counter}`;
                counter++;
            }
            return finalAlias;
        }

        static getFunctionSignature(name) {
            if (!name) return null;
            const upper = name.toUpperCase().trim();
            return FUNCTION_SIGNATURES[upper] || null;
        }

        getFunctionSignature(name) {
            return CompletionEngine.getFunctionSignature(name);
        }

        static getSignatureHelp(fullSql, cursorOffset, tabCtx) {
            if (window.SqlSignatureHelpResolver) {
                return window.SqlSignatureHelpResolver.resolve(fullSql, cursorOffset, tabCtx);
            }
            return null;
        }

        getSignatureHelp(fullSql, cursorOffset, tabCtx) {
            return CompletionEngine.getSignatureHelp(fullSql, cursorOffset, tabCtx);
        }

        static getHover(fullSql, cursorOffset, tabCtx) {
            if (window.SqlHoverResolver) {
                return window.SqlHoverResolver.resolve(fullSql, cursorOffset, tabCtx);
            }
            return null;
        }

        getHover(fullSql, cursorOffset, tabCtx) {
            return CompletionEngine.getHover(fullSql, cursorOffset, tabCtx);
        }

        static hashString(str) {
            if (!str) return '0';
            let hash = 5381;
            for (let i = 0; i < str.length; i++) {
                hash = ((hash << 5) + hash) + str.charCodeAt(i);
                hash |= 0;
            }
            return (hash >>> 0).toString(36);
        }

        static computeAliasSignature(aliasMap) {
            if (!aliasMap || typeof aliasMap !== 'object') return '';
            const keys = Object.keys(aliasMap).sort();
            return keys.map(k => {
                const val = aliasMap[k];
                const tbl = (val && (val.table || val.targetObject || val.name)) || '';
                const sch = (val && val.schema) || '';
                return `${k}:${sch}.${tbl}`;
            }).join(';');
        }

        async getCompletions(documentText, cursor, context, tabCtx) {
            if (context && !context.fullSql) {
                context.fullSql = documentText || '';
            }
            if (tabCtx) {
                if (!context.dbType && tabCtx.dbType) context.dbType = tabCtx.dbType;
                if (!context.connectionId && tabCtx.connectionId) context.connectionId = tabCtx.connectionId;
                if (!context.tabCtx) context.tabCtx = tabCtx;
            }
            const requestId = ++this._currentRequestId;
            const wordLower = (context.currentWord || '').toLowerCase();
            const alterPart = context.isAlterContext ? `ALTER_${context.alterObjectType || ''}` : '';
            const execPart = (context.execContext && context.execContext.isExec) ? 'EXEC' : '';
            const tablesKey = (context.queryTables || []).map(t => (t.table || '').toLowerCase()).sort().join(',');

            // Issue I9: Statement text hash and aliasMap signature
            const stmtText = (context.fullSql && context.statementRange)
                ? context.fullSql.substring(context.statementRange.start, context.statementRange.end)
                : (context.fullSql || '');
            const stmtHash = CompletionEngine.hashString(stmtText);
            const aliasSig = CompletionEngine.computeAliasSignature(context.aliasMap);

            const dtPart = context.isDataTypePosition ? `DT_${context.dataTypeTarget || ''}` : '';
            const tabPrefix = tabCtx && tabCtx.tabId ? `${tabCtx.tabId}::` : '';
            const scopeKey = `${tabPrefix}${context.clause || 'DEFAULT'}::${tablesKey}::${stmtHash}::${aliasSig}::${context.qualifier || ''}::${alterPart}::${execPart}::${dtPart}::${context.resolvedObject ? context.resolvedObject.name : ''}`;

            // Level 3 Completion Memoization lookup (Issue I9: attach replacementRange per call, never reuse stale offsets from cache)
            if (this.metadataCache && typeof this.metadataCache.getCompletions === 'function') {
                const cached = this.metadataCache.getCompletions(tabCtx.connectionId, tabCtx.database, tabCtx.schema, scopeKey, wordLower);
                if (cached && Array.isArray(cached) && cached.length > 0) {
                    const result = [...cached];
                    result.rawCandidates = cached.rawCandidates || cached;
                    result.replacementRange = context.replacementRange || null;
                    result.prefixRange = context.prefixRange || null;
                    result.isTruncated = Boolean(cached.isTruncated);
                    result.meta = cached.meta || null;
                    return result;
                }
            }

            // Step 1: Raw candidates. The grammar slot (sql-completion-rules.js) decides, the engine fills in what it asks for.
            const generate = (overrides) => this.generator.generate(overrides ? Object.assign({}, context, overrides) : context, tabCtx, wordLower);
            const rules = window.SqlCompletionRules;
            const rule = rules ? rules.resolve(context, tabCtx) : null;
            const rawCandidates = rule ? await rules.apply(rule, context, generate) : await generate(null);

            // Concurrency check
            if (requestId !== this._currentRequestId) {
                return null;
            }

            // Step 2: Multi-Stage Filtering (Exact -> Prefix -> Sub-Word -> Substring -> Fuzzy)
            const currentQuery = (context.currentWord || wordLower);
            const filtered = this.filter.filter(rawCandidates, currentQuery);

            // Step 3: Contextual Ranking (Clause, Scope, Match Quality, Types)
            // A slot answered "as after FROM" ranks like FROM too (objects first, then schemas, then databases).
            const rankContext = rule && (rule.kind === 'tables' || rule.kind === 'clause')
                ? Object.assign({}, context, { clause: rule.clause || 'FROM' })
                : context;
            const ranked = this.ranker.rank(filtered, rankContext, currentQuery);
            if (ranked && Array.isArray(ranked)) {
                ranked.rawCandidates = rawCandidates;
                ranked.replacementRange = context.replacementRange || null;
                ranked.prefixRange = context.prefixRange || null;
                if (rawCandidates && rawCandidates.hasError) {
                    ranked.hasError = true;
                    ranked.meta = ranked.meta || {};
                    ranked.meta.errors = rawCandidates.meta ? rawCandidates.meta.errors : [];
                }
                if (ranked.meta) {
                    ranked.isTruncated = Boolean(ranked.meta.truncated);
                }
            }

            // Step 4: Level 3 Memoization Store (Do not cache if there were metadata errors)
            const hasErrors = Boolean(ranked && (ranked.hasError || (ranked.meta && ranked.meta.errors && ranked.meta.errors.length > 0)));
            if (!hasErrors && this.metadataCache && typeof this.metadataCache.setCompletions === 'function' && ranked && ranked.length > 0) {
                this.metadataCache.setCompletions(tabCtx.connectionId, tabCtx.database, tabCtx.schema, scopeKey, wordLower, ranked);
            }

            return ranked;
        }

        async expandStar(documentText, cursorOffset, context, tabCtx) {
            if (!context) {
                context = (window.SqlContextAnalyzer && typeof window.SqlContextAnalyzer.analyze === 'function')
                    ? window.SqlContextAnalyzer.analyze(documentText, cursorOffset, (tabCtx && tabCtx.dbType) ? tabCtx.dbType : '')
                    : { clause: 'SELECT', queryTables: [] };
            }
            return this.generator.expandStarColumns(context, tabCtx, context.qualifier || null);
        }
    }

    // Hover and signature help live in hover-resolver.js and signature-help-resolver.js (loaded before this file).

    // Export singleton instance, signatures, and classes
    const defaultEngine = new CompletionEngine();
    const SigResolverClass = window.SqlSignatureHelpResolverClass;
    const HoverResolverClass = window.SqlHoverResolverClass;
    const signatureResolver = (typeof window !== 'undefined' && window.SqlSignatureHelpResolver) || (SigResolverClass ? new SigResolverClass() : null);
    const hoverResolver = (typeof window !== 'undefined' && window.SqlHoverResolver) || (HoverResolverClass ? new HoverResolverClass() : null);
    defaultEngine.signatureResolver = signatureResolver;
    defaultEngine.hoverResolver = hoverResolver;

    CompletionEngine.invalidate = function (connId, database) {
        if (typeof window !== 'undefined') {
            if (window.AppMetadataCache) {
                if (database && typeof window.AppMetadataCache.invalidateDatabase === 'function') {
                    window.AppMetadataCache.invalidateDatabase(connId, database);
                } else if (typeof window.AppMetadataCache.invalidateConnection === 'function') {
                    window.AppMetadataCache.invalidateConnection(connId);
                }
            }
            if (window.AppIntelliSense && window.AppIntelliSense.dataProvider) {
                if (database && typeof window.AppIntelliSense.dataProvider.invalidateDatabase === 'function') {
                    window.AppIntelliSense.dataProvider.invalidateDatabase(connId, database);
                } else if (typeof window.AppIntelliSense.dataProvider.invalidateConnection === 'function') {
                    window.AppIntelliSense.dataProvider.invalidateConnection(connId);
                }
            }
        }
    };

    window.SqlFunctionSignatures = FUNCTION_SIGNATURES;
    if (!window.CandidateFilter) window.CandidateFilter = CandidateFilter;
    if (!window.CompletionRanker) window.CompletionRanker = CompletionRanker;
    if (!window.CandidateGenerator) window.CandidateGenerator = CandidateGenerator;
    window.CompletionEngine = CompletionEngine;
    window.DefaultCompletionEngine = defaultEngine;
    window.SqlSignatureHelpResolver = signatureResolver;
    window.SqlHoverResolver = hoverResolver;
    window.SqlSignatureHelpResolverClass = SigResolverClass;
    window.SqlHoverResolverClass = HoverResolverClass;

})(typeof window !== 'undefined' ? window : globalThis);

