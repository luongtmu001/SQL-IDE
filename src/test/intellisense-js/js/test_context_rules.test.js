/**
 * What SQL allows at the caret (engine/sql-completion-rules.js), and the keyword coverage per dialect.
 *
 * Corpus fields: first (the list starts with these, in order), only (exactly these, in order), contains, absent,
 * before ([a, b]: a is above b), empty.
 */
const test = require('node:test');
const assert = require('node:assert');
const fs = require('node:fs');
const path = require('node:path');
const { createIdeContext } = require('./load.js');

const corpus = JSON.parse(fs.readFileSync(path.resolve(__dirname, '../corpus/intellisense/context_rules.json'), 'utf8'));

async function complete(sql, dialect) {
    const ide = createIdeContext({ dbType: dialect });
    const tab = { connectionId: 'conn-1', database: 'testdb', schema: dialect === 'postgresql' ? 'public' : 'dbo', dbType: dialect };
    const r = await ide.getCompletions(sql, null, tab);
    return Array.from(r || [], (c) => c.label || c.name || c.insertText);   // Array.from: arrays of this realm, the sandbox's fail deepStrictEqual
}

test('Grammar slots', async (t) => {
    for (const item of corpus) {
        const dialect = item.d || 'mssql';
        await t.test(`${dialect}: ${item.sql.replace(/\n/g, ' ')}`, async () => {
            const labels = await complete(item.sql, dialect);
            const show = labels.slice(0, 12).join(' | ');
            if (item.empty) assert.deepStrictEqual(labels, [], `expected nothing, got: ${show}`);
            if (item.only) assert.deepStrictEqual(labels, item.only, `got: ${show}`);
            if (item.first) assert.deepStrictEqual(labels.slice(0, item.first.length), item.first, `got: ${show}`);
            for (const c of item.contains || []) assert.ok(labels.includes(c), `missing ${c}, got: ${show}`);
            for (const c of item.absent || []) assert.ok(!labels.includes(c), `must not offer ${c}, got: ${show}`);
            for (const [a, b] of item.before || []) {
                assert.ok(labels.includes(a) && labels.includes(b) && labels.indexOf(a) < labels.indexOf(b), `${a} before ${b}, got: ${show}`);
            }
        });
    }
});

test('Keyword and function coverage', () => {
    const { sandbox } = createIdeContext({ dbType: 'mssql' });
    const expected = {
        sqlserver: {
            keywords: 'ADD ALTER AS BEGIN BREAK CATCH CHECK CLUSTERED COMMIT CONSTRAINT CONTINUE CREATE CROSS CURSOR DECLARE DEFAULT DELETE DISTINCT DROP ELSE END EXCEPT EXEC EXISTS FETCH FOREIGN FROM GO GOTO GRANT GROUP HAVING IDENTITY IF INDEX INNER INSERT INTERSECT INTO JOIN KEY LEFT LIKE MERGE NOLOCK NONCLUSTERED OFFSET OUTPUT OVER PARTITION PIVOT PRIMARY PRINT PROCEDURE RAISERROR REFERENCES RETURN RETURNS ROLLBACK ROWCOUNT SCHEMA SELECT SET THROW TOP TRANSACTION TRIGGER TRUNCATE TRY UNION UNIQUE UNPIVOT UPDATE USE VALUES VIEW WAITFOR WHEN WHERE WHILE WITH NOCOUNT XACT_ABORT ISOLATION LEVEL SERIALIZABLE SNAPSHOT MATCHED INCLUDE AFTER INSTEAD SCHEMABINDING'.split(' '),
            functions: 'AVG CAST CHARINDEX COALESCE CONCAT CONVERT COUNT DATALENGTH DATEADD DATEDIFF DATEPART FORMAT GETDATE ISJSON ISNULL JSON_QUERY JSON_VALUE LEN LOWER MAX MIN NEWID OPENJSON REPLACE ROUND ROW_NUMBER SCOPE_IDENTITY STRING_AGG STRING_SPLIT SUBSTRING SUM TRY_CAST TRY_CONVERT UPPER'.split(' ')
        },
        postgresql: {
            keywords: 'ALTER ANALYZE AS BEGIN CALL CASCADE COMMIT CONFLICT CREATE CUBE DECLARE DELETE DO DOMAIN DROP ELSIF ENUM EXCEPTION EXECUTE EXPLAIN FILTER FOREACH FROM GROUPING ILIKE INSERT LANGUAGE LATERAL LIMIT LOOP MATERIALIZED NOTHING NOTICE NULLS OFFSET OWNER PERFORM PLPGSQL RAISE RECURSIVE RETURNING RETURNS ROLLUP SAVEPOINT SELECT SETOF UPDATE VACUUM WINDOW WITH'.split(' '),
            functions: 'ARRAY_AGG ARRAY_LENGTH COALESCE CURRVAL DATE_TRUNC GENERATE_SERIES JSON_AGG JSONB_AGG JSONB_BUILD_OBJECT NEXTVAL NOW REGEXP_REPLACE ROW_NUMBER SETVAL STRING_AGG STRING_TO_ARRAY TO_CHAR UNNEST'.split(' ')
        }
    };
    for (const db of Object.keys(expected)) {
        const d = sandbox.SqlDialectFactory.getDialect(db);
        const keywords = new Set(d.getKeywords().map((w) => String(w).toUpperCase()));
        const functions = new Set(d.getFunctions().map((w) => String(w).toUpperCase()));
        const missKw = expected[db].keywords.filter((w) => !keywords.has(w) && !functions.has(w));
        const missFn = expected[db].functions.filter((w) => !functions.has(w) && !keywords.has(w));
        assert.deepStrictEqual(missKw, [], `${db} keywords`);
        assert.deepStrictEqual(missFn, [], `${db} functions`);
    }
});
