package app.ui.editor;

import java.util.function.Consumer;

/**
 * One Monaco editor inside a dialog (Settings JSON, code style preview): one model, app theme and
 * editor font, change callback that ignores the text set from Java.
 */
public final class EmbeddedMonaco {

    private final MonacoEditorHost host = new MonacoEditorHost();
    private final String modelId;
    private String lastSet = "";
    private Consumer<String> onUserEdit;

    public EmbeddedMonaco(String modelId, String language, String initial) {
        this.modelId = modelId;
        this.lastSet = initial != null ? initial : "";
        host.getStyleClass().add("embedded-monaco");
        host.createModel(modelId, lastSet, language);
        host.switchModel(modelId);
        host.setOnReady(() -> {
            host.applyAppTheme(); // T1: same Monaco theme as the main editor
            host.updateEditorSettings(app.services.SettingsService.getInstance().getCurrentSettings());
        });
        host.setOnContentChanged((id, text) -> {
            if (!modelId.equals(id) || text == null || text.equals(lastSet)) return;
            if (onUserEdit != null) onUserEdit.accept(text);
        });
    }

    public MonacoEditorHost node() {
        return host;
    }

    /** Typed / pasted by the user (not text set with {@link #setText}). */
    public void setOnUserEdit(Consumer<String> c) {
        onUserEdit = c;
    }

    public void setOnCursorPositionChanged(java.util.function.BiConsumer<Integer, Integer> c) {
        host.setOnCursorPositionChanged(c);
    }

    public void setText(String text) {
        String t = text != null ? text : "";
        if (t.equals(getText())) return;
        lastSet = t;
        host.updateContent(modelId, t);
    }

    public String getText() {
        return host.getContent(modelId);
    }

    public void setReadOnly(boolean ro) {
        host.setReadOnly(ro);
    }

    public void setShowStatusBar(boolean show) {
        host.getEditorBar().setVisible(show);
        host.getEditorBar().setManaged(show);
    }

    public void format() {
        host.formatDocument();
    }

    public void gotoLine(int line) {
        host.gotoLine(line);
    }

    public void highlightSearchMatches(java.util.List<com.lltool.core.domain.objectsearch.model.SearchMatchLocation> matches) {
        if (matches == null || matches.isEmpty()) {
            host.clearSearchDecorations();
            return;
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < matches.size(); i++) {
            var m = matches.get(i);
            if (i > 0) sb.append(",");
            sb.append("{\"line\":").append(m.line())
              .append(",\"column\":").append(m.column())
              .append(",\"length\":").append(m.length()).append("}");
        }
        sb.append("]");
        host.setSearchDecorations(sb.toString());
    }

    public void setActiveSearchMatch(int line, int column, int length) {
        host.setActiveSearchMatch(line, column, length);
    }

    public void clearSearchHighlights() {
        host.clearSearchDecorations();
    }
}
