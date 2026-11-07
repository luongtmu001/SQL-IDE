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

    public void format() {
        host.formatDocument();
    }
}
