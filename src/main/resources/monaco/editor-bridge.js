// editor-bridge.js — Monaco Editor Bridge for JavaFX SQL IDE
(function (window) {
    "use strict";

    // ---- System clipboard bridge (JavaFX WebView has no usable navigator.clipboard) ----
    function readClipboard() {
        try {
            if (window.javaBridge && window.javaBridge.getClipboardText) {
                const t = window.javaBridge.getClipboardText();
                return t == null ? "" : String(t);
            }
        } catch (e) { /* ignore */ }
        return "";
    }
    function writeClipboard(text) {
        try {
            if (window.javaBridge && window.javaBridge.setClipboardText) {
                window.javaBridge.setClipboardText(String(text));
                return true;
            }
        } catch (e) { /* ignore */ }
        return false;
    }
    // Monaco's context-menu Paste/Copy and its fallbacks call navigator.clipboard: route them to Java.
    try {
        Object.defineProperty(navigator, "clipboard", {
            configurable: true,
            value: {
                readText: function () { return Promise.resolve(readClipboard()); },
                writeText: function (t) { writeClipboard(t); return Promise.resolve(); },
                write: function () { return Promise.resolve(); }
            }
        });
    } catch (e) { /* ignore */ }
    // Monaco's WebKit path prepares a ClipboardItem on every key press; this WebKit has none, and it would throw each time.
    // Copy itself goes through installClipboard(), so an empty stand-in is enough.
    if (!window.ClipboardItem) window.ClipboardItem = function () { };

    // Editor typography: Settings give the BASE size, zoom (Ctrl+Wheel / status bar) is a percentage of it.
    let baseFontSize = 13;
    let baseLineHeight = 1.5;   // <=4: multiplier of font size, >4: absolute px at 100 %
    let zoomPct = 100;
    const ZOOM_MIN = 20, ZOOM_MAX = 400;

    function lineHeightFor(fontSize) {
        const raw = Number(baseLineHeight);
        if (!raw || raw <= 0) return Math.round(fontSize * 19 / 13);
        if (raw <= 4) return Math.max(fontSize, Math.round(fontSize * raw));
        return Math.max(fontSize, Math.round(raw * zoomPct / 100));
    }
    function effectiveFontSize() {
        return Math.max(6, Math.min(100, Math.round(baseFontSize * zoomPct / 100)));
    }
    function applyTypography() {
        if (!editor) return;
        const fs = effectiveFontSize();
        editor.updateOptions({ fontSize: fs, lineHeight: lineHeightFor(fs) });
    }
    function setZoomPct(pct, notify) {
        pct = Math.max(ZOOM_MIN, Math.min(ZOOM_MAX, Math.round(pct)));
        if (pct === zoomPct) return;
        zoomPct = pct;
        applyTypography();
        if (notify && window.javaBridge && window.javaBridge.onZoomChanged) {
            window.javaBridge.onZoomChanged(zoomPct);
        }
    }

    let editor = null;
    let activeModelId = null;
    let lastLineCopy = null;   // text copied with an empty selection (whole line), pasted on its own line
    const models = new Map(); // id -> { model, viewState }
    let isReady = false;
    const contentChangeTimers = new Map();
    let cursorPositionTimer = null;

    function flushContentChanged(targetId) {
        const id = targetId || activeModelId;
        if (id && contentChangeTimers.has(id)) {
            clearTimeout(contentChangeTimers.get(id));
            contentChangeTimers.delete(id);
            if (models.has(id) && window.javaBridge && window.javaBridge.onContentChanged) {
                window.javaBridge.onContentChanged(id, models.get(id).model.getValue());
            }
        }
    }

    // Determine vs base path
    let vsPath = "vs";
    if (typeof window.require !== "undefined" && window.require.config) {
        window.require.config({
            paths: { "vs": vsPath },
            "vs/nls": { availableLanguages: {} }
        });
    }

    const DARK_THEME = "ide-dark";
    const LIGHT_THEME = "ide-light";

    const BASE_DARK_RULES = [
        { token: "keyword", foreground: "569cd6", fontStyle: "bold" },
        { token: "keyword.sql", foreground: "569cd6", fontStyle: "bold" },
        { token: "operator", foreground: "d4d4d4" },
        { token: "operator.sql", foreground: "d4d4d4" },
        { token: "predefined", foreground: "dcdcaa" },
        { token: "predefined.sql", foreground: "dcdcaa" },
        { token: "variable", foreground: "9cdcfe" },
        { token: "variable.sql", foreground: "9cdcfe" },
        { token: "variable.parameter", foreground: "f59e0b", fontStyle: "bold" },
        { token: "variable.parameter.sql", foreground: "f59e0b", fontStyle: "bold" },
        { token: "string", foreground: "ce9178" },
        { token: "string.sql", foreground: "ce9178" },
        { token: "comment", foreground: "6a9955", fontStyle: "italic" },
        { token: "comment.sql", foreground: "6a9955", fontStyle: "italic" },
        { token: "number", foreground: "b5cea8" },
        { token: "number.sql", foreground: "b5cea8" },
        { token: "type", foreground: "4ec9b0" },
        { token: "type.sql", foreground: "4ec9b0" },
        { token: "tag", foreground: "4ec9b0", fontStyle: "bold" },
        { token: "tag.xml", foreground: "4ec9b0", fontStyle: "bold" },
        { token: "attribute.name", foreground: "9cdcfe" },
        { token: "attribute.value", foreground: "ce9178" }
    ];

    const BASE_LIGHT_RULES = [
        { token: "keyword", foreground: "0000ff", fontStyle: "bold" },
        { token: "keyword.sql", foreground: "0000ff", fontStyle: "bold" },
        { token: "operator", foreground: "000000" },
        { token: "operator.sql", foreground: "000000" },
        { token: "predefined", foreground: "795e26" },
        { token: "predefined.sql", foreground: "795e26" },
        { token: "variable", foreground: "001080" },
        { token: "variable.sql", foreground: "001080" },
        { token: "variable.parameter", foreground: "b45309", fontStyle: "bold" },
        { token: "variable.parameter.sql", foreground: "b45309", fontStyle: "bold" },
        { token: "string", foreground: "a31515" },
        { token: "string.sql", foreground: "a31515" },
        { token: "comment", foreground: "008000", fontStyle: "italic" },
        { token: "comment.sql", foreground: "008000", fontStyle: "italic" },
        { token: "number", foreground: "098658" },
        { token: "number.sql", foreground: "098658" },
        { token: "type", foreground: "267f99" },
        { token: "type.sql", foreground: "267f99" }
    ];

    function installWheelZoom(container) {
        let acc = 0;
        container.addEventListener("wheel", function (e) {
            if (!(e.ctrlKey || e.metaKey)) return;
            e.preventDefault();
            e.stopPropagation();
            acc += e.deltaY;
            let steps = 0;
            while (acc <= -50) { steps++; acc += 50; }
            while (acc >= 50) { steps--; acc -= 50; }
            if (steps === 0) return;
            const cur = effectiveFontSize();
            const target = Math.max(6, Math.min(100, cur + steps));
            if (target === cur) return;
            setZoomPct(target / baseFontSize * 100, true);
            if (effectiveFontSize() === cur) {      // rounding swallowed the step
                setZoomPct(zoomPct + (steps > 0 ? 1 : -1), true);
            }
        }, { passive: false, capture: true });
    }

    function installClipboard() {
        function isReadOnly() { return editor.getOption(monaco.editor.EditorOption.readOnly); }

        function doCopy(cut) {
            const model = editor.getModel();
            const sels = editor.getSelections();
            if (!model || !sels || !sels.length) return;
            const nonEmpty = sels.filter(function (s) { return !s.isEmpty(); });
            let text, lineMode = false;
            if (nonEmpty.length === 0) {
                const lines = Array.from(new Set(sels.map(function (s) { return s.positionLineNumber; })))
                    .sort(function (a, b) { return a - b; });
                text = lines.map(function (l) { return model.getLineContent(l); }).join("\n") + "\n";
                lineMode = true;
                if (cut && !isReadOnly()) {
                    const edits = lines.map(function (l) {
                        return l < model.getLineCount()
                            ? { range: new monaco.Range(l, 1, l + 1, 1), text: "" }
                            : (l > 1
                                ? { range: new monaco.Range(l - 1, model.getLineMaxColumn(l - 1), l, model.getLineMaxColumn(l)), text: "" }
                                : { range: new monaco.Range(l, 1, l, model.getLineMaxColumn(l)), text: "" });
                    });
                    editor.executeEdits("clipboard-cut", edits);
                }
            } else {
                text = nonEmpty.map(function (s) { return model.getValueInRange(s); }).join("\n");
                if (cut && !isReadOnly()) {
                    editor.executeEdits("clipboard-cut", nonEmpty.map(function (s) { return { range: s, text: "" }; }));
                }
            }
            if (!writeClipboard(text)) {
                try { document.execCommand(cut ? "cut" : "copy"); } catch (e) { /* ignore */ }
                return;
            }
            lastLineCopy = lineMode ? text : null;
        }

        function doPaste() {
            if (isReadOnly()) return;
            const text = readClipboard();
            if (!text) return;
            const norm = text.replace(/\r\n/g, "\n");
            const onNewLine = lastLineCopy !== null && lastLineCopy === norm;
            try {
                editor.trigger("clipboard", "paste", { text: norm, pasteOnNewLine: onNewLine, multicursorText: null, mode: null });
            } catch (e) {
                const sels = editor.getSelections() || [];
                editor.executeEdits("clipboard-paste", sels.map(function (s) { return { range: s, text: norm, forceMoveMarkers: true }; }));
            }
            editor.focus();
        }

        const K = monaco.KeyCode, M = monaco.KeyMod, ctx = "editorTextFocus";
        editor.addCommand(M.CtrlCmd | K.KeyC, function () { doCopy(false); }, ctx);
        editor.addCommand(M.CtrlCmd | K.Insert, function () { doCopy(false); }, ctx);
        editor.addCommand(M.CtrlCmd | K.KeyX, function () { doCopy(true); }, ctx);
        editor.addCommand(M.Shift | K.Delete, function () { doCopy(true); }, ctx);
        editor.addCommand(M.CtrlCmd | K.KeyV, doPaste, ctx);
        editor.addCommand(M.Shift | K.Insert, doPaste, ctx);
    }

    function initMonaco() {
        window.require(["vs/editor/editor.main"], function () {
            // Define custom themes matching AtlantaFX IDE desktop theme
            monaco.editor.defineTheme(DARK_THEME, {
                base: "vs-dark",
                inherit: true,
                rules: BASE_DARK_RULES,
                colors: {
                    "editor.background": "#1c1f24",
                    "editor.foreground": "#d4d4d4",
                    "editor.lineHighlightBackground": "#23272e",
                    "editorLineNumber.foreground": "#636e7b",
                    "editorLineNumber.activeForeground": "#c6c6c6",
                    "editor.selectionBackground": "#264f78",
                    "editorCursor.foreground": "#aeafad"
                }
            });

            monaco.editor.defineTheme(LIGHT_THEME, {
                base: "vs",
                inherit: true,
                rules: BASE_LIGHT_RULES,
                colors: {
                    "editor.background": "#ffffff",
                    "editor.foreground": "#000000",
                    "editor.lineHighlightBackground": "#f5f5f5",
                    "editorLineNumber.foreground": "#747474",
                    "editorLineNumber.activeForeground": "#000000",
                    "editor.selectionBackground": "#add6ff"
                }
            });

            if (window.MonacoSqlLanguage) {
                window.MonacoSqlLanguage.register();
            }

            // Register SQL Document & Range Formatting Providers (Gate D / Milestone D6)
            if (window.monaco && window.SqlFormatter) {
                try {
                    monaco.languages.registerDocumentFormattingEditProvider('sql', {
                        provideDocumentFormattingEdits: function (model, options, token) {
                            try {
                                const fullText = model.getValue();
                                if (!fullText || !fullText.trim()) return [];
                                const formatted = window.SqlFormatter.format(fullText, {
                                    dialect: window.editorBridge?.activeDialect || 'sqlserver'
                                });
                                return [{
                                    range: model.getFullModelRange(),
                                    text: formatted
                                }];
                            } catch (e) {
                                console.warn('[SqlFormatter] Document formatting failed:', e);
                                return [];
                            }
                        }
                    });

                    monaco.languages.registerDocumentRangeFormattingEditProvider('sql', {
                        provideDocumentRangeFormattingEdits: function (model, range, options, token) {
                            try {
                                const selectedText = model.getValueInRange(range);
                                if (!selectedText || !selectedText.trim()) return [];
                                const formatted = window.SqlFormatter.format(selectedText, {
                                    dialect: window.editorBridge?.activeDialect || 'sqlserver'
                                });
                                return [{
                                    range: range,
                                    text: formatted
                                }];
                            } catch (e) {
                                console.warn('[SqlFormatter] Range formatting failed:', e);
                                return [];
                            }
                        }
                    });
                } catch (fmtErr) {
                    console.warn('[SqlFormatter] Failed to register formatting providers:', fmtErr);
                }
            }

            // Create main editor instance
            const container = document.getElementById("monaco-container");
            editor = monaco.editor.create(container, {
                theme: DARK_THEME,
                fontSize: 13,
                fontFamily: "'JetBrains Mono', 'Cascadia Code', Consolas, monospace",
                fontLigatures: false,
                lineNumbers: "on",
                roundedSelection: false,
                scrollBeyondLastLine: false,
                automaticLayout: true,
                minimap: { enabled: false },
                renderWhitespace: "selection",
                tabSize: 4,
                insertSpaces: true,
                detectIndentation: false,     // always follow Settings (tab size / spaces)
                mouseWheelZoom: false,        // zoom is handled below so it can sync the status bar
                emptySelectionClipboard: true,
                contextmenu: true
            });

            installClipboard();
            installWheelZoom(container);
            if (window.SqlIntellisense) {
                window.editorBridge.applyIntellisenseOptions(window.SqlIntellisense.context());
            }
            if (window.IntelliSenseHost) {
                window.IntelliSenseHost.start(editor);
            }

            // Listen to cursor position (debounced by 40ms to avoid saturating JNI bridge)
            editor.onDidChangeCursorPosition(function (e) {
                if (cursorPositionTimer) clearTimeout(cursorPositionTimer);
                cursorPositionTimer = setTimeout(function () {
                    cursorPositionTimer = null;
                    if (window.javaBridge && window.javaBridge.onCursorPositionChanged) {
                        window.javaBridge.onCursorPositionChanged(e.position.lineNumber, e.position.column);
                    }
                }, 40);
            });

            // Bind Monaco keybindings: F5 and Ctrl+Enter to trigger execution in Java
            editor.addCommand(monaco.KeyCode.F5, function () {
                flushContentChanged();
                if (window.javaBridge && window.javaBridge.onExecuteRequested) {
                    window.javaBridge.onExecuteRequested();
                }
            });
            editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.Enter, function () {
                flushContentChanged();
                if (window.javaBridge && window.javaBridge.onExecuteRequested) {
                    window.javaBridge.onExecuteRequested();
                }
            });

            // Phase 8 / D3: Ctrl+E runs only the statement under the caret
            editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.KeyE, function () {
                flushContentChanged();
                if (window.javaBridge && window.javaBridge.onExecuteStatementRequested) {
                    window.javaBridge.onExecuteStatementRequested();
                }
            });

            // Edit > Go to Beginning / Go to End (source app: Alt+[ / Alt+])
            editor.addCommand(monaco.KeyMod.Alt | monaco.KeyCode.BracketLeft, function () {
                window.editorBridge.gotoBegin();
            });
            editor.addCommand(monaco.KeyMod.Alt | monaco.KeyCode.BracketRight, function () {
                window.editorBridge.gotoEnd();
            });

            // Window resize handler
            window.addEventListener("resize", function () {
                if (editor) editor.layout();
            });

            isReady = true;

            // Notify Java bridge
            if (window.javaBridge && window.javaBridge.onEditorReady) {
                window.javaBridge.onEditorReady();
            }
        });
    }

    // Public Bridge API exposed to Java
    window.editorBridge = {
        isReady: function () {
            return isReady;
        },

        createModel: function (id, content, language) {
            if (!isReady || !window.monaco) return false;
            if (models.has(id)) {
                return true;
            }
            const lang = language || "sql";
            const initialText = content || "";
            const model = monaco.editor.createModel(initialText, lang);

            // Listen to content changes (debounced by 300ms so typing does not stall WebKit on every keystroke)
            model.onDidChangeContent(function () {
                if (contentChangeTimers.has(id)) {
                    clearTimeout(contentChangeTimers.get(id));
                }
                const timer = setTimeout(function () {
                    contentChangeTimers.delete(id);
                    if (window.javaBridge && window.javaBridge.onContentChanged) {
                        window.javaBridge.onContentChanged(id, model.getValue());
                    }
                }, 300);
                contentChangeTimers.set(id, timer);
            });

            models.set(id, { model: model, viewState: null });

            // If no active model, switch to this one
            if (!activeModelId) {
                this.switchModel(id);
            }
            return true;
        },

        flushContentChanged: function (targetId) {
            flushContentChanged(targetId);
        },

        switchModel: function (id) {
            if (!isReady || !editor || !models.has(id)) return false;

            flushContentChanged(activeModelId);

            // Save previous model view state
            if (activeModelId && models.has(activeModelId)) {
                models.get(activeModelId).viewState = editor.saveViewState();
            }

            activeModelId = id;
            const entry = models.get(id);
            editor.setModel(entry.model);

            // Restore view state if previously saved
            if (entry.viewState) {
                editor.restoreViewState(entry.viewState);
            }
            editor.focus();
            return true;
        },

        /** Suggestions are the custom popup (intellisense/): Monaco's own widget stays off. Hover / signature obey the switch. */
        applyIntellisenseOptions: function (c) {
            if (!editor || !c) return;
            if (c.dialect) {
                this.activeDialect = c.dialect;
            }
            const on = c.enabled !== false;
            editor.updateOptions({
                quickSuggestions: false,
                suggestOnTriggerCharacters: false,
                wordBasedSuggestions: "off",
                tabCompletion: "off",
                parameterHints: { enabled: on }
            });
        },

        /** Syntax-check markers (owner "sql-syntax"); an empty list clears them. */
        setMarkers: function (id, markers, owner) {
            if (!window.monaco || !models.has(id)) return false;
            monaco.editor.setModelMarkers(models.get(id).model, owner || "sql-syntax", markers || []);
            return true;
        },

        /** LF ⇄ CRLF of the active model; returns the new sequence name. */
        toggleEol: function () {
            if (!editor || !editor.getModel()) return "LF";
            const m = editor.getModel();
            const crlf = monaco.editor.EndOfLineSequence.CRLF;
            const lf = monaco.editor.EndOfLineSequence.LF;
            const to = m.getEndOfLineSequence() === crlf ? lf : crlf;
            m.pushEOL(to);
            return to === crlf ? "CRLF" : "LF";
        },

        getEol: function () {
            if (!editor || !editor.getModel()) return "LF";
            return editor.getModel().getEndOfLineSequence() === monaco.editor.EndOfLineSequence.CRLF ? "CRLF" : "LF";
        },

        getContent: function (id) {
            const targetId = id || activeModelId;
            flushContentChanged(targetId);
            if (models.has(targetId)) {
                return models.get(targetId).model.getValue();
            }
            return "";
        },

        getSelectedText: function () {
            if (!isReady || !editor) return "";
            const selection = editor.getSelection();
            if (!selection || selection.isEmpty()) return "";
            return editor.getModel().getValueInRange(selection);
        },

        updateContent: function (id, content) {
            const targetId = id || activeModelId;
            if (models.has(targetId)) {
                const model = models.get(targetId).model;
                model.setValue(content || "");
                return true;
            }
            return false;
        },

        disposeModel: function (id) {
            flushContentChanged(id);
            if (models.has(id)) {
                const entry = models.get(id);
                if (activeModelId === id) {
                    activeModelId = null;
                }
                entry.model.dispose();
                models.delete(id);
                return true;
            }
            return false;
        },

        // T1: the app theme's own Monaco theme (built-in palette or imported VS Code theme)
        defineAndSetTheme: function (themeId, themeDef) {
            if (!isReady || !window.monaco || !themeId || !themeDef) return false;
            try {
                monaco.editor.defineTheme(themeId, themeDef);
                monaco.editor.setTheme(themeId);
                return true;
            } catch (e) {
                console.error("defineAndSetTheme failed: " + e);
                return false;
            }
        },

        setTheme: function (themeName) {
            if (!isReady || !window.monaco) return;
            const t = (themeName && themeName.toLowerCase().includes("light")) ? LIGHT_THEME : DARK_THEME;
            monaco.editor.setTheme(t);
        },

        /** Base font size (Settings); the zoom percentage is applied on top. */
        setFontSize: function (size) {
            baseFontSize = Number(size) || 13;
            applyTypography();
        },

        /** Zoom as a percentage of the base font size (status bar / Ctrl+= / Ctrl+-). */
        setZoom: function (pct) {
            setZoomPct(Number(pct) || 100, false);
        },

        getZoom: function () {
            return zoomPct;
        },

        updateOptions: function (opts) {
            if (editor && opts) {
                if (opts.fontSize) { baseFontSize = Number(opts.fontSize) || baseFontSize; }
                if (opts.lineHeight !== undefined) { baseLineHeight = Number(opts.lineHeight); }
                delete opts.fontSize;
                delete opts.lineHeight;
                editor.updateOptions(opts);
                applyTypography();
            }
        },

        setReadOnly: function (readOnly) {
            if (editor) {
                editor.updateOptions({ readOnly: !!readOnly });
            }
        },

        /* DLG-007a: select and reveal a 1-based line */
        gotoLine: function (line) {
            if (!editor || !editor.getModel()) return;
            const model = editor.getModel();
            const n = Math.max(1, Math.min(line, model.getLineCount()));
            editor.setPosition({ lineNumber: n, column: 1 });
            editor.revealLineInCenter(n);
            editor.setSelection({ startLineNumber: n, startColumn: 1, endLineNumber: n, endColumn: model.getLineMaxColumn(n) });
            editor.focus();
        },

        loadSnippets: function (snippetsJson) {
            try {
                if (!snippetsJson) return;
                var list = typeof snippetsJson === "string" ? JSON.parse(snippetsJson) : snippetsJson;
                if (Array.isArray(list)) {
                    window.customSnippets = list;
                }
            } catch (e) {
                console.warn("[editor-bridge] loadSnippets error:", e);
            }
        },

        setActiveCodeStyle: function (styleJson) {
            try {
                const style = typeof styleJson === 'string' ? JSON.parse(styleJson) : styleJson;
                if (window.SqlFormatter) {
                    window.SqlFormatter.setActiveStyle(style);
                }
            } catch (e) {
                console.warn('[SqlFormatter] Error setting active style:', e);
            }
        },

        formatDocument: function () {
            if (editor) {
                const action = editor.getAction("editor.action.formatDocument");
                if (action) {
                    action.run();
                } else if (window.SqlFormatter && editor.getModel()) {
                    const model = editor.getModel();
                    const formatted = window.SqlFormatter.format(model.getValue(), {
                        dialect: window.editorBridge?.activeDialect || 'sqlserver'
                    });
                    editor.executeEdits("format-document", [{
                        range: model.getFullModelRange(),
                        text: formatted
                    }]);
                }
            }
        },

        formatSelection: function () {
            if (editor) {
                const sel = editor.getSelection();
                if (sel && !sel.isEmpty()) {
                    const action = editor.getAction("editor.action.formatSelection");
                    if (action) {
                        action.run();
                    } else if (window.SqlFormatter && editor.getModel()) {
                        const model = editor.getModel();
                        const selectedText = model.getValueInRange(sel);
                        const formatted = window.SqlFormatter.format(selectedText, {
                            dialect: window.editorBridge?.activeDialect || 'sqlserver'
                        });
                        editor.executeEdits("format-selection", [{
                            range: sel,
                            text: formatted
                        }]);
                    }
                } else {
                    this.formatDocument();
                }
            }
        },

        layout: function () {
            if (editor) {
                editor.layout();
            }
        },

        gotoBegin: function () {
            if (!editor) return;
            editor.setPosition({ lineNumber: 1, column: 1 });
            editor.revealLine(1);
            editor.focus();
        },

        gotoEnd: function () {
            if (!editor || !editor.getModel()) return;
            const model = editor.getModel();
            const line = model.getLineCount();
            const col = model.getLineMaxColumn(line);
            editor.setPosition({ lineNumber: line, column: col });
            editor.revealLine(line);
            editor.focus();
        },

        /* Object Content Search decorations & navigation */
        setSearchDecorations: function (matchesJson) {
            if (!editor) return;
            try {
                const matches = typeof matchesJson === "string" ? JSON.parse(matchesJson) : matchesJson;
                if (!window._searchDecorations) window._searchDecorations = [];
                const newDecs = (matches || []).map(function (m) {
                    return {
                        range: new monaco.Range(m.line, m.column, m.line, m.column + m.length),
                        options: {
                            inlineClassName: 'ocs-monaco-match',
                            overviewRuler: {
                                color: 'rgba(234, 179, 8, 0.8)',
                                position: monaco.editor.OverviewRulerLane.Right
                            }
                        }
                    };
                });
                window._searchDecorations = editor.deltaDecorations(window._searchDecorations, newDecs);
            } catch (e) {
                console.warn("[editor-bridge] setSearchDecorations error:", e);
            }
        },

        setActiveSearchMatch: function (line, column, length) {
            if (!editor) return;
            try {
                if (!window._activeSearchDec) window._activeSearchDec = [];
                const len = Number(length) || 1;
                const ln = Number(line) || 1;
                const col = Number(column) || 1;
                const activeDec = [{
                    range: new monaco.Range(ln, col, ln, col + len),
                    options: {
                        inlineClassName: 'ocs-monaco-match-active',
                        overviewRuler: {
                            color: '#f97316',
                            position: monaco.editor.OverviewRulerLane.Full
                        }
                    }
                }];
                window._activeSearchDec = editor.deltaDecorations(window._activeSearchDec, activeDec);
                editor.revealLineInCenter(ln);
                editor.setPosition({ lineNumber: ln, column: col });
                editor.focus();
            } catch (e) {
                console.warn("[editor-bridge] setActiveSearchMatch error:", e);
            }
        },

        clearSearchDecorations: function () {
            if (!editor) return;
            try {
                if (window._searchDecorations && window._searchDecorations.length) {
                    window._searchDecorations = editor.deltaDecorations(window._searchDecorations, []);
                }
                if (window._activeSearchDec && window._activeSearchDec.length) {
                    window._activeSearchDec = editor.deltaDecorations(window._activeSearchDec, []);
                }
            } catch (e) {
                console.warn("[editor-bridge] clearSearchDecorations error:", e);
            }
        }
    };

    // Callback when Java attaches javaBridge after page loads
    window.onJavaBridgeAttached = function () {
        if (isReady && window.javaBridge && window.javaBridge.onEditorReady) {
            window.javaBridge.onEditorReady();
        }
    };

    // Initialize immediately without waiting for DOMContentLoaded (container is already in DOM)
    initMonaco();

})(window);

