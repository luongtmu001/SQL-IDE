package app.ui.editor;

import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.scene.layout.StackPane;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import netscape.javascript.JSObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.IntConsumer;

/**
 * MonacoEditorHost — Encapsulates Monaco Editor in a single JavaFX WebView.
 * Implements a robust Java <-> JavaScript bridge with multi-model support,
 * allowing instant tab switching without reloading or creating multiple WebViews.
 */
public class MonacoEditorHost extends StackPane {

    private static final Logger log = LoggerFactory.getLogger(MonacoEditorHost.class);

    private final EditorBar editorBar = new EditorBar();
    private final WebView webView;
    private final WebEngine webEngine;
    private boolean isEditorReady = false;

    // Queued actions for when Monaco is still loading
    private final List<Runnable> readyCallbacks = new ArrayList<>();
    private final Map<String, ModelDefinition> modelCache = new HashMap<>();
    private String activeModelId = null;

    // Event listeners
    private BiConsumer<String, String> onContentChangedListener;
    private BiConsumer<Integer, Integer> onCursorPositionListener;
    private Runnable onReadyListener;
    private Runnable onExecuteRequestListener;
    private Runnable onExecuteStatementRequestListener;
    private IntConsumer onZoomChangedListener;
    private int zoomPercent = 100;

    private record ModelDefinition(String id, String content, String language) {}

    public MonacoEditorHost() {
        this.webView = new WebView();
        this.webEngine = webView.getEngine();

        // Set transparent page fill and initial opacity 0 to eliminate black flash / flicker during startup
        webView.setPageFill(javafx.scene.paint.Color.TRANSPARENT);
        webView.setOpacity(0.0);

        // The editor with its small bar underneath (zoom, PID, cursor, indentation ...), like SSMS
        javafx.scene.layout.BorderPane inner = new javafx.scene.layout.BorderPane();
        inner.setCenter(webView);
        inner.setBottom(editorBar);
        getChildren().add(inner);
        inner.prefWidthProperty().bind(widthProperty());
        inner.prefHeightProperty().bind(heightProperty());
        editorBar.setOnToggleEol(this::toggleEol);
        // the editor colours come from the scene's theme: apply them as soon as the host is in a scene
        sceneProperty().addListener((o, a, s) -> {
            if (s != null) applyAppTheme();
        });

        setupWebEngine();
    }

