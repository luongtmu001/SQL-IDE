/*
 * Runs inside the REAL editor.html of this app (Monaco + IntelliSense), in Edge and in the JavaFX WebView, with a
 * JavaScript stand-in for the Java metadata. Measures what the user feels while typing:
 *   - key: time of one typed character until the layout is done
 *   - gap: how long the thread could do nothing else (a 4 ms timer that came late)
 *   - popup: from typing the space after FROM until the list is on screen
 * and the cost of the text that is handed over on every key (G3).
 */
(function () {
    var q = new URLSearchParams(location.search);
    var BROWSER = q.get('browser') || 'edge';
    var errors = [];
    window.addEventListener('error', function (e) { errors.push(String(e.message) + ' @' + e.filename + ':' + e.lineno); });

    var catalogSize = 5000;
    var catalogs = {};
    function catalogJson() {
        if (!catalogs[catalogSize]) {
            var out = [], kinds = ['table', 'table', 'table', 'view', 'procedure', 'function'];
            out.push({ name: 'Orders', schema: 'dbo', type: 'table' }, { name: 'Customers', schema: 'dbo', type: 'table' });
            for (var i = 0; i < catalogSize; i++) out.push({ name: (i % 2 ? 'Order_' : 'Table_') + i, schema: 'dbo', type: kinds[i % kinds.length] });
            catalogs[catalogSize] = JSON.stringify(out);
        }
        return catalogs[catalogSize];
    }
    var COLUMNS = JSON.stringify([
        { name: 'OrderId', data_type: 'int', is_nullable: false, is_pk: true }, { name: 'CustomerId', data_type: 'int', is_nullable: false, is_pk: false },
        { name: 'CustomerName', data_type: 'nvarchar', is_nullable: true, is_pk: false }, { name: 'Status', data_type: 'int', is_nullable: true, is_pk: false }]);

    // Answers in the next tick and from a ready string: the time measured is the page's, not the database's.
    window.javaBridge = {
        intellisenseRequest: function (id, method) {
            var data = method === 'objects' ? catalogJson() : method === 'columns' ? COLUMNS : method === 'schemas' ? '["dbo"]' : '[]';
            setTimeout(function () { window.IntelliSenseHost.resolve(id, '{"ok":true,"data":' + data + '}'); }, 0);
        },
        setClipboardText: function () {}, getClipboardText: function () { return ''; },
        onEditorReady: function () {}, onContentChanged: function () {}, onCursorPositionChanged: function () {},
        onZoomChanged: function () {}, onExecuteRequested: function () {}, onExecuteStatementRequested: function () {},
        intellisenseOpenScript: function () {}, intellisenseReload: function () {}
    };

    function stats(a) {
        if (!a.length) return { n: 0 };
        var s = a.slice().sort(function (x, y) { return x - y; });
        var r = function (v) { return Math.round(v * 10) / 10; };
        return { n: s.length, median: r(s[Math.floor(s.length / 2)]), p95: r(s[Math.min(s.length - 1, Math.floor(s.length * 0.95))]), max: r(s[s.length - 1]) };
    }
    function wait(ms) { return new Promise(function (r) { setTimeout(r, ms); }); }
    async function until(fn, ms) {
        var end = performance.now() + ms;
        while (performance.now() < end) { if (fn()) return true; await wait(5); }
        return false;
    }
    function documentOf(bytes) {
        var line = "SELECT o.OrderId, c.CustomerName, o.Status FROM dbo.Orders o INNER JOIN dbo.Customers c ON o.CustomerId = c.CustomerId WHERE o.Status > 1;\n";
        var s = '';
        while (s.length < bytes) s += line;
        return s + '\n';
    }
    function context(enabled, mode) {
        window.SqlIntellisense.setContext({ enabled: enabled, dialect: 'SQLSERVER', keywordCase: 'upper', mru: false, detail: true, detailMode: mode, connectionId: 'c1', database: 'bravo', schema: 'dbo' });
    }
    var editor;
    function reset(bytes, enabled, mode) {
        window.AppIntelliSense.hidePopup();
        window.AppMetadataCache.invalidateAll();
        context(enabled, mode);
        editor.setValue(documentOf(bytes));
        var last = editor.getModel().getLineCount();
        editor.setPosition({ lineNumber: last, column: 1 });
        editor.focus();
    }

    var TEXT = 'SELECT o.OrderId, c.CustomerName FROM dbo.Orders o JOIN dbo.Customers c ON o.CustomerId = c.CustomerId WHERE o.St';

    /** Ten characters per second: a normal typist. */
    async function typing(bytes, enabled, mode, n) {
        catalogSize = n;
        reset(bytes, enabled, mode);
        await wait(300);
        var keys = [], gaps = [], stop = false, last = performance.now();
        (function tick() {
            var now = performance.now();
            gaps.push(now - last);
            last = now;
            if (!stop) setTimeout(tick, 4);
        })();
        var shown = 0;
        for (var i = 0; i < TEXT.length; i++) {
            var t0 = performance.now();
            editor.trigger('keyboard', 'type', { text: TEXT[i] });
            void document.body.offsetHeight;
            keys.push(performance.now() - t0);
            await wait(100);
            if (window.AppIntelliSense.isPopupVisible()) shown++;
        }
        await wait(400);
        stop = true;
        window.AppIntelliSense.hidePopup();
        var over50 = gaps.filter(function (g) { return g >= 50; });
        return {
            sizeBytes: bytes, intellisense: enabled, detail: mode, objects: n, key: stats(keys), gap: stats(gaps),
            gapsOver50ms: over50.length, blockedOver50msTotal: Math.round(over50.reduce(function (a, b) { return a + b; }, 0)), popupVisibleKeys: shown, keys: TEXT.length
        };
    }

    /** The first list after FROM (nothing remembered yet), then the same again. */
    async function popupOpen(n, mode) {
        catalogSize = n;
        var first = [], again = [];
        for (var round = 0; round < 6; round++) {
            reset(1000, true, mode);
            await wait(120);
            editor.trigger('keyboard', 'type', { text: 'SELECT * FROM' });
            await wait(80);
            var t0 = performance.now();
            editor.trigger('keyboard', 'type', { text: ' ' });
            var ok = await until(function () { return window.AppIntelliSense.isPopupVisible(); }, 8000);
            if (ok) first.push(performance.now() - t0);
            window.AppIntelliSense.hidePopup();
            await wait(60);
            editor.trigger('keyboard', 'deleteLeft', {});
            await wait(60);
            window.AppMetadataCache.clearCompletions();
            var t1 = performance.now();
            editor.trigger('keyboard', 'type', { text: ' ' });
            ok = await until(function () { return window.AppIntelliSense.isPopupVisible(); }, 8000);
            if (ok) again.push(performance.now() - t1);
            window.AppIntelliSense.hidePopup();
            await wait(60);
        }
        return { objects: n, detail: mode, firstTime: stats(first), objectsRemembered: stats(again) };
    }

    /** G3: the text handed over on every key. In WebKit also through a real Java object. */
    function handover() {
        var out = {};
        var sizes = { '1KB': 1024, '16KB': 16000, '54KB': 54000, '100KB': 100000, '500KB': 500000 };
        for (var name in sizes) {
            var model = window.monaco.editor.createModel(documentOf(sizes[name]), 'sql');
            var getValue = [], toJava = [];
            for (var i = 0; i < 40; i++) {
                model.applyEdits([{ range: new window.monaco.Range(1, 1, 1, 1), text: 'x' }]);
                var t0 = performance.now();
                var v = model.getValue();
                var t1 = performance.now();
                getValue.push(t1 - t0);
                if (window.__javaPerf) {
                    window.__javaPerf.sink('id', v);
                    toJava.push(performance.now() - t1);
                }
            }
            out[name] = { getValue: stats(getValue), toJava: stats(toJava) };
            model.dispose();
        }
        return out;
    }

    // ------------------------------------------------------------------ where the time goes (?mode=breakdown)

    var spent = {};
    function wrap(owner, method, label) {
        var f = owner && owner[method];
        if (typeof f !== 'function') return;
        owner[method] = function () {
            var t0 = performance.now();
            try {
                return f.apply(this, arguments);
            } finally {
                var s = spent[label] || (spent[label] = { calls: 0, ms: 0, max: 0 });
                var d = performance.now() - t0;
                s.calls++;
                s.ms += d;
                if (d > s.max) s.max = d;
            }
        };
    }
    function installProbes() {
        var api = window.AppIntelliSense, c = api.getController(), ui = api.getPopupUI(), e = c.completionEngine;
        wrap(window.SqlContextAnalyzer, 'analyze', 'analyze');
        wrap(c, '_decideChange', 'controller._decideChange (gồm analyze)');
        wrap(c, '_typedWordChar', 'controller._typedWordChar');
        wrap(c, 'refresh', 'controller.refresh (gồm lọc + vẽ)');
        wrap(e.filter, 'filter', 'filter');
        wrap(e.ranker, 'rank', 'rank');
        wrap(ui, 'render', 'popup.render (gồm chọn mục)');
        wrap(ui, 'updateDetailPreview', 'popup.updateDetailPreview');
        wrap(window.SqlCompletionRules, 'resolve', 'rules.resolve');
        wrap(e.generator, 'generate', 'generator.generate (phần đồng bộ đầu)');
        wrap(window.IntelliSenseHost.signatureProvider, 'provideSignatureHelp', 'signatureHelp');
        wrap(JSON, 'parse', 'JSON.parse');
    }

    /** opts: hide (nothing painted), plain (no SQL colouring). */
    async function probe(label, bytes, enabled, opts) {
        opts = opts || {};
        catalogSize = 5000;
        var box = document.getElementById('monaco-container');
        box.style.visibility = opts.hide ? 'hidden' : '';
        reset(bytes, enabled, 'always');
        window.monaco.editor.setModelLanguage(editor.getModel(), opts.plain ? 'plaintext' : 'sql');
        await wait(1500);   // the background colouring of the new text is over
        spent = {};
        var gaps = [], keys = [], frames = [], stop = false, last = performance.now();
        (function tick() {
            var now = performance.now();
            gaps.push(now - last);
            last = now;
            if (!stop) setTimeout(tick, 4);
        })();
        var count = opts.idle ? 0 : 60;
        var t0 = performance.now();
        if (opts.idle) await wait(4000);
        for (var i = 0; i < count; i++) {
            var k0 = performance.now();
            editor.trigger('keyboard', 'type', { text: TEXT[i] });
            void document.body.offsetHeight;
            keys.push(performance.now() - k0);
            await wait(100);
        }
        var duration = performance.now() - t0;
        stop = true;
        window.AppIntelliSense.hidePopup();
        box.style.visibility = '';
        window.monaco.editor.setModelLanguage(editor.getModel(), 'sql');
        var over = gaps.filter(function (g) { return g >= 50; });
        var total = over.reduce(function (a, b) { return a + b; }, 0);
        var parts = {};
        Object.keys(spent).sort(function (a, b) { return spent[b].ms - spent[a].ms; }).forEach(function (n) {
            parts[n] = { calls: spent[n].calls, ms: Math.round(spent[n].ms), avg: Math.round(spent[n].ms / spent[n].calls * 10) / 10, max: Math.round(spent[n].max) };
        });
        return {
            label: label, sizeBytes: bytes, keys: count, durationMs: Math.round(duration), expectedMs: count ? count * 100 : 4000,
            key: stats(keys), gap: stats(gaps), gapsOver50ms: over.length, blockedOver50msTotal: Math.round(total), parts: parts
        };
    }

    async function breakdown() {
        var res = { browser: BROWSER, ua: navigator.userAgent, errors: errors, probes: [] };
        try {
            await until(function () { return window.editorBridge && window.editorBridge.isReady(); }, 60000);
            window.editorBridge.createModel('t1', '', 'sql');
            window.editorBridge.switchModel('t1');
            editor = window.monaco.editor.getEditors()[0];
            context(true, 'always');
            await until(function () { return window.IntelliSenseHost.isLoaded(); }, 60000);
            installProbes();
            var size = [1000, 15000];
            for (var i = 0; i < size.length; i++) {
                var s = size[i], n = Math.round(s / 1000) + 'KB';
                res.probes.push(await probe(n + ' đứng yên, tắt IS', s, false, { idle: true }));
                res.probes.push(await probe(n + ' gõ, tắt IS', s, false));
                res.probes.push(await probe(n + ' gõ, tắt IS, không tô màu', s, false, { plain: true }));
                res.probes.push(await probe(n + ' gõ, tắt IS, không vẽ', s, false, { hide: true }));
                res.probes.push(await probe(n + ' gõ, bật IS', s, true));
                res.probes.push(await probe(n + ' gõ, bật IS, không vẽ', s, true, { hide: true }));
            }
        } catch (e) {
            res.failure = String(e && e.stack || e);
        }
        var x = new XMLHttpRequest();
        x.open('POST', '/result?name=breakdown-' + BROWSER, true);
        x.send(JSON.stringify(res));
        window.__benchDone = true;
    }

    // ------------------------------------------------------------------ what the real app showed (?mode=focus)

    /** Ranking with the "recently used" bonus on and off, for an empty and a realistic history. */
    async function mruCost(n) {
        var E = window.AppIntelliSense.getController().completionEngine;
        var pool = [];
        for (var i = 0; i < n; i++) pool.push({ name: 'Order_' + i, label: 'Order_' + i, type: 'table', kind: 'table', schema: 'dbo', insertText: 'Order_' + i, level: 1 });
        var ctx = { clause: 'FROM', dbType: 'postgresql', connectionId: 'c1' };
        var measure = function (mru) {
            E.ranker.v2Mru = mru;
            var t = [];
            for (var r = 0; r < 3; r++) {
                var t0 = performance.now();
                E.ranker.rank(pool.map(function (p) { return Object.assign({}, p); }), ctx, 'o');
                t.push(performance.now() - t0);
            }
            return stats(t);
        };
        var key = 'intellisense_mru_history', saved = null, out = {};
        try { saved = window.localStorage.getItem(key); } catch (e) { /* no storage */ }
        out.storageAvailable = typeof window.localStorage !== 'undefined';
        out.off = measure(false);
        window.localStorage.removeItem(key);
        out.onEmptyHistory = measure(true);
        var hist = {};
        for (var h = 0; h < 200; h++) hist['postgresql::c1::table::t' + h] = { count: 2, lastUsed: Date.now() };
        window.localStorage.setItem(key, JSON.stringify(hist));
        out.on200Entries = measure(true);
        if (saved) window.localStorage.setItem(key, saved); else window.localStorage.removeItem(key);
        delete E.ranker.v2Mru;
        return out;
    }

    /** Moving the selection in an open list: one arrow key. */
    async function listMove(rows) {
        var api = window.AppIntelliSense, ui = api.getPopupUI();
        api.hidePopup();
        context(true, 'onDemand');
        var list = [];
        for (var i = 0; i < rows; i++) list.push({ name: 'Order_' + i, label: 'Order_' + i, type: 'table', schema: 'dbo', displayType: 'Table' });
        var t0 = performance.now();
        ui.render(list, { top: 100, bottom: 120, left: 100 }, { connectionId: 'c1', database: 'bravo', schema: 'dbo' }, api.getDataProvider(), 'o');
        void document.body.offsetHeight;
        var open = performance.now() - t0, moves = [];
        for (var m = 0; m < 25; m++) {
            var t1 = performance.now();
            ui.moveSelection(1);
            void document.body.offsetHeight;
            moves.push(performance.now() - t1);
            await wait(30);
        }
        api.hidePopup();
        return { rows: rows, openMs: Math.round(open), move: stats(moves) };
    }

    /** An arrow key in an open list including what follows it: painting, the detail panel. */
    async function listMovePainted(rows, mode, type) {
        var api = window.AppIntelliSense, ui = api.getPopupUI();
        api.hidePopup();
        context(true, mode);
        var list = [];
        for (var i = 0; i < rows; i++) list.push({ name: 'fn_' + i, label: 'fn_' + i, type: type, schema: 'dbo', displayType: type });
        ui.render(list, { top: 100, bottom: 120, left: 100 }, { connectionId: 'c1', database: 'bravo', schema: 'dbo' }, api.getDataProvider(), 'f');
        await wait(600);
        var settle = [], gaps = [], stop = false, last = performance.now();
        (function tick() {
            var now = performance.now();
            gaps.push(now - last);
            last = now;
            if (!stop) setTimeout(tick, 4);
        })();
        for (var m = 0; m < 20; m++) {
            var t0 = performance.now();
            ui.moveSelection(1);
            await wait(0);                 // the first turn of the thread after the selection
            settle.push(performance.now() - t0);
            await wait(250);               // the arrow key, a few times per second; the detail delay has passed
        }
        stop = true;
        api.hidePopup();
        return { rows: rows, detail: mode, type: type, toNextTurn: stats(settle), gap: stats(gaps), gapsOver50ms: gaps.filter(function (g) { return g >= 50; }).length };
    }

    async function focus() {
        var res = { browser: BROWSER, ua: navigator.userAgent, errors: errors };
        try {
            await until(function () { return window.editorBridge && window.editorBridge.isReady(); }, 60000);
            window.editorBridge.createModel('t1', '', 'sql');
            window.editorBridge.switchModel('t1');
            editor = window.monaco.editor.getEditors()[0];
            context(true, 'always');
            await until(function () { return window.IntelliSenseHost.isLoaded(); }, 60000);
            if (q.get('part') === 'list') {
                res.painted = [await listMovePainted(150, 'onDemand', 'function'), await listMovePainted(1000, 'onDemand', 'function'),
                    await listMovePainted(150, 'always', 'function'), await listMovePainted(1000, 'always', 'function')];
                var y = new XMLHttpRequest();
                y.open('POST', '/result?name=focuslist-' + BROWSER, true);
                y.send(JSON.stringify(res));
                window.__benchDone = true;
                return;
            }
            // the list first: ranking with the bonus on can take minutes on WebKit
            res.list = [await listMove(150), await listMove(1000)];
            res.mru = { '1000': await mruCost(1000), '3000': await mruCost(3000) };
        } catch (e) {
            res.failure = String(e && e.stack || e);
        }
        var x = new XMLHttpRequest();
        x.open('POST', '/result?name=focus-' + BROWSER, true);
        x.send(JSON.stringify(res));
        window.__benchDone = true;
    }

    async function run() {
        if (q.get('mode') === 'breakdown') return breakdown();
        if (q.get('mode') === 'focus') return focus();
        var res = { browser: BROWSER, ua: navigator.userAgent, errors: errors, typing: [], popup: [] };
        try {
            await until(function () { return window.editorBridge && window.editorBridge.isReady(); }, 60000);
            if (BROWSER === 'webkit') await until(function () { return !!window.__javaPerf; }, 5000);
            window.editorBridge.createModel('t1', '', 'sql');
            window.editorBridge.switchModel('t1');
            editor = window.monaco.editor.getEditors()[0];
            context(true, 'always');
            await until(function () { return window.IntelliSenseHost.isLoaded(); }, 60000);
            res.engineLoadMs = window.IntelliSenseHost.loadMs;
            res.handover = handover();
            var runs = [
                [1000, false, 'always', 5000], [1000, true, 'onDemand', 5000], [1000, true, 'always', 5000],
                [15000, false, 'always', 5000], [15000, true, 'onDemand', 5000], [15000, true, 'always', 5000],
                [54000, false, 'always', 5000], [54000, true, 'onDemand', 5000], [54000, true, 'always', 5000],
                [15000, true, 'always', 500]
            ];
            for (var i = 0; i < runs.length; i++) {
                var r = runs[i];
                res.typing.push(await typing(r[0], r[1], r[2], r[3]));
            }
            res.popup.push(await popupOpen(500, 'onDemand'), await popupOpen(5000, 'onDemand'), await popupOpen(20000, 'onDemand'), await popupOpen(5000, 'always'));
        } catch (e) {
            res.failure = String(e && e.stack || e);
        }
        var x = new XMLHttpRequest();
        x.open('POST', '/result?name=editor-' + BROWSER, true);
        x.send(JSON.stringify(res));
        window.__benchDone = true;
    }

    window.addEventListener('load', function () { setTimeout(run, 100); });
})();
