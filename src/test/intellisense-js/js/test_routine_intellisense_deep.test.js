const { test } = require('node:test');
const assert = require('node:assert/strict');
const { createIdeContext } = require('./load.js');

test('IntelliSense Deep: Routine Parameter Definition', async (t) => {
    const ctx = createIdeContext({ dbType: 'postgres' });
    const tabCtx = { connectionId: 'conn-1', database: 'testdb', schema: 'public', dbType: 'postgres' };

    // 1. In routine parameter list, typing a new parameter
    const sqlPg = `CREATE OR REPLACE FUNCTION dbo.ufn_calc(
        p_docdate1 date,
        ‸
    ) RETURNS void AS $$ BEGIN END; $$;`;

    const completionsPg = await ctx.getCompletions(sqlPg, null, tabCtx);
    assert.ok(completionsPg.length > 0, 'Should return completions for routine param');

    // Must NOT contain any functions
    const funcItems = completionsPg.filter(c => c.type === 'sql_function' || c.kind === 'function');
    assert.equal(funcItems.length, 0, `Expected 0 function suggestions, got ${funcItems.length}: ${funcItems.map(f => f.label).join(', ')}`);

    // Must NOT contain query statement starters
    const badKeywords = completionsPg.filter(c => ['SELECT', 'FROM', 'WHERE', 'INSERT', 'UPDATE', 'DELETE'].includes(c.label.toUpperCase()));
    assert.equal(badKeywords.length, 0, `Expected no SELECT/FROM/WHERE keywords, got: ${badKeywords.map(k => k.label).join(', ')}`);

    // Must contain parameter mode keywords (IN, OUT, INOUT, VARIADIC)
    assert.ok(completionsPg.some(c => c.label === 'IN'), 'Should suggest IN');
    assert.ok(completionsPg.some(c => c.label === 'OUT'), 'Should suggest OUT');

    // Must contain standard data types
    assert.ok(completionsPg.some(c => c.label.toLowerCase() === 'int' || c.label.toLowerCase() === 'integer'), 'Should suggest integer data type');
    assert.ok(completionsPg.some(c => c.label.toLowerCase() === 'text' || c.label.toLowerCase() === 'varchar'), 'Should suggest text/varchar data type');
});

test('IntelliSense Deep: ALTER TABLE inside Routine Body suggests Tables, NOT Variables', async (t) => {
    const ctx = createIdeContext({ dbType: 'postgres' });
    const tabCtx = { connectionId: 'conn-1', database: 'testdb', schema: 'public', dbType: 'postgres' };

    const sql = `CREATE OR REPLACE FUNCTION dbo.ufn_process() RETURNS void AS $$
    DECLARE
        _strExec text;
        _Key_Biz varchar(4000);
    BEGIN
        ALTER TABLE ‸
    END;
    $$;`;

    const completions = await ctx.getCompletions(sql, null, tabCtx);
    assert.ok(completions.length > 0, 'Should return completions for ALTER TABLE');

    // Must NOT suggest variables
    const vars = completions.filter(c => c.type === 'scalar_variable' || c.kind === 'variable' || c.label.startsWith('_'));
    assert.equal(vars.length, 0, `Expected 0 variables in ALTER TABLE, got: ${vars.map(v => v.label).join(', ')}`);

    // Must suggest tables and schemas
    assert.ok(completions.some(c => c.type === 'table'), 'Should suggest database tables');
    assert.ok(completions.some(c => c.type === 'schema'), 'Should suggest schemas');
});

test('IntelliSense Deep: DROP TABLE IF EXISTS and TRUNCATE TABLE suggest Tables', async (t) => {
    const ctx = createIdeContext({ dbType: 'postgres' });
    const tabCtx = { connectionId: 'conn-1', database: 'testdb', schema: 'public', dbType: 'postgres' };

    const sqlDrop = `DROP TABLE IF EXISTS ‸`;
    const completionsDrop = await ctx.getCompletions(sqlDrop, null, tabCtx);
    assert.ok(completionsDrop.length > 0, 'Should suggest tables for DROP TABLE IF EXISTS');
    assert.ok(completionsDrop.some(c => c.type === 'table'), 'Should suggest tables');
    assert.equal(completionsDrop.filter(c => c.type === 'sql_function').length, 0, 'No functions in DROP TABLE');

    const sqlTrunc = `TRUNCATE TABLE ‸`;
    const completionsTrunc = await ctx.getCompletions(sqlTrunc, null, tabCtx);
    assert.ok(completionsTrunc.length > 0, 'Should suggest tables for TRUNCATE TABLE');
    assert.ok(completionsTrunc.some(c => c.type === 'table'), 'Should suggest tables');
    assert.equal(completionsTrunc.filter(c => c.type === 'sql_function').length, 0, 'No functions in TRUNCATE TABLE');
});

test('IntelliSense Deep: Multi-column ALTER TABLE ADD with ADD COLUMN', async (t) => {
    const ctx = createIdeContext({ dbType: 'postgres' });
    const tabCtx = { connectionId: 'conn-1', database: 'testdb', schema: 'public', dbType: 'postgres' };

    const sql = `
    CREATE TEMP TABLE tmp_data (id INT);
    ALTER TABLE tmp_data ADD COLUMN col_alpha TEXT, ADD COLUMN col_beta NUMERIC(18,2);
    SELECT t.‸ FROM tmp_data t;
    `;

    const completions = await ctx.getCompletions(sql, null, tabCtx);
    const colNames = completions.map(c => c.label.toLowerCase());
    assert.ok(colNames.includes('col_alpha'), 'Should suggest col_alpha added via ALTER TABLE ADD COLUMN');
    assert.ok(colNames.includes('col_beta'), 'Should suggest col_beta added via second ADD COLUMN clause');
    assert.ok(!colNames.includes('add'), 'Should NOT treat ADD as a column name');
    assert.ok(!colNames.includes('column'), 'Should NOT treat COLUMN as a column name');
});

test('IntelliSense Deep: SQL Server Multi-statement TVF return table variable', async (t) => {
    const ctx = createIdeContext({ dbType: 'mssql' });

    const sql = `
    CREATE FUNCTION dbo.ufn_GetEmployees()
    RETURNS @ResultTable TABLE (
        EmployeeID INT,
        FullName NVARCHAR(100)
    )
    AS
    BEGIN
        SELECT r.‸ FROM @ResultTable r;
        RETURN;
    END;
    `;

    const completions = await ctx.getCompletions(sql);
    const colNames = completions.map(c => c.label.toLowerCase());
    assert.ok(colNames.includes('employeeid'), 'Should resolve EmployeeID on @ResultTable');
    assert.ok(colNames.includes('fullname'), 'Should resolve FullName on @ResultTable');
});
