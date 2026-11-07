/**
 * tests/js/test_intellisense_edge_cases.test.js
 * 
 * Comprehensive regression tests for SQL IntelliSense edge cases:
 * - CASE ... END statement boundary truncation
 * - Comma-separated table references in FROM clause (FROM t1, t2)
 * - PostgreSQL UPDATE ... SET ... RETURNING columns
 * - Schema-qualified function calls (e.g. pg_catalog.)
 * - Cross-database 2-part and 3-part references
 * - DDL Cache invalidation for INDEX, SCHEMA, SYNONYM
 */

const test = require('node:test');
const assert = require('node:assert');
const path = require('node:path');
const { createIdeContext } = require('./load.js');

test('IntelliSense Edge Cases & Bug Fixes', async (t) => {

    await t.test('CASE WHEN statement boundary: does not truncate FROM clause', async () => {
        const ide = createIdeContext({ dbType: 'mssql' });
        const tabCtx = { connectionId: 'conn-1', database: 'testdb', schema: 'dbo', dbType: 'mssql' };
        
        // CASE WHEN with alias in condition
        const c1 = await ide.getCompletions('SELECT CASE WHEN o.‸ > 100 THEN 1 ELSE 0 END FROM dbo.Orders o', null, tabCtx);
        const labels1 = (c1 || []).map(x => x.label);
        assert.ok(labels1.includes('Total'), 'Expected Total column in CASE WHEN condition');
        assert.ok(labels1.includes('OrderID'), 'Expected OrderID column in CASE WHEN condition');

        // CASE WHEN condition without alias
        const c2 = await ide.getCompletions('SELECT CASE WHEN ‸ THEN 1 ELSE 0 END FROM dbo.Orders', null, tabCtx);
        const labels2 = (c2 || []).map(x => x.label);
        assert.ok(labels2.includes('Total'), 'Expected Total in CASE WHEN condition without alias');
    });

    await t.test('Comma-separated tables in FROM clause: resolves all aliases', async () => {
        const ide = createIdeContext({ dbType: 'mssql' });
        const tabCtx = { connectionId: 'conn-1', database: 'testdb', schema: 'dbo', dbType: 'mssql' };

        // Second table in comma join
        const c1 = await ide.getCompletions('SELECT * FROM dbo.Orders o, dbo.Customers c WHERE o.CustomerID = c.‸', null, tabCtx);
        const labels1 = (c1 || []).map(x => x.label);
        assert.ok(labels1.includes('CustomerID'), 'Expected CustomerID from second table c');
        assert.ok(labels1.includes('CustomerName'), 'Expected CustomerName from second table c');

        // First table in comma join
        const c2 = await ide.getCompletions('SELECT * FROM dbo.Orders o, dbo.Customers c WHERE o.‸', null, tabCtx);
        const labels2 = (c2 || []).map(x => x.label);
        assert.ok(labels2.includes('OrderID'), 'Expected OrderID from first table o');
    });

    await t.test('PostgreSQL UPDATE RETURNING: suggests assigned and unassigned columns', async () => {
        const ide = createIdeContext({ dbType: 'postgres' });
        const tabCtx = { connectionId: 'conn-1', database: 'testdb', schema: 'public', dbType: 'postgres' };

        const comps = await ide.getCompletions('UPDATE public.orders SET total = 100 RETURNING ‸', null, tabCtx);
        const labels = (comps || []).map(x => x.label.toLowerCase());
        assert.ok(labels.includes('id'), 'Expected id in RETURNING');
        assert.ok(labels.includes('customer_id'), 'Expected customer_id in RETURNING');
        assert.ok(labels.includes('total'), 'Expected total in RETURNING clause even if assigned in SET');
    });

    await t.test('PostgreSQL schema-qualified functions & objects (pg_catalog.)', async () => {
        const ide = createIdeContext({ dbType: 'postgres' });
        const tabCtx = { connectionId: 'conn-1', database: 'testdb', schema: 'public', dbType: 'postgres' };

        const comps = await ide.getCompletions('SELECT pg_catalog.‸', null, tabCtx);
        assert.ok(comps && comps.length > 0, 'Expected completions for pg_catalog schema');
        const labels = comps.map(x => x.label.toLowerCase());
        assert.ok(labels.includes('now') || labels.includes('version') || labels.includes('pg_class'), 'Expected catalog functions/tables');
    });

    await t.test('Cross-database 3-part references (Db.schema.table)', async () => {
        const ide = createIdeContext({ dbType: 'mssql' });
        const tabCtx = { connectionId: 'conn-1', database: 'testdb', schema: 'dbo', dbType: 'mssql' };

        const comps = await ide.getCompletions('SELECT * FROM OtherDb.dbo.‸', null, tabCtx);
        assert.ok(comps && comps.length > 0, 'Expected object suggestions for OtherDb.dbo.');
    });

    await t.test('Scope isolation between statements: statement 1 does not leak into statement 2', async () => {
        const ide = createIdeContext({ dbType: 'mssql' });
        const tabCtx = { connectionId: 'conn-1', database: 'testdb', schema: 'dbo', dbType: 'mssql' };

        const sql = 'SELECT o.‸ FROM dbo.Orders o;\nSELECT c.CustomerName FROM dbo.Customers c;';
        const comps = await ide.getCompletions(sql, null, tabCtx);
        const labels = (comps || []).map(x => x.label);
        assert.ok(labels.includes('OrderID'), 'Expected OrderID for o in statement 1');
        assert.ok(!labels.includes('CustomerName'), 'Customers c from statement 2 must not leak into statement 1');
    });

    await t.test('DDL Cache invalidation supports INDEX, SCHEMA, SYNONYM', () => {
        const ide = createIdeContext({ dbType: 'mssql' });
        const mc = ide.sandbox.window.AppMetadataCache;
        assert.ok(mc, 'AppMetadataCache must exist');

        // Invalidate via CREATE SCHEMA
        const res1 = mc.handleDdlExecution('conn-1', 'testdb', 'CREATE SCHEMA sales_new');
        assert.ok(res1, 'Expected handleDdlExecution to match CREATE SCHEMA');
        assert.strictEqual(res1.objType, 'SCHEMA');

        // Invalidate via CREATE INDEX
        const res2 = mc.handleDdlExecution('conn-1', 'testdb', 'CREATE INDEX idx_orders_customer ON dbo.Orders(CustomerID)');
        assert.ok(res2, 'Expected handleDdlExecution to match CREATE INDEX');
        assert.strictEqual(res2.objType, 'INDEX');

        // Invalidate via DROP SYNONYM
        const res3 = mc.handleDdlExecution('conn-1', 'testdb', 'DROP SYNONYM dbo.syn_orders');
        assert.ok(res3, 'Expected handleDdlExecution to match DROP SYNONYM');
        assert.strictEqual(res3.objType, 'SYNONYM');
    });
});
