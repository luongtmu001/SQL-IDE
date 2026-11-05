// editor-bridge.js — Monaco Editor Bridge for JavaFX SQL IDE
(function (window) {
    "use strict";

    let editor = null;
    let activeModelId = null;
    const models = new Map(); // id -> { model, viewState }
    let isReady = false;

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

            // Create main editor instance
            const container = document.getElementById("monaco-container");
            editor = monaco.editor.create(container, {
                theme: DARK_THEME,
                fontSize: 13,
                fontFamily: "JetBrains Mono, Cascadia Code, Consolas, monospace",
                lineNumbers: "on",
                roundedSelection: false,
                scrollBeyondLastLine: false,
                automaticLayout: true,
                minimap: { enabled: false },
                renderWhitespace: "selection",
                tabSize: 4,
                insertSpaces: true
            });

            // Listen to cursor position
            editor.onDidChangeCursorPosition(function (e) {
                if (window.javaBridge && window.javaBridge.onCursorPositionChanged) {
                    window.javaBridge.onCursorPositionChanged(e.position.lineNumber, e.position.column);
                }
            });

            // Bind Monaco keybindings: F5 and Ctrl+Enter to trigger execution in Java
            editor.addCommand(monaco.KeyCode.F5, function () {
                if (window.javaBridge && window.javaBridge.onExecuteRequested) {
                    window.javaBridge.onExecuteRequested();
                }
            });
            editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.Enter, function () {
                if (window.javaBridge && window.javaBridge.onExecuteRequested) {
                    window.javaBridge.onExecuteRequested();
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

            // Listen to content changes
            model.onDidChangeContent(function () {
                if (window.javaBridge && window.javaBridge.onContentChanged) {
                    window.javaBridge.onContentChanged(id, model.getValue());
                }
            });

            models.set(id, { model: model, viewState: null });

            // If no active model, switch to this one
            if (!activeModelId) {
                this.switchModel(id);
            }
            return true;
        },

        switchModel: function (id) {
            if (!isReady || !editor || !models.has(id)) return false;

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

        getContent: function (id) {
            const targetId = id || activeModelId;
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

        setTheme: function (themeName) {
            if (!isReady || !window.monaco) return;
            const t = (themeName && themeName.toLowerCase().includes("light")) ? LIGHT_THEME : DARK_THEME;
            monaco.editor.setTheme(t);
        },

        setFontSize: function (size) {
            if (editor) {
                editor.updateOptions({ fontSize: Number(size) || 13 });
            }
        },

        updateOptions: function (opts) {
            if (editor && opts) {
                if (opts.lineHeight && opts.lineHeight > 0 && opts.lineHeight <= 4 && opts.fontSize) {
                    opts.lineHeight = Math.max(opts.fontSize, Math.round(opts.fontSize * opts.lineHeight));
                }
                editor.updateOptions(opts);
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

        formatDocument: function () {
            if (editor) {
                editor.getAction("editor.action.formatDocument").run();
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

