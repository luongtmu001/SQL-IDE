/*
 * IntelliSense host: everything the LLTool engine (intellisense/engine/*.js, copied unchanged) expected from
 * the Python app, implemented for the JavaFX WebView.
 *
 *  - loads the engine in small steps after the editor is ready (JS shares the FX thread with the whole app)
 *  - JavaMetadataProvider: metadata through window.javaBridge instead of /api/metadata
 *  - AppTabs / AppSnippets shims and the CodeMirror-style wrapper the engine talks to
 *  - batch view: for a long script the engine only sees the batch around the caret
 */
(function (window) {
    "use strict";

    var ENGINE = [
        "sql-dialects", "sql-tokenizer", "scope-resolver", "context-analyzer",
        "metadata-provider", "metadata-cache", "sql-function-signatures", "candidate-filter", "completion-ranker",
        "candidate-generator", "sql-completion-rules", "signature-help-resolver", "hover-resolver", "completion-engine",
        "trigger-controller", "editor-adapter"
    ];

    /** Below this size the engine sees the whole document (~60 ms per analyze measured in the WebView). */
    var WHOLE_DOCUMENT_CHARS = 16000;
    /** Upper bound of the text around the caret when a script has no GO separators, or a very long batch. */
    var WINDOW_CHARS_EACH_SIDE = 16000;
    var FAILURE_RETRY_MS = 15000;
    var REQUEST_TIMEOUT_MS = 130000;   // the details of a very large database (the server side stops at 120 s)
    var DB_TYPES = { SQLSERVER: "sqlserver", POSTGRESQL: "postgresql", SQLITE: "sqlite" };
    var GO_LINE = /^\s*GO(\s+\d+)?\s*(--.*)?$/i;

    var ctx = {
        enabled: true, dialect: "OTHER", keywordCase: "upper", mru: true,
        detail: true, detailMode: "always", connectionId: null, database: null, schema: null
    };
    var editor = null;
    var loaded = false, loading = false;

    function dbType() { return DB_TYPES[ctx.dialect] || "common"; }

    // ------------------------------------------------------------------ Java requests

    var nextRequestId = 1;
    var pending = Object.create(null);    // id -> { resolve, timer, key }
    var inFlight = Object.create(null);   // key -> promise (same question while one is running: one query)
    var failures = Object.create(null);   // key -> { at, error }

    function request(method, args) {
        var key = method + "|" + JSON.stringify(args);
        var failed = failures[key];
        if (failed && Date.now() - failed.at < FAILURE_RETRY_MS) {
            return Promise.resolve({ ok: false, error: failed.error });
        }
        if (inFlight[key]) return inFlight[key];
        if (!window.javaBridge || !window.javaBridge.intellisenseRequest) {
            return Promise.resolve({ ok: false, error: "Chưa nối được với ứng dụng." });
        }
        var p = new Promise(function (resolve) {
            var id = "r" + (nextRequestId++);
            var timer = setTimeout(function () {
                settle(id, { ok: false, error: "Đọc metadata quá lâu." });
            }, REQUEST_TIMEOUT_MS);
            pending[id] = { resolve: resolve, timer: timer, key: key };
            try {
                window.javaBridge.intellisenseRequest(id, method, JSON.stringify(args));
            } catch (e) {
                settle(id, { ok: false, error: String(e) });
            }
        });
        inFlight[key] = p;
        return p;
    }

    function settle(id, result) {
        var r = pending[id];
        if (!r) return;
        delete pending[id];
        delete inFlight[r.key];
        clearTimeout(r.timer);
        if (result && result.ok === false) failures[r.key] = { at: Date.now(), error: result.error };
        r.resolve(result);
    }

    function clearFailures() { failures = Object.create(null); }

    /** The text the user was at when a list came back without the names. */
    var waitingVersion = null;

    function caretKey() {
        var m = editor && editor.getModel();
        var p = editor && editor.getPosition();
        return m && p ? m.id + "|" + m.getAlternativeVersionId() + "|" + p.lineNumber + ":" + p.column : null;
    }

    /** Only the popup waits; a hover or signature that asked meanwhile must not open it later. */
    function noteWaiting() {
        var api = window.AppIntelliSense;
        if (api && api.getTriggerController().getState() === window.CompletionState.OPENING) waitingVersion = caretKey();
    }

    function installProvider() {
        var Base = window.MetadataProvider;

        function list(method, args) {
            return request(method, args).then(function (res) {
                if (res && res.loading) {
                    // The names are on their way: the list shows what it has now and is filled in when they arrive.
                    var waiting = [];
                    waiting.loading = true;
                    noteWaiting();
                    return waiting;
                }
                if (res && res.ok) return Array.isArray(res.data) ? res.data : [];
                var err = [];
                err.hasError = true;
                err.error = new Error(res ? res.error : "Không tải được metadata");
                return err;
            });
        }

        class JavaMetadataProvider extends Base {
            constructor() { super(dbType()); this._lastError = null; }
            getLastError() { return this._lastError; }
            clearLastError() { this._lastError = null; }
            cleanIdentifier(ident) {
                var s = String(ident == null ? "" : ident).trim();
                if ((s[0] === "[" && s[s.length - 1] === "]") || (s[0] === '"' && s[s.length - 1] === '"') || (s[0] === "`" && s[s.length - 1] === "`")) {
                    return s.slice(1, -1);
                }
                return s;
            }
            quoteIdentifier(ident) {
                var clean = this.cleanIdentifier(ident);
                if (dbType() === "sqlserver") return "[" + clean + "]";
                return '"' + clean + '"';
            }
            getDatabases(c) { return c ? list("databases", [c]) : Promise.resolve([]); }
            getSchemas(c, db) { return c ? list("schemas", [c, db]) : Promise.resolve([]); }
            getObjects(c, db, schema) { return c ? list("objects", [c, db, this.cleanIdentifier(schema)]) : Promise.resolve([]); }
            getColumns(c, db, schema, table) {
                return c && table ? list("columns", [c, db, this.cleanIdentifier(schema), this.cleanIdentifier(table)]) : Promise.resolve([]);
            }
            getParameters(c, db, schema, name) {
                return c && name ? list("parameters", [c, db, this.cleanIdentifier(schema), this.cleanIdentifier(name)]) : Promise.resolve([]);
            }
            getDefinition(c, db, schema, name, type) {
                if (!c || !name) return Promise.resolve("");
                return request("definition", [c, db, this.cleanIdentifier(schema), this.cleanIdentifier(name), type || ""])
                    .then(function (res) { return res && res.ok && res.data ? String(res.data) : ""; });
            }
            getSupportedTypes() { return Promise.resolve([]); }
            getUserTypes(c, db, schema) { return c ? list("userTypes", [c, db, schema]) : Promise.resolve([]); }
            getForeignKeys(c, db, schema, table) {
                return c && table ? list("foreignKeys", [c, db, this.cleanIdentifier(schema), this.cleanIdentifier(table)]) : Promise.resolve([]);
            }
            getLinkedServers(c) { return c ? list("linkedServers", [c]) : Promise.resolve([]); }
            getRemoteDatabases(c, server) { return c && server ? list("remoteDatabases", [c, server]) : Promise.resolve([]); }
            getRemoteSchemas(c, server, db) { return c && server ? list("remoteSchemas", [c, server, db]) : Promise.resolve([]); }
            getRemoteObjects(c, server, db, schema) { return c && server ? list("remoteObjects", [c, server, db, schema]) : Promise.resolve([]); }
            getRemoteColumns(c, server, db, schema, table) {
                return c && server && table ? list("remoteColumns", [c, server, db, schema, this.cleanIdentifier(table)]) : Promise.resolve([]);
            }
            getForeignServers(c) { return c ? list("foreignServers", [c]) : Promise.resolve([]); }
            getForeignTables(c, schema) { return c ? list("foreignTables", [c, schema]) : Promise.resolve([]); }
        }

        var provider = new JavaMetadataProvider();
        var factory = window.MetadataProviderFactory;
        factory.getProvider = function () { return provider; };
        factory.getProviderForConnection = function () { return provider; };
        factory.getDbTypeForConnection = function () { return dbType(); };
        window.AppMetadataProvider = provider;
    }

    // ------------------------------------------------------------------ tabs / globals the engine reads

    function tabState() {
        return {
            id: "active", tabId: "active", connectionId: ctx.connectionId, database: ctx.database,
            schema: ctx.schema, dbType: dbType()
        };
    }

    window.AppTabs = {
        getActiveTabId: function () { return "active"; },
        getActiveTabState: tabState,
        getAllTabs: function () { return [tabState()]; },
        createTab: function (t) {
            if (window.javaBridge && window.javaBridge.intellisenseOpenScript) {
                window.javaBridge.intellisenseOpenScript(t.title || "Definition.sql", t.content || "");
            }
        }
    };

    function syncGlobals() {
        window.ActiveConnectionId = ctx.connectionId;
        window.ActiveDatabase = ctx.database;
        window.ActiveSchema = ctx.schema;
        window.ActiveDbType = dbType();
        try { window.localStorage.setItem("intellisense.v2.mru", ctx.mru ? "true" : "false"); } catch (e) { /* no storage: defaults */ }
    }

    // ------------------------------------------------------------------ snippets (data: sql-intellisense.js)

    function rawSnippets() {
        return window.SqlIntellisense ? window.SqlIntellisense._itemsFor(ctx.dialect).sn : [];
    }

    /** Monaco template -> plain text with the first placeholder selected (only a fallback / the preview). */
    function expandTemplate(body) {
        var first = null;
        var out = "";
        var re = /\$\{(\d+):([^}]*)\}|\$(\d+)/g, last = 0, m;
        while ((m = re.exec(body)) !== null) {
            out += body.slice(last, m.index);
            var text = m[2] || "";
            if (first === null) first = { start: out.length, end: out.length + text.length };
            out += text;
            last = m.index + m[0].length;
        }
        out += body.slice(last);
        return {
            expandedText: out,
            selectionStart: first ? first.start : out.length,
            selectionEnd: first ? first.end : out.length
        };
    }

    window.AppSnippets = {
        getSnippets: function () {
            return rawSnippets().map(function (s) {
                return { prefix: s[0], body: s[1], description: s[2], dbType: "all", placeholders: [] };
            });
        },
        matchesDbType: function () { return true; },
        normalizeDbType: function () { return "all"; },
        expandSnippetBody: function (snip) { return expandTemplate(snip.body || ""); },
        /** Tab right after an exact snippet prefix expands it (LLTool behaviour). */
        tryExpandSnippet: function () {
            if (!editor || !editor.getModel()) return false;
            var pos = editor.getPosition();
            var sel = editor.getSelection();
            if (!pos || (sel && !sel.isEmpty())) return false;
            var word = editor.getModel().getWordUntilPosition(pos);
            if (!word.word) return false;
            var lower = word.word.toLowerCase();
            var hit = rawSnippets().filter(function (s) { return s[0].toLowerCase() === lower; })[0];
            if (!hit) return false;
            editor.setSelection(new monaco.Selection(pos.lineNumber, word.startColumn, pos.lineNumber, word.endColumn));
            return insertSnippet(hit[1]);
        }
    };

    /** Inserts a Monaco template over the current selection, with real tab stops. */
    function insertSnippet(body) {
        var c = editor && editor.getContribution("snippetController2");
        if (!c || typeof c.insert !== "function") return false;
        c.insert(body);
        return true;
    }

    // ------------------------------------------------------------------ engine tuning

    /** Per-dialect keywords / functions Java's IntelliSense already knew stay available. */
    function mergeWords() {
        var F = window.SqlDialectFactory, S = window.SqlIntellisense;
        if (!F || !S) return;
        Object.keys(DB_TYPES).forEach(function (d) {
            var dialect = F.getDialect(DB_TYPES[d]);
            if (!dialect || !dialect.getKeywords) return;
            var set = S._itemsFor(d);
            var merged = function (base, more) {
                var seen = Object.create(null), out = [];
                base.concat(more).forEach(function (w) {
                    var u = String(w).toUpperCase();
                    if (!seen[u]) { seen[u] = 1; out.push(w); }
                });
                return out;
            };
            var keywords = merged(dialect.getKeywords(), set.kw);
            var functions = merged(dialect.getFunctions(), set.fn);
            dialect.getKeywords = function () { return keywords; };
            dialect.getFunctions = function () { return functions; };
        });
    }

    /**
     * One keystroke reaches analyze() several times (decision, trigger, engine) with the same text: reuse it.
     * The analyze itself runs on the thread that also paints the app.
     */
    function memoizeAnalyze() {
        var A = window.SqlContextAnalyzer;
        var original = A.analyze;
        var lastText = null, lastOffset = -1, lastType = null, lastResult = null;
        A.analyze = function (text, offset, type) {
            if (text === lastText && offset === lastOffset && type === lastType && lastResult) return lastResult;
            lastResult = original.call(A, text, offset, type);
            lastText = text; lastOffset = offset; lastType = type;
            return lastResult;
        };
    }

    // ------------------------------------------------------------------ editor wrapper + batch view

    var KEY_NAMES = {
        8: "Backspace", 9: "Tab", 13: "Enter", 16: "Shift", 17: "Control", 18: "Alt", 20: "CapsLock", 27: "Escape",
        32: " ", 33: "PageUp", 34: "PageDown", 35: "End", 36: "Home", 37: "ArrowLeft", 38: "ArrowUp", 39: "ArrowRight",
        40: "ArrowDown", 45: "Insert", 46: "Delete"
    };

    /** JavaFX's WebKit leaves KeyboardEvent.key and .code empty; the engine decides by them, so rebuild them from keyCode. */
    function namedKey(be) {
        if (!be) return { key: "", code: "" };
        if (be.key) return { key: be.key, code: be.code || be.key };
        var n = be.keyCode;
        if (n >= 112 && n <= 123) return { key: "F" + (n - 111), code: "F" + (n - 111) };
        var key = KEY_NAMES[n];
        if (key) return { key: key, code: key === " " ? "Space" : key };
        if (n >= 65 && n <= 90) {
            var ch = String.fromCharCode(n);
            return { key: be.shiftKey ? ch : ch.toLowerCase(), code: "Key" + ch };
        }
        if (n >= 48 && n <= 57) return { key: String(n - 48), code: "Digit" + (n - 48) };
        return { key: "Unidentified", code: "" };
    }

    /** The lines the engine works on for a caret line: the batch around it, cut to a bounded size. */
    function computeView(m, caretLine) {
        var count = m.getLineCount();
        if (m.getValueLength() <= WHOLE_DOCUMENT_CHARS) return { startLine: 1, endLine: count };
        var budget = WINDOW_CHARS_EACH_SIDE;
        var start = caretLine, i;
        for (i = caretLine; i >= 1; i--) {
            var up = m.getLineContent(i);
            if (i < caretLine && GO_LINE.test(up)) { start = i + 1; break; }
            start = i;
            budget -= up.length + 1;
            if (budget <= 0 && /;\s*$/.test(up)) { start = i + 1; break; }
            if (budget <= -WINDOW_CHARS_EACH_SIDE) break;
        }
        budget = WINDOW_CHARS_EACH_SIDE;
        var end = caretLine;
        for (i = caretLine; i <= count; i++) {
            var down = m.getLineContent(i);
            if (i > caretLine && GO_LINE.test(down)) { end = i - 1; break; }
            end = i;
            budget -= down.length + 1;
            if (budget <= 0 && /;\s*$/.test(down)) break;
            if (budget <= -WINDOW_CHARS_EACH_SIDE) break;
        }
        return { startLine: start, endLine: Math.max(start, end) };
    }

    /**
     * The API the engine and controller use, in coordinates relative to the "view": the batch around the caret
     * for a long script, the whole document otherwise. Lines are never cut, so {line, ch} only shift by a line.
     */
    function createEditorWrapper(ed) {
        var cached = null;      // { version, startLine, endLine }
        var pinnedStart = 0;    // > 0: an edit is in progress, keep the same origin

        function model() { return ed.getModel(); }

        function view() {
            var m = model();
            if (!m) return { startLine: 1, endLine: 1 };
            if (pinnedStart > 0) return { startLine: pinnedStart, endLine: m.getLineCount() };
            var pos = ed.getPosition() || { lineNumber: 1 };
            var version = m.getAlternativeVersionId() + "|" + m.id;
            if (cached && cached.version === version && pos.lineNumber >= cached.startLine && pos.lineNumber <= cached.endLine) {
                return cached;
            }
            cached = computeView(m, pos.lineNumber);
            cached.version = version;
            return cached;
        }

        function baseOffset(v) { return model().getOffsetAt({ lineNumber: v.startLine, column: 1 }); }

        var wrapper = {
            isAdapter: false,
            get rawEditor() { return ed; },
            getValue: function () {
                var m = model();
                if (!m) return "";
                var v = view();
                if (v.startLine === 1 && v.endLine === m.getLineCount()) return m.getValue();
                return m.getValueInRange(new monaco.Range(v.startLine, 1, v.endLine, m.getLineMaxColumn(v.endLine)));
            },
            getLine: function (n) {
                var m = model();
                var ln = n + view().startLine;
                return m && ln >= 1 && ln <= m.getLineCount() ? m.getLineContent(ln) : "";
            },
            getRange: function (from, to) {
                var m = model();
                if (!m || !from || !to) return "";
                var s = view().startLine;
                return m.getValueInRange(new monaco.Range(from.line + s, from.ch + 1, to.line + s, to.ch + 1));
            },
            getCursor: function () {
                var pos = ed.getPosition() || { lineNumber: 1, column: 1 };
                return { line: pos.lineNumber - view().startLine, ch: pos.column - 1 };
            },
            setCursor: function (pos) {
                if (!pos) return;
                ed.setPosition({ lineNumber: pos.line + view().startLine, column: (pos.ch || 0) + 1 });
            },
            setSelection: function (a, b) {
                if (!a) return;
                var s = view().startLine, e = b || a;
                ed.setSelection(new monaco.Selection(a.line + s, a.ch + 1, e.line + s, (e.ch !== undefined ? e.ch : a.ch) + 1));
            },
            somethingSelected: function () { var sel = ed.getSelection(); return Boolean(sel && !sel.isEmpty()); },
            posFromIndex: function (idx) {
                var m = model();
                if (!m) return { line: 0, ch: 0 };
                var v = view();
                var p = m.getPositionAt(baseOffset(v) + idx);
                return { line: p.lineNumber - v.startLine, ch: p.column - 1 };
            },
            indexFromPos: function (pos) {
                var m = model();
                if (!m || !pos) return 0;
                var v = view();
                return m.getOffsetAt({ lineNumber: pos.line + v.startLine, column: (pos.ch || 0) + 1 }) - baseOffset(v);
            },
            replaceRange: function (text, from, to) {
                if (!from) return;
                var s = view().startLine, e = to || from;
                ed.executeEdits("intellisense", [{
                    range: new monaco.Range(from.line + s, from.ch + 1, e.line + s, (e.ch !== undefined ? e.ch : from.ch) + 1),
                    text: text, forceMoveMarkers: true
                }]);
                ed.pushUndoStop();
            },
            operation: function (fn) { return fn(); },
            cursorCoords: function (pos) {
                var p = pos ? { lineNumber: pos.line + view().startLine, column: (pos.ch || 0) + 1 } : (ed.getPosition() || { lineNumber: 1, column: 1 });
                var px = ed.getScrolledVisiblePosition(p);
                var dom = ed.getDomNode();
                var rect = dom ? dom.getBoundingClientRect() : { top: 0, left: 0 };
                if (px) {
                    var top = rect.top + px.top, left = rect.left + px.left;
                    return { top: top, left: left, bottom: top + (px.height || 19), right: left };
                }
                return { top: rect.top + 50, left: rect.left + 50, bottom: rect.top + 70, right: rect.left + 50 };
            },
            findWordAt: function (pos) {
                var m = model();
                if (!m) return { anchor: pos, head: pos };
                var w = m.getWordAtPosition({ lineNumber: pos.line + view().startLine, column: (pos.ch || 0) + 1 });
                return w
                    ? { anchor: { line: pos.line, ch: w.startColumn - 1 }, head: { line: pos.line, ch: w.endColumn - 1 } }
                    : { anchor: pos, head: pos };
            },
            scrollIntoView: function (pos) {
                if (pos) ed.revealPositionInCenterIfOutsideViewport({ lineNumber: pos.line + view().startLine, column: (pos.ch || 0) + 1 });
            },
            focus: function () { ed.focus(); },
            /** Keeps the view's first line while text is inserted, whatever the inserted text contains. */
            pinView: function () { pinnedStart = view().startLine; },
            unpinView: function () { pinnedStart = 0; cached = null; },
            on: function (event, fn) {
                if (event === "change") {
                    ed.onDidChangeModelContent(function (e) {
                        var hasRemoval = (e.changes || []).some(function (c) { return (c.rangeLength || 0) > 0; });
                        fn(wrapper, {
                            origin: e.isFlush ? "setValue" : (e.isUndoing || e.isRedoing ? "undo" : "+input"),
                            changes: e.changes,
                            text: (e.changes || []).map(function (c) { return c.text || ""; }),
                            removed: hasRemoval ? ["x"] : []
                        });
                    });
                } else if (event === "cursorActivity") {
                    ed.onDidChangeCursorPosition(function (e) { fn(wrapper, e); });
                } else if (event === "keydown") {
                    ed.onKeyDown(function (e) {
                        var be = e.browserEvent;
                        var named = namedKey(be);
                        fn(wrapper, {
                            key: named.key, code: named.code, keyCode: e.keyCode,
                            ctrlKey: e.ctrlKey, metaKey: e.metaKey, altKey: e.altKey, shiftKey: e.shiftKey,
                            browserEvent: be,
                            preventDefault: function () { e.preventDefault(); },
                            stopPropagation: function () { e.stopPropagation(); }
                        });
                    });
                }
            }
        };
        return wrapper;
    }

    /** What the hover / signature providers read from a model, limited to the batch around the given position. */
    function batchModel(model, position) {
        if (model.getValueLength() <= WHOLE_DOCUMENT_CHARS) return { model: model, position: position, lineShift: 0 };
        var v = computeView(model, position.lineNumber);
        var base = model.getOffsetAt({ lineNumber: v.startLine, column: 1 });
        return {
            lineShift: v.startLine - 1,
            position: position,
            model: {
                getValue: function () {
                    return model.getValueInRange(new monaco.Range(v.startLine, 1, v.endLine, model.getLineMaxColumn(v.endLine)));
                },
                getOffsetAt: function (p) { return model.getOffsetAt(p) - base; }
            }
        };
    }

    /**
     * The engine registers hover / signature providers once for all tabs: here they obey the Settings switch and
     * only see the current batch (signature help runs on every space, a whole long script would stall the app).
     */
    function gateProviders() {
        var L = monaco.languages;
        var hover = L.registerHoverProvider, sig = L.registerSignatureHelpProvider;
        L.registerHoverProvider = function (lang, p) {
            var f = p.provideHover;
            p.provideHover = function (model, position, token) {
                if (!ctx.enabled) return null;
                var b = batchModel(model, position);
                return Promise.resolve(f.call(p, b.model, b.position, token)).then(function (r) {
                    if (r && r.range && b.lineShift) {
                        r.range = new monaco.Range(r.range.startLineNumber + b.lineShift, r.range.startColumn, r.range.endLineNumber + b.lineShift, r.range.endColumn);
                    }
                    return r;
                });
            };
            window.IntelliSenseHost.hoverProvider = p;
            return hover.call(L, lang, p);
        };
        L.registerSignatureHelpProvider = function (lang, p) {
            var f = p.provideSignatureHelp;
            p.provideSignatureHelp = function (model, position, token, context) {
                if (!ctx.enabled) return null;
                var b = batchModel(model, position);
                return f.call(p, b.model, b.position, token, context);
            };
            window.IntelliSenseHost.signatureProvider = p;
            return sig.call(L, lang, p);
        };
    }

    // ------------------------------------------------------------------ loading

    function loadScript(src) {
        return new Promise(function (resolve, reject) {
            var s = document.createElement("script");
            s.src = src;
            s.onload = resolve;
            s.onerror = function () { reject(new Error("Cannot load " + src)); };
            document.head.appendChild(s);
        });
    }

    function pause() { return new Promise(function (r) { setTimeout(r, 0); }); }

    function loadEngine() {
        if (loaded || loading || !editor || !ctx.enabled) return;
        loading = true;
        var started = Date.now();
        gateProviders();   // before the engine: it registers its providers while loading
        var chain = Promise.resolve();
        ENGINE.forEach(function (name, i) {
            chain = chain.then(function () { return loadScript("intellisense/engine/" + name + ".js"); }).then(pause);
            if (name === "metadata-provider") chain = chain.then(installProvider);
            if (name === "context-analyzer") chain = chain.then(function () { memoizeAnalyze(); mergeWords(); });
        });
        chain.then(function () { return loadScript("intellisense/intellisense.js"); }).then(function () {
            window.AppIntelliSense.init();
            window.AppIntelliSense.attachEditor(createEditorWrapper(editor));
            editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyMod.Shift | monaco.KeyCode.KeyR, function () {
                if (ctx.connectionId && window.javaBridge && window.javaBridge.intellisenseReload) {
                    window.javaBridge.intellisenseReload(ctx.connectionId, ctx.database || "");
                }
            });
            loaded = true;
            loading = false;
            window.IntelliSenseHost.loadMs = Date.now() - started;
        }).catch(function (e) {
            loading = false;
            console.error("[IntelliSense] " + e);
        });
    }

    // ------------------------------------------------------------------ measuring (off unless Java asks)

    var perfOn = false;

    function installPerf() {
        var send = function (line) { if (window.javaBridge && window.javaBridge.intellisensePerf) window.javaBridge.intellisensePerf(line); };
        var spent = {}, keys = 0, triggerAt = 0;
        var wrap = function (owner, method, label, after) {
            var f = owner && owner[method];
            if (typeof f !== "function") return;
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
                    if (after) after();
                }
            };
        };
        var api = window.AppIntelliSense, c = api.getController(), ui = api.getPopupUI(), e = c.completionEngine;
        wrap(window.SqlContextAnalyzer, "analyze", "analyze");
        wrap(e.filter, "filter", "filter");
        wrap(e.ranker, "rank", "rank");
        wrap(window.SqlCompletionRules, "resolve", "rules");
        wrap(ui, "updateDetailPreview", "detail");
        wrap(ui, "render", "render", function () {
            if (triggerAt) {
                send("popup on screen " + Math.round(performance.now() - triggerAt) + " ms after trigger, " + ui.candidates.length + " items");
                triggerAt = 0;
            }
        });
        var trigger = c.trigger;
        c.trigger = function () {
            triggerAt = performance.now();
            return trigger.apply(this, arguments);
        };
        editor.onDidChangeModelContent(function () { keys++; });
        setInterval(function () {
            if (!keys) return;
            var m = editor.getModel();
            var line = keys + " edits, " + (m ? m.getValueLength() : 0) + " chars:";
            Object.keys(spent).sort().forEach(function (k) {
                line += " " + k + " " + spent[k].calls + "x " + Math.round(spent[k].ms) + "ms (max " + Math.round(spent[k].max) + ")";
            });
            send(line);
            spent = {};
            keys = 0;
        }, 5000);
        send("measuring on, engine loaded in " + window.IntelliSenseHost.loadMs + " ms");
    }

    // ------------------------------------------------------------------ public

    window.IntelliSenseHost = {
        loadMs: 0,
        /** Called by editor-bridge.js once the editor exists; waits until the app has finished starting. */
        start: function (ed) {
            editor = ed;
            setTimeout(loadEngine, 400);
        },
        /** Answer of javaBridge.intellisenseRequest. */
        resolve: function (id, json) {
            var result;
            try { result = JSON.parse(json); } catch (e) { result = { ok: false, error: String(e) }; }
            settle(id, result);
        },
        contextChanged: function (c) {
            var was = ctx.connectionId + "|" + ctx.database + "|" + ctx.schema + "|" + ctx.enabled + "|" + ctx.dialect
                + "|" + ctx.detail + "|" + ctx.detailMode;
            ["enabled", "dialect", "keywordCase", "mru", "detail", "detailMode", "connectionId", "database", "schema"].forEach(function (k) {
                if (c[k] !== undefined) ctx[k] = c[k];
            });
            syncGlobals();
            var now = ctx.connectionId + "|" + ctx.database + "|" + ctx.schema + "|" + ctx.enabled + "|" + ctx.dialect
                + "|" + ctx.detail + "|" + ctx.detailMode;
            if (was !== now && loaded) window.AppIntelliSense.hidePopup();
            loadEngine();
        },
        invalidate: function (connectionId) {
            clearFailures();
            if (loaded) window.AppIntelliSense.invalidateConnection(connectionId);
        },
        /** New metadata can be served: forget what was remembered and fill in the list the user is waiting for. */
        metadataArrived: function (connectionId) {
            clearFailures();
            if (!loaded) return;
            window.AppIntelliSense.invalidateConnection(connectionId);
            var waited = waitingVersion;
            waitingVersion = null;
            if (connectionId !== ctx.connectionId || !editor) return;
            window.AppIntelliSense.getController().metadataArrived(Boolean(waited && waited === caretKey()));
        },
        clearWaiting: function () { waitingVersion = null; },
        /** Measuring only (SQLIDE_PERF_IS=1, Phase 0): time inside the main steps, reported every 5 s while typing. */
        enablePerf: function () {
            if (perfOn) return;
            perfOn = true;
            var install = setInterval(function () {
                if (!loaded) return;
                clearInterval(install);
                installPerf();
            }, 200);
        },
        context: function () { return ctx; },
        isEnabled: function () { return ctx.enabled !== false; },
        isLoaded: function () { return loaded; },
        detailEnabled: function () { return ctx.enabled !== false && ctx.detail !== false; },
        detailOnDemand: function () { return ctx.detail !== false && ctx.detailMode === "onDemand"; },
        applyKeywordCase: function (word) {
            if (/[a-z]/.test(String(word))) return word;   // written that way on purpose: plpgsql, search_path, year
            if (ctx.keywordCase === "lower") return String(word).toLowerCase();
            if (ctx.keywordCase === "preserve") return word;
            return String(word).toUpperCase();
        },
        clearFailures: clearFailures,
        insertSnippet: insertSnippet,
        pendingRequests: function () { return Object.keys(pending).length; }
    };

    syncGlobals();
})(window);