    private void setupWebEngine() {
        webEngine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {
                attachJavaBridge();
            } else if (newState == Worker.State.FAILED) {
                log.error("Failed to load Monaco editor HTML: {}", webEngine.getLoadWorker().getMessage());
            }
        });

        URL editorHtmlUrl = getClass().getResource("/monaco/editor.html");
        if (editorHtmlUrl != null) {
            webEngine.load(editorHtmlUrl.toExternalForm());
        } else {
            log.error("Monaco editor.html resource not found at /monaco/editor.html");
        }
    }

    private void attachJavaBridge() {
        try {
            JSObject window = (JSObject) webEngine.executeScript("window");
            window.setMember("javaBridge", new BridgeCallback());
            webEngine.executeScript("if (window.onJavaBridgeAttached) window.onJavaBridgeAttached();");
            log.debug("Attached javaBridge to Monaco WebView window");
        } catch (Exception e) {
            log.error("Failed to attach javaBridge: {}", e.getMessage(), e);
        }
    }

    /**
     * Callback object exposed to JavaScript window.javaBridge.
     */
    public class BridgeCallback {
        public void onEditorReady() {
            Platform.runLater(() -> {
                isEditorReady = true;
                log.info("Monaco Editor is fully initialized and ready.");

                // Re-play cached models
                for (ModelDefinition def : modelCache.values()) {
                    executeJs("window.editorBridge.createModel('" + escapeJs(def.id) + "', " +
                            "decodeURIComponent('" + encodeUri(def.content) + "'), '" + escapeJs(def.language) + "');");
                }
                if (activeModelId != null) {
                    executeJs("window.editorBridge.switchModel('" + escapeJs(activeModelId) + "');");
                }

                if (zoomPercent != 100) {
                    executeJs("window.editorBridge.setZoom(" + zoomPercent + ");");
                }

                // Run ready callbacks
                for (Runnable r : readyCallbacks) {
                    try {
                        r.run();
                    } catch (Exception e) {
                        log.error("Error executing ready callback: {}", e.getMessage());
                    }
                }
                readyCallbacks.clear();

                // Smoothly fade in WebView with zero black flash
                javafx.animation.FadeTransition fadeIn = new javafx.animation.FadeTransition(
                        javafx.util.Duration.millis(120), webView);
                fadeIn.setFromValue(0.0);
                fadeIn.setToValue(1.0);
                fadeIn.play();

                if (onReadyListener != null) {
                    onReadyListener.run();
                }
            });
        }

        public void onContentChanged(String modelId, String newContent) {
            Platform.runLater(() -> {
                if (modelCache.containsKey(modelId)) {
                    ModelDefinition old = modelCache.get(modelId);
                    modelCache.put(modelId, new ModelDefinition(modelId, newContent, old.language));
                }
                if (onContentChangedListener != null) {
                    onContentChangedListener.accept(modelId, newContent);
                }
            });
        }

        public void onCursorPositionChanged(int line, int col) {
            Platform.runLater(() -> {
                if (onCursorPositionListener != null) {
                    onCursorPositionListener.accept(line, col);
                }
            });
        }

        /** System clipboard, read by Monaco paste (called on the FX thread by the WebView). */
        public String getClipboardText() {
            try {
                String s = javafx.scene.input.Clipboard.getSystemClipboard().getString();
                return s == null ? "" : s.replace("\r\n", "\n");
            } catch (Exception e) {
                log.warn("Clipboard read failed: {}", e.getMessage());
                return "";
            }
        }

        /** Monaco copy/cut: put the text on the system clipboard (Windows line endings). */
        public void setClipboardText(String text) {
            try {
                String t = text == null ? "" : text.replace("\r\n", "\n").replace("\n", System.lineSeparator());
                javafx.scene.input.ClipboardContent cc = new javafx.scene.input.ClipboardContent();
                cc.putString(t);
                javafx.scene.input.Clipboard.getSystemClipboard().setContent(cc);
            } catch (Exception e) {
                log.warn("Clipboard write failed: {}", e.getMessage());
            }
        }

        /** Ctrl+Wheel zoom inside the editor. */
        public void onZoomChanged(Object value) {
            final int pct = value instanceof Number n ? (int) Math.round(n.doubleValue()) : zoomPercent;
            log.debug("Editor zoom (Ctrl+Wheel) -> {}%", pct);
            Platform.runLater(() -> {
                zoomPercent = pct;
                if (onZoomChangedListener != null) {
                    onZoomChangedListener.accept(pct);
                }
            });
        }

        public void onExecuteStatementRequested() {
            Platform.runLater(() -> {
                if (onExecuteStatementRequestListener != null) {
                    onExecuteStatementRequestListener.run();
                }
            });
        }

        public void onExecuteRequested() {
            Platform.runLater(() -> {
                if (onExecuteRequestListener != null) {
                    onExecuteRequestListener.run();
                }
            });
        }
    }

    public boolean isReady() {
        return isEditorReady;
    }

    public boolean isEditorReady() {
        return isEditorReady;
    }

    public String getSelectedText() {
        if (!isEditorReady) return "";
        try {
            Object res = webEngine.executeScript("window.editorBridge ? window.editorBridge.getSelectedText() : '';");
            return res != null ? res.toString() : "";
        } catch (Exception e) {
            return "";
        }
    }

    public void setOnExecuteStatementRequested(Runnable listener) {
        this.onExecuteStatementRequestListener = listener;
    }

    public void setOnExecuteRequested(Runnable listener) {
        this.onExecuteRequestListener = listener;
    }

    public void createModel(String id, String content, String language) {
        String lang = (language != null && !language.isBlank()) ? language : "sql";
        String initial = content != null ? content : "";
        modelCache.put(id, new ModelDefinition(id, initial, lang));

        runWhenReady(() -> {
            executeJs("window.editorBridge.createModel('" + escapeJs(id) + "', " +
                    "decodeURIComponent('" + encodeUri(initial) + "'), '" + escapeJs(lang) + "');");
        });
    }

    public void switchModel(String id) {
        this.activeModelId = id;
        runWhenReady(() -> {
            executeJs("window.editorBridge.switchModel('" + escapeJs(id) + "');");
        });
    }

    public void updateContent(String id, String content) {
        String finalContent = content != null ? content : "";
        if (modelCache.containsKey(id)) {
            ModelDefinition old = modelCache.get(id);
            modelCache.put(id, new ModelDefinition(id, finalContent, old.language));
        }
        runWhenReady(() -> {
            executeJs("window.editorBridge.updateContent('" + escapeJs(id) + "', " +
                    "decodeURIComponent('" + encodeUri(finalContent) + "'));");
        });
    }

    public String getContent(String id) {
        if (!isEditorReady) {
            ModelDefinition def = modelCache.get(id);
            return def != null ? def.content : "";
        }
        try {
            Object res = webEngine.executeScript("window.editorBridge.getContent('" + escapeJs(id) + "');");
            return res != null ? res.toString() : "";
        } catch (Exception e) {
            ModelDefinition def = modelCache.get(id);
            return def != null ? def.content : "";
        }
    }

    public void disposeModel(String id) {
        modelCache.remove(id);
        if (Objects.equals(activeModelId, id)) {
            activeModelId = null;
        }
        runWhenReady(() -> {
            executeJs("window.editorBridge.disposeModel('" + escapeJs(id) + "');");
        });
    }

    /**
     * T1: the editor shows the app's current theme with its own Monaco theme (imported: from the VS Code theme;
     * built in: its palette + the scene's AtlantaFX colours). Replaces the shared "ide-dark" / "ide-light".
     */
    public void applyAppTheme() {
        runWhenReady(() -> {
            var def = app.services.ThemeService.getInstance().getCurrentDefinition();
            String json = app.services.theme.MonacoThemes.json(def, getScene());
            executeJs("window.editorBridge.defineAndSetTheme('" + escapeJs(app.services.theme.MonacoThemes.monacoId(def))
                    + "', " + json + ");");
        });
    }

    public void setTheme(String themeName) {
        runWhenReady(() -> {
            executeJs("window.editorBridge.setTheme('" + escapeJs(themeName) + "');");
        });
    }

    /** Base font size from Settings; the current zoom is applied on top of it. */
    public void setFontSize(int size) {
        runWhenReady(() -> {
            executeJs("window.editorBridge.setFontSize(" + size + ");");
        });
    }

    /** Editor zoom in percent of the Settings font size. */
    public void setZoom(int percent) {
        zoomPercent = percent;
        runWhenReady(() -> executeJs("window.editorBridge.setZoom(" + percent + ");"));
    }

    public int getZoom() {
        return zoomPercent;
    }

    public void setOnZoomChanged(IntConsumer listener) {
        this.onZoomChangedListener = listener;
    }

    public void setReadOnly(boolean readOnly) {
        runWhenReady(() -> {
            executeJs("window.editorBridge.setReadOnly(" + readOnly + ");");
        });
    }

    /** Shows the syntax diagnostics of a document as squiggles (replaces the previous ones; empty clears). */
    public void setMarkers(String modelId, java.util.List<app.framework.syntax.SyntaxDiagnostic> diagnostics) {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (var d : diagnostics) {
            if (!first) sb.append(',');
            first = false;
            sb.append(String.format(java.util.Locale.ROOT,
                    "{\"startLineNumber\":%d,\"startColumn\":%d,\"endLineNumber\":%d,\"endColumn\":%d,\"severity\":%d,\"message\":\"%s\",\"source\":\"SQL\"}",
                    d.line(), d.column(), d.endLine(), d.endColumn(), app.framework.syntax.SyntaxDiagnostic.SEVERITY_ERROR,
                    jsonString(d.message())));
        }
        sb.append(']');
        String json = sb.toString();
        runWhenReady(() -> executeJs("window.editorBridge.setMarkers('" + escapeJs(modelId)
                + "', JSON.parse(decodeURIComponent('" + encodeUri(json) + "')), 'sql-syntax');"));
    }

    private static String jsonString(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> { }
                case '\t' -> b.append("\\t");
                default -> b.append(c < 0x20 ? ' ' : c);
            }
        }
        return b.toString();
    }

    public void formatDocument() {
        runWhenReady(() -> {
            executeJs("window.editorBridge.formatDocument();");
        });
    }

    /** Selects and reveals a 1-based line in the active document (DLG-007a navigation). */
    public void gotoLine(int line) {
        runWhenReady(() -> executeJs("window.editorBridge.gotoLine(" + line + ");"));
    }

    /** Moves the caret to the first line / column of the active document. */
    public void gotoBegin() {
        runWhenReady(() -> executeJs("window.editorBridge.gotoBegin();"));
    }

    /** Moves the caret to the end of the active document. */
    public void gotoEnd() {
        runWhenReady(() -> executeJs("window.editorBridge.gotoEnd();"));
    }

    public void requestEditorLayout() {
        runWhenReady(() -> {
            executeJs("window.editorBridge.layout();");
        });
    }

    /** IntelliSense context of the active tab: dialect (SQLSERVER / POSTGRESQL / SQLITE / OTHER) and Settings. */
    public void setIntellisense(boolean enabled, String dialect, String keywordCase, boolean mru) {
        runWhenReady(() -> executeJs(String.format(java.util.Locale.ROOT,
                "if (window.SqlIntellisense) window.SqlIntellisense.setContext({ enabled: %b, dialect: '%s', keywordCase: '%s', mru: %b });",
                enabled, escapeJs(dialect == null ? "OTHER" : dialect),
                escapeJs(keywordCase == null ? "upper" : keywordCase), mru)));
    }

    public void updateEditorSettings(app.models.IdeSettings s) {
        if (s == null) return;
        runWhenReady(() -> {
            String fontFam = s.getEditorFontFamily();
            if (fontFam == null || fontFam.isBlank()) fontFam = "Cascadia Mono Light";
            fontFam = fontFam.replace("'", "\\'");

            double lh = s.getEditor() != null ? s.getEditor().getLineHeight() : 1.5;
            double ls = s.getEditor() != null ? s.getEditor().getLetterSpacing() : 0.5;
            boolean insertSpaces = s.getEditor() != null && s.getEditor().isInsertSpaces();

            String json = String.format(
                    java.util.Locale.ROOT,
                    "{ fontFamily: '%s, Consolas, monospace', fontSize: %d, lineHeight: %.2f, letterSpacing: %.2f, tabSize: %d, insertSpaces: %b, wordWrap: '%s', lineNumbers: '%s', minimap: { enabled: %b } }",
                    fontFam,
                    s.getEditorFontSize(),
                    lh,
                    ls,
                    s.getTabSize(),
                    insertSpaces,
                    s.isWordWrap() ? "on" : "off",
                    s.isShowLineNumbers() ? "on" : "off",
                    s.isMinimap()
            );
            executeJs("window.editorBridge.updateOptions(" + json + ");");
        });
    }

    @Override
    protected void layoutChildren() {
        super.layoutChildren();
        if (isEditorReady) {
            executeJs("window.editorBridge.layout();");
        }
    }

    private void runWhenReady(Runnable action) {
        if (isEditorReady) {
            if (Platform.isFxApplicationThread()) {
                action.run();
            } else {
                Platform.runLater(action);
            }
        } else {
            readyCallbacks.add(action);
        }
    }

    private void executeJs(String script) {
        try {
            webEngine.executeScript(script);
        } catch (Exception e) {
            log.warn("Failed to execute JS: {} -> {}", script, e.getMessage());
        }
    }

    private String escapeJs(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                   .replace("'", "\\'")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "");
    }

    private String encodeUri(String text) {
        if (text == null) return "";
        try {
            return java.net.URLEncoder.encode(text, java.nio.charset.StandardCharsets.UTF_8)
                    .replace("+", "%20");
        } catch (Exception e) {
            return escapeJs(text);
        }
    }

    public void setOnContentChanged(BiConsumer<String, String> listener) {
        this.onContentChangedListener = listener;
    }

    public void setOnCursorPositionChanged(BiConsumer<Integer, Integer> listener) {
        this.onCursorPositionListener = listener;
    }

    public void setOnReady(Runnable callback) {
        this.onReadyListener = callback;
    }

    /** LF ⇄ CRLF for the active document; the bar shows the result. */
    public void toggleEol() {
        if (!isEditorReady) return;
        try {
            Object r = webEngine.executeScript("window.editorBridge.toggleEol();");
            if (r != null) editorBar.setLineSeparator(r.toString());
        } catch (Exception e) {
            log.warn("toggleEol failed: {}", e.getMessage());
        }
    }

    public EditorBar getEditorBar() {
        return editorBar;
    }

    public WebView getWebView() {
        return webView;
    }
}
