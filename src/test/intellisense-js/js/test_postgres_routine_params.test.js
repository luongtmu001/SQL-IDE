/**
 * test_postgres_routine_params.test.js
 * 
 * Verifies IntelliSense and Signature Help behaviors in PostgreSQL routine parameter declarations:
 * 1. Inside CREATE OR REPLACE FUNCTION (...) parameter list:
 *    - At parameter name position (e.g. typing p_), no functions or unqualified objects are suggested.
 *    - After parameter name (e.g. p_docdate1 ), data types (date, integer, character varying, etc.) are suggested.
 *    - With type prefix (e.g. p_docdate1 d), matching data types (date, decimal, double precision) are suggested.
 *    - After data type (e.g. p_docdate1 date ), DEFAULT / = keywords are suggested.
 *    - Multi-word types (e.g. character ) suggest varying.
 * 2. Signature Help is NOT triggered for the routine currently being declared or altered.
 * 3. Subsequent parameters after commas maintain proper context.
 * 4. PL/pgSQL DECLARE variables and CREATE TABLE columns suggest appropriate candidates.
 */

const test = require('node:test');
const assert = require('node:assert');
const { createIdeContext } = require('./load.js');

test('PostgreSQL Routine Parameter IntelliSense & Signature Help', async (t) => {
    const ide = createIdeContext({ dbType: 'postgresql' });
    const tabCtx = {
        connectionId: 'conn-1',
        database: 'testdb',
        schema: 'public',
        dbType: 'postgresql'
    };

    await t.test('1. Param naming slot: typing p_ does NOT suggest schema functions', async () => {
        const sql = 'CREATE OR REPLACE FUNCTION dbo.ufn_sales_bizdocso_payment_to_invoice(\n    p_‸';
        const comps = await ide.getCompletions(sql, null, tabCtx);
        const labels = (comps || []).map(c => c.label.toLowerCase());

        // Must not contain functions
        assert.ok(!labels.includes('iif'), 'Must not suggest iif function');
        assert.ok(!labels.includes('ufn_sales_bizdocso_payment_to_invoice'), 'Must not suggest the function itself');
        assert.ok(!labels.includes('now'), 'Must not suggest now() function');
    });

    await t.test('2. Param type slot: typing after param name suggests data types', async () => {
        const sql = 'CREATE OR REPLACE FUNCTION dbo.ufn_sales_bizdocso_payment_to_invoice(\n    p_docdate1 ‸';
        const comps = await ide.getCompletions(sql, null, tabCtx);
        assert.ok(comps && comps.length > 0, 'Must return data type completions');

        const labels = comps.map(c => c.label.toLowerCase());
        assert.ok(labels.includes('date'), 'Should suggest date');
        assert.ok(labels.includes('integer') || labels.includes('int'), 'Should suggest integer/int');
        assert.ok(labels.includes('character varying') || labels.includes('varchar'), 'Should suggest character varying/varchar');
        assert.ok(labels.includes('boolean'), 'Should suggest boolean');

        // Functions must NOT be suggested
        assert.ok(!labels.includes('iif'), 'Must not suggest functions in type position');
    });

    await t.test('3. Param type prefix: typing p_docdate1 d suggests date, decimal, double precision', async () => {
        const sql = 'CREATE OR REPLACE FUNCTION dbo.ufn_sales_bizdocso_payment_to_invoice(\n    p_docdate1 d‸';
        const comps = await ide.getCompletions(sql, null, tabCtx);
        assert.ok(comps && comps.length > 0, 'Must return completions matching d');

        const labels = comps.map(c => c.label.toLowerCase());
        assert.ok(labels.includes('date'), 'Should suggest date');
        assert.ok(labels.includes('decimal'), 'Should suggest decimal');
        assert.ok(labels.includes('double precision'), 'Should suggest double precision');

        // Non-d types should not be in the filtered list
        assert.ok(!labels.includes('integer'), 'Should not include integer when typing d');
    });

    await t.test('4. After param type: p_docdate1 date suggests DEFAULT and =', async () => {
        const sql = 'CREATE OR REPLACE FUNCTION dbo.ufn_sales_bizdocso_payment_to_invoice(\n    p_docdate1 date ‸';
        const comps = await ide.getCompletions(sql, null, tabCtx);
        const labels = (comps || []).map(c => c.label.toUpperCase());

        assert.ok(labels.includes('DEFAULT'), 'Should suggest DEFAULT');
        assert.ok(labels.includes('='), 'Should suggest =');
        assert.ok(!labels.includes('IIF'), 'Must not suggest function');
    });

    await t.test('5. Multi-word type continuation: character suggests varying', async () => {
        const sql = 'CREATE OR REPLACE FUNCTION dbo.test_func(\n    p_name character ‸';
        const comps = await ide.getCompletions(sql, null, tabCtx);
        const labels = (comps || []).map(c => c.label.toLowerCase());

        assert.ok(labels.includes('varying'), 'Should suggest varying after character');
        assert.ok(labels.includes('default'), 'Should also allow DEFAULT after character');
    });

    await t.test('6. Subsequent parameters after comma: parameter naming vs type slots', async () => {
        // Naming slot of 2nd parameter
        const sqlParam2Name = 'CREATE OR REPLACE FUNCTION dbo.my_func(\n    p_docdate1 date DEFAULT \'2024-10-01\'::date,\n    p_‸';
        const comps2Name = await ide.getCompletions(sqlParam2Name, null, tabCtx);
        const labels2Name = (comps2Name || []).map(c => c.label.toLowerCase());
        assert.ok(!labels2Name.includes('iif'), '2nd param name slot must not suggest functions');

        // Type slot of 2nd parameter
        const sqlParam2Type = 'CREATE OR REPLACE FUNCTION dbo.my_func(\n    p_docdate1 date DEFAULT \'2024-10-01\'::date,\n    p_docdate2 ‸';
        const comps2Type = await ide.getCompletions(sqlParam2Type, null, tabCtx);
        const labels2Type = (comps2Type || []).map(c => c.label.toLowerCase());
        assert.ok(labels2Type.includes('date'), '2nd param type slot must suggest date');
        assert.ok(!labels2Type.includes('iif'), '2nd param type slot must not suggest functions');
    });

    await t.test('7. Mode prefix: IN p_param suggests data types', async () => {
        const sql = 'CREATE OR REPLACE FUNCTION dbo.my_func(IN p_param ‸';
        const comps = await ide.getCompletions(sql, null, tabCtx);
        const labels = (comps || []).map(c => c.label.toLowerCase());
        assert.ok(labels.includes('integer') || labels.includes('int'), 'Should suggest data types after mode + param_name');
    });

    await t.test('8. Signature Help suppression in CREATE / ALTER routine definitions', async () => {
        const sigCreateFunc = await ide.getSignatureHelp('CREATE OR REPLACE FUNCTION dbo.ufn_sales_bizdocso_payment_to_invoice(‸');
        assert.strictEqual(sigCreateFunc, null, 'Signature help must be null for CREATE FUNCTION');

        const sigCreateProc = await ide.getSignatureHelp('CREATE PROCEDURE dbo.usp_test(‸');
        assert.strictEqual(sigCreateProc, null, 'Signature help must be null for CREATE PROCEDURE');

        const sigAlterFunc = await ide.getSignatureHelp('ALTER FUNCTION dbo.ufn_sales_bizdocso_payment_to_invoice(‸');
        assert.strictEqual(sigAlterFunc, null, 'Signature help must be null for ALTER FUNCTION');

        const sigCreateTable = await ide.getSignatureHelp('CREATE TABLE dbo.orders_new(‸');
        assert.strictEqual(sigCreateTable, null, 'Signature help must be null for CREATE TABLE');
    });

    await t.test('9. PL/pgSQL DECLARE variable suggests data types', async () => {
        const sql = 'CREATE FUNCTION dbo.calc() RETURNS void AS $$\nDECLARE\n    v_date ‸';
        const comps = await ide.getCompletions(sql, null, tabCtx);
        const labels = (comps || []).map(c => c.label.toLowerCase());
        assert.ok(labels.includes('date'), 'DECLARE variable should suggest data types');
        assert.ok(labels.includes('integer') || labels.includes('int'), 'DECLARE variable should suggest integer');
    });

    await t.test('10. CREATE TABLE column definition: column name vs data type', async () => {
        // At column name slot, functions should not be suggested
        const sqlColName = 'CREATE TABLE dbo.my_table (\n    ‸';
        const compsColName = await ide.getCompletions(sqlColName, null, tabCtx);
        const labelsColName = (compsColName || []).map(c => c.label.toLowerCase());
        assert.ok(!labelsColName.includes('iif'), 'CREATE TABLE column name slot must not suggest functions');

        // At column type slot, data types should be suggested
        const sqlColType = 'CREATE TABLE dbo.my_table (\n    id ‸';
        const compsColType = await ide.getCompletions(sqlColType, null, tabCtx);
        const labelsColType = (compsColType || []).map(c => c.label.toLowerCase());
        assert.ok(labelsColType.includes('integer') || labelsColType.includes('int') || labelsColType.includes('bigint'), 'CREATE TABLE column type slot must suggest types');
    });
    await t.test('11. PostgreSQL: ALTER TABLE in function body suggests tables, no params or variables', async () => {
        const sql = `CREATE OR REPLACE FUNCTION dbo.ufn_sales_bizdocso_payment_to_invoice(
    p_docdate1 date DEFAULT '2024-10-01'::date
)
RETURNS void
LANGUAGE plpgsql
AS $
DECLARE
    _strExec text;
BEGIN
    ALTER TABLE ‸
END;
$;`;
        const comps = await ide.getCompletions(sql, null, tabCtx);
        const labels = (comps || []).map(c => c.label);
        assert.ok(!labels.includes('_strExec'), 'Must not suggest local variable _strExec');
        assert.ok(!labels.includes('p_docdate1'), 'Must not suggest parameter p_docdate1');
        assert.ok(!labels.includes('iif'), 'Must not suggest function iif');
        assert.ok(labels.includes('Customers') || labels.includes('orders'), 'Must suggest database tables');
    });

    await t.test('12. MSSQL: ALTER TABLE in proc body suggests tables and temp tables, no @vars', async () => {
        const ideMs = createIdeContext({ dbType: 'sqlserver' });
        const tabCtxMs = { connectionId: 'conn-2', database: 'testdb', schema: 'dbo', dbType: 'sqlserver' };
        const sql = `CREATE PROCEDURE dbo.usp_test
    @p_param1 INT = 1
AS
BEGIN
    DECLARE @my_var VARCHAR(100);
    CREATE TABLE #LocalTemp (id INT);

    ALTER TABLE ‸
END;`;
        const comps = await ideMs.getCompletions(sql, null, tabCtxMs);
        const labels = (comps || []).map(c => c.label);
        assert.ok(!labels.includes('@my_var'), 'Must not suggest variable @my_var');
        assert.ok(!labels.includes('@p_param1'), 'Must not suggest parameter @p_param1');
        assert.ok(labels.includes('#LocalTemp'), 'Must suggest local temp table #LocalTemp');
        assert.ok(labels.includes('Orders'), 'Must suggest database tables');
    });
});
