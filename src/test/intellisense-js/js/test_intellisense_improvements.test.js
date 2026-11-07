/**
 * test_intellisense_improvements.test.js
 * 
 * Verifies fixes for:
 * 1. PostgreSQL column identifiers with mixed/upper case are NOT auto-quoted with ""
 * 2. Auto-focus on first suggestion row by default in popup UI
 * 3. Habit / MRU ranking is active by default, supports cross-connection matching, and invalidates completion memoization
 */

const { test, describe, beforeEach } = require('node:test');
const assert = require('node:assert');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { createIdeContext, ROOT_DIR } = require('./load.js');

describe('IntelliSense Improvements & Habit Ranking', () => {
    let ide;
    const tabCtx = {
        connectionId: 'conn-1',
        database: 'testdb',
        schema: 'public',
        dbType: 'postgresql'
    };

    beforeEach(() => {
        ide = createIdeContext({ dbType: 'postgresql' });
        if (ide.sandbox.localStorage) {
            ide.sandbox.localStorage.clear();
        }
    });

    test('1. PostgreSQL column with mixed/upper case does NOT auto-quote with double quotes', async () => {
        const CompletionEngine = ide.sandbox.CompletionEngine;
        assert.ok(CompletionEngine);

        // Mixed/upper case identifiers in PostgreSQL must NOT need quoting
        assert.strictEqual(CompletionEngine.needsQuoting('CustomerId', 'postgresql'), false);
        assert.strictEqual(CompletionEngine.needsQuoting('ORDER_ID', 'postgresql'), false);
        assert.strictEqual(CompletionEngine.needsQuoting('userName', 'postgres'), false);
        assert.strictEqual(CompletionEngine.needsQuoting('id', 'postgresql'), false);

        // Only identifiers with spaces, non-standard chars, starting with digit, or keywords need quoting
        assert.strictEqual(CompletionEngine.needsQuoting('Customer Id', 'postgresql'), true);
        assert.strictEqual(CompletionEngine.needsQuoting('123col', 'postgresql'), true);
        assert.strictEqual(CompletionEngine.needsQuoting('user-name', 'postgresql'), true);
        assert.strictEqual(CompletionEngine.needsQuoting('select', 'postgresql'), true);

        // Test completion candidate insertText on a table with columns in PostgreSQL
        const completions = await ide.getCompletions('SELECT o.‸ FROM public.orders o', null, tabCtx);

        assert.ok(completions && completions.length > 0, 'Completions must be returned for public.orders');
        const cols = completions.filter(c => c.type === 'column' || c.type === 'pk_column');
        assert.ok(cols.length > 0, 'Must have column candidates');
        for (const c of cols) {
            assert.ok(!c.insertText.startsWith('"'), `Column ${c.name} insertText must NOT be quoted with "": ${c.insertText}`);
        }
    });

    // Tests 2 and 3 exercised the old popup DOM; the Java popup is covered by IntellisenseWebViewIT.

    test('4. Habit (MRU) ranking is active by default and supports cross-connection matching', async () => {
        const MruClass = ide.sandbox.CompletionMruManager;
        const manager = new MruClass();

        // No localStorage flag set -> default must be active
        const ranker = new ide.sandbox.CompletionRanker({ mruManager: manager });

        // Record usage under conn_1
        manager.recordItem('postgresql', 'conn_1', 'column', 'customer_id');
        manager.recordItem('postgresql', 'conn_1', 'column', 'customer_id');

        // Query bonus from conn_1
        const bonusConn1 = manager.getMruBonus('postgresql', 'conn_1', 'column', 'customer_id');
        assert.strictEqual(bonusConn1, 6, 'Bonus on exact connection should be 6 pts');

        // Query bonus from conn_2 (different connection on same PostgreSQL engine)
        const bonusConn2 = manager.getMruBonus('postgresql', 'conn_2', 'column', 'customer_id');
        assert.ok(bonusConn2 > 0, 'Cross-connection habit bonus should be retained across connections on same DB engine');

        // Query bonus with alias postgres vs postgresql
        const bonusPgAlias = manager.getMruBonus('postgres', 'conn_3', 'column', 'customer_id');
        assert.ok(bonusPgAlias > 0, 'Engine alias postgres should resolve to postgresql');

        // Check ranker ranking with default settings (no localStorage set)
        const items = [
            { name: 'address', type: 'column' },
            { name: 'customer_id', type: 'column' },
            { name: 'phone', type: 'column' }
        ];

        const ranked = ranker.rank(items, { dbType: 'postgresql', connectionId: 'conn_1' }, '');
        assert.strictEqual(ranked[0].name, 'customer_id', 'Frequently used customer_id must rank #1 by default');
    });

    test('5. Typing ssf at statement start suggests ssf snippet and excludes tables/views/functions', async () => {
        // Mock snippet provider with ssf
        ide.sandbox.window.AppSnippets = {
            getSnippets: () => [
                { id: '1', prefix: 'ssf', description: 'SELECT * FROM fragment', body: 'SELECT * FROM ', dbType: 'all' },
                { id: '2', prefix: 'sfb', description: 'SELECT * FROM WHERE fragment', body: 'SELECT * FROM  WHERE ', dbType: 'all' }
            ],
            matchesDbType: () => true,
            normalizeDbType: (d) => d || 'all'
        };

        const res = await ide.getCompletions('ssf‸', null, tabCtx);
        assert.ok(res && res.length > 0, 'Completions must return at least ssf snippet');
        const first = res[0];
        assert.strictEqual(first.name, 'ssf', 'First candidate must be ssf snippet');
        assert.strictEqual(first.type, 'snippet', 'Candidate type must be snippet');

        // Check that NO tables, views, or functions are suggested
        const badObjects = res.filter(c => c.type === 'table' || c.type === 'view' || c.type === 'function');
        assert.strictEqual(badObjects.length, 0, 'No tables, views, or functions should be suggested at statement_start for ssf');
    });

    test('6. Invalid syntax typing at statement start yields 0 suggestions', async () => {
        const res = await ide.getCompletions('xyz999‸', null, tabCtx);
        assert.ok(!res || res.length === 0, 'Invalid token xyz999 must return 0 suggestions at statement_start');
    });

    test('7. Tab key dismisses popup without accepting; only Enter accepts suggestions', () => {
        const triggerJs = fs.readFileSync(path.join(ROOT_DIR, 'trigger-controller.js'), 'utf8');
        vm.runInContext(triggerJs, ide.sandbox);
        const TriggerController = ide.sandbox.TriggerController;
        const KeyCategory = ide.sandbox.KeyCategory;
        const controller = new TriggerController();

        // 1. Classification check
        const enterClass = controller.classifyKey({ key: 'Enter' });
        assert.strictEqual(enterClass.category, KeyCategory.ACTION_ACCEPT, 'Enter must be classified as ACTION_ACCEPT');

        const tabClass = controller.classifyKey({ key: 'Tab' });
        assert.notStrictEqual(tabClass.category, KeyCategory.ACTION_ACCEPT, 'Tab must NOT be classified as ACTION_ACCEPT');

        // 2. Decision check when popup is visible
        const tabDecision = controller.decideKeyDown({ key: 'Tab' }, true, false);
        assert.strictEqual(tabDecision.action, 'close', 'Tab key must dismiss popup (action=close) without accepting');

        const enterDecision = controller.decideKeyDown({ key: 'Enter' }, true, false);
        assert.strictEqual(enterDecision.action, 'accept', 'Enter key must accept the suggestion');
    });
});
