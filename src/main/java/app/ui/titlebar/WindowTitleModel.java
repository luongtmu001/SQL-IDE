package app.ui.titlebar;

import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class WindowTitleModel {

    private static final WindowTitleModel INSTANCE = new WindowTitleModel();

    private final StringProperty appName = new SimpleStringProperty("SQL IDE");
    private final StringProperty documentTitle = new SimpleStringProperty("");
    private final StringProperty themeName = new SimpleStringProperty("");
    private final StringProperty fullTitle = new SimpleStringProperty();

    private WindowTitleModel() {
        fullTitle.bind(Bindings.createStringBinding(() -> {
            StringBuilder sb = new StringBuilder(appName.get());
            if (documentTitle.get() != null && !documentTitle.get().isBlank()) {
                sb.append(" — ").append(documentTitle.get());
            }
            if (themeName.get() != null && !themeName.get().isBlank()) {
                sb.append(" [").append(themeName.get()).append("]");
            }
            return sb.toString();
        }, appName, documentTitle, themeName));
    }

    public static WindowTitleModel getInstance() {
        return INSTANCE;
    }

    public StringProperty appNameProperty() {
        return appName;
    }

    public String getAppName() {
        return appName.get();
    }

    public void setAppName(String name) {
        this.appName.set(name);
    }

    public StringProperty documentTitleProperty() {
        return documentTitle;
    }

    public String getDocumentTitle() {
        return documentTitle.get();
    }

    public void setDocumentTitle(String docTitle) {
        this.documentTitle.set(docTitle);
    }

    public StringProperty themeNameProperty() {
        return themeName;
    }

    public String getThemeName() {
        return themeName.get();
    }

    public void setThemeName(String theme) {
        this.themeName.set(theme);
    }

    public StringProperty fullTitleProperty() {
        return fullTitle;
    }

    public String getFullTitle() {
        return fullTitle.get();
    }
}

