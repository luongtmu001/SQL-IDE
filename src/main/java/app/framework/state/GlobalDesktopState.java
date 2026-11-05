package app.framework.state;

import javafx.beans.property.*;

/**
 * Observable Global State for JavaFX Desktop Application (Phase 4).
 * Bridges Core domain events and UI components reactively.
 */
public class GlobalDesktopState {

    private static GlobalDesktopState instance;

    private final StringProperty activeWorkspaceId = new SimpleStringProperty("");
    private final StringProperty activeWorkspaceName = new SimpleStringProperty("Default Workspace");
    private final StringProperty activeProject = new SimpleStringProperty("");
    private final StringProperty activeConnection = new SimpleStringProperty("");
    private final StringProperty activeCatalog = new SimpleStringProperty("");
    private final StringProperty activeSchema = new SimpleStringProperty("public");
    private final StringProperty activeDialect = new SimpleStringProperty("PostgreSQL 16");
    private final BooleanProperty busy = new SimpleBooleanProperty(false);
    private final StringProperty statusMessage = new SimpleStringProperty("Ready");

    private GlobalDesktopState() {}

    public static synchronized GlobalDesktopState getInstance() {
        if (instance == null) {
            instance = new GlobalDesktopState();
        }
        return instance;
    }

    public StringProperty activeWorkspaceIdProperty() {
        return activeWorkspaceId;
    }

    public String getActiveWorkspaceId() {
        return activeWorkspaceId.get();
    }

    public void setActiveWorkspaceId(String activeWorkspaceId) {
        this.activeWorkspaceId.set(activeWorkspaceId);
    }

    public StringProperty activeWorkspaceNameProperty() {
        return activeWorkspaceName;
    }

    public String getActiveWorkspaceName() {
        return activeWorkspaceName.get();
    }

    public void setActiveWorkspaceName(String activeWorkspaceName) {
        this.activeWorkspaceName.set(activeWorkspaceName);
    }

    public StringProperty activeProjectProperty() {
        return activeProject;
    }

    public String getActiveProject() {
        return activeProject.get();
    }

    public void setActiveProject(String activeProject) {
        this.activeProject.set(activeProject);
    }

    public StringProperty activeConnectionProperty() {
        return activeConnection;
    }

    public String getActiveConnection() {
        return activeConnection.get();
    }

    public void setActiveConnection(String activeConnection) {
        this.activeConnection.set(activeConnection);
    }

    public StringProperty activeCatalogProperty() {
        return activeCatalog;
    }

    public String getActiveCatalog() {
        return activeCatalog.get();
    }

    public void setActiveCatalog(String activeCatalog) {
        this.activeCatalog.set(activeCatalog);
    }

    public StringProperty activeSchemaProperty() {
        return activeSchema;
    }

    public String getActiveSchema() {
        return activeSchema.get();
    }

    public void setActiveSchema(String activeSchema) {
        this.activeSchema.set(activeSchema);
    }

    public StringProperty activeDialectProperty() {
        return activeDialect;
    }

    public String getActiveDialect() {
        return activeDialect.get();
    }

    public void setActiveDialect(String activeDialect) {
        this.activeDialect.set(activeDialect);
    }

    public BooleanProperty busyProperty() {
        return busy;
    }

    public boolean isBusy() {
        return busy.get();
    }

    public void setBusy(boolean busy) {
        this.busy.set(busy);
    }

    public StringProperty statusMessageProperty() {
        return statusMessage;
    }

    public String getStatusMessage() {
        return statusMessage.get();
    }

    public void setStatusMessage(String statusMessage) {
        this.statusMessage.set(statusMessage);
    }
}

