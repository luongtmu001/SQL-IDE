package app.controllers;

import app.framework.async.AsyncTaskManager;
import app.framework.bootstrap.DesktopBootstrap;
import app.framework.connection.ConnectionSessionManager;
import app.framework.metadata.MockMetadataProvider;
import app.framework.state.GlobalDesktopState;
import app.models.DatabaseNode;
import app.models.DatabaseNodeType;
import app.ui.components.ConnectionTreeBuilder;
import app.ui.components.DatabaseTreeCell;
import app.services.ExplorerGroupService;
import app.services.NotificationService;
import app.services.PasswordPolicyService;
import app.ui.dialogs.ConfirmDialog;
import com.lltool.core.api.connection.ConnectionApi;
import com.lltool.core.api.request.CreateConnectionRequest;
import com.lltool.core.api.request.TestConnectionDraftRequest;
import com.lltool.core.api.request.UpdateConnectionRequest;
import com.lltool.core.application.connection.dto.ConnectionProfileDto;
import com.lltool.core.common.result.Result;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.prefs.Preferences;

/**
 * SCR-DLG-001 Connection Manager (Phase 2B, Gate B 2026-11-05).
 *
 * <ul>
 *   <li>Q1 = b: list of saved connections (grouped like the Explorer) + form.</li>
 *   <li>Q2 = a: "Kiểm tra" and the ↻ database / schema buttons answer with mock data while
 *       {@link MockMetadataProvider} is on; no real database connection.</li>
 *   <li>Q3 = a: saved password is encrypted by the credential vault and valid 30 days from the last
 *       save ({@link PasswordPolicyService}).</li>
 *   <li>Database type: selection with the product logo next to it.</li>
 * </ul>
 */
public class ConnectionManagerController implements Initializable {

    private static final Logger log = LoggerFactory.getLogger(ConnectionManagerController.class);
    private static final List<String> DB_TYPES = List.of("PostgreSQL", "SQL Server", "SQLite");
    private static final Map<String, Image> LOGOS = new HashMap<>();
    private static final Preferences SCHEMA_PREFS =
            Preferences.userNodeForPackage(ConnectionManagerController.class).node("default_schema");

    @FXML private HBox dialogRoot;

    @FXML private Button newButton;
    @FXML private Button duplicateButton;
    @FXML private Button deleteButton;
    @FXML private TextField searchField;
    @FXML private TreeView<DatabaseNode> connectionTree;

    @FXML private ImageView headerLogo;
    @FXML private Label headerTitle;
    @FXML private Label headerSubtitle;
    @FXML private VBox formBox;

    @FXML private ComboBox<String> dbTypeComboBox;
    @FXML private TextField nameField;
    @FXML private ComboBox<String> groupComboBox;
    @FXML private ComboBox<String> envComboBox;
    @FXML private CheckBox readOnlyCheckBox;

    @FXML private Label serverSection;
    @FXML private Region hostPortRow;
    @FXML private TextField hostField;
    @FXML private Spinner<Integer> portSpinner;
    @FXML private Region sqliteRow;
    @FXML private TextField sqliteFileField;
    @FXML private Button chooseFileButton;
    @FXML private ComboBox<String> databaseComboBox;
    @FXML private Button fetchDbsButton;
    @FXML private ComboBox<String> schemaComboBox;
    @FXML private Button fetchSchemasButton;

    @FXML private Label authSection;
    @FXML private Region authTypeRow;
    @FXML private CheckBox windowsAuthSwitch;
    @FXML private Region usernameRow;
    @FXML private TextField usernameField;
    @FXML private Region passwordRow;
    @FXML private Button advancedToggle;
    @FXML private FontIcon advancedIcon;
    @FXML private Label advancedSummary;
    @FXML private Region advancedBox;
    @FXML private PasswordField passwordField;
    @FXML private TextField passwordVisibleField;
    @FXML private ToggleButton showPasswordToggle;
    @FXML private FontIcon showPasswordIcon;
    @FXML private Region savePasswordRow;
    @FXML private CheckBox savePasswordCheckBox;
    @FXML private Label passwordExpiryLabel;

    @FXML private Spinner<Integer> timeoutSpinner;
    @FXML private CheckBox trustCertCheckBox;

    @FXML private Button testButton;
    @FXML private ProgressIndicator testProgressIndicator;
    @FXML private Label statusLabel;
    @FXML private Button saveButton;
    @FXML private Button connectNowButton;
    @FXML private Button closeButton;

    private final List<ConnectionProfileDto> profiles = new ArrayList<>();
    private String currentConnectionId = null;
    /** Set by "Lưu & kết nối": the Explorer opens this connection after the dialog closes. */
    private String connectRequestedId = null;
    private boolean suppressSelection = false;

    // ------------------------------------------------------------------ init

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupSpinners();
        setupFormControls();
        setupTree();
        setupPasswordField();
        searchField.textProperty().addListener((o, a, b) -> rebuildTree());
        loadConnections();
    }

    private void setupSpinners() {
        portSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 65535, 5432));
        portSpinner.getEditor().textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && newVal.matches("\\d+")) {
                try {
                    int p = Integer.parseInt(newVal);
                    if (p >= 1 && p <= 65535) portSpinner.getValueFactory().setValue(p);
                } catch (NumberFormatException ignored) {
                }
            }
        });
        timeoutSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 300, 15));
    }

    private void setupFormControls() {
        dbTypeComboBox.setItems(FXCollections.observableArrayList(DB_TYPES));
        dbTypeComboBox.setCellFactory(lv -> new LogoCell());
        dbTypeComboBox.setButtonCell(new LogoCell());
        dbTypeComboBox.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> applyTypeLayout(b));

        envComboBox.setItems(FXCollections.observableArrayList("DEV", "TEST", "STAGING", "PROD"));
        windowsAuthSwitch.selectedProperty().addListener((o, a, b) -> applyTypeLayout(dbTypeComboBox.getValue()));
        savePasswordCheckBox.selectedProperty().addListener((o, a, b) -> updatePasswordExpiry());
        nameField.textProperty().addListener((o, a, b) -> updateHeader());
        hostField.textProperty().addListener((o, a, b) -> updateHeader());
        sqliteFileField.textProperty().addListener((o, a, b) -> updateHeader());
        portSpinner.valueProperty().addListener((o, a, b) -> updateHeader());
        timeoutSpinner.valueProperty().addListener((o, a, b) -> updateAdvancedSummary());
        readOnlyCheckBox.selectedProperty().addListener((o, a, b) -> updateAdvancedSummary());
        trustCertCheckBox.selectedProperty().addListener((o, a, b) -> updateAdvancedSummary());
    }

    private void setupPasswordField() {
        passwordVisibleField.textProperty().bindBidirectional(passwordField.textProperty());
        showPasswordToggle.selectedProperty().addListener((o, a, on) -> {
            passwordVisibleField.setVisible(on);
            passwordField.setVisible(!on);
            showPasswordIcon.setIconLiteral(on ? "fth-eye-off" : "fth-eye");
        });
    }

    /** Same cell as the Object Explorer (user request 2026-11-05: list must look exactly like it). */
    private void setupTree() {
        connectionTree.setCellFactory(tv -> DatabaseTreeCell.forList());
        connectionTree.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (suppressSelection) return;
            if (newVal != null && newVal.getValue() != null && newVal.getValue().getType() == DatabaseNodeType.CONNECTION
                    && newVal.getValue().getConnectionProfile() != null) {
                populateForm(newVal.getValue().getConnectionProfile());
            }
        });
    }

    /** Connection row that shows the Explorer's expand chevron but never opens (no metadata here). */
    private static final class ConnectionItem extends TreeItem<DatabaseNode> {
        ConnectionItem(DatabaseNode node) {
            super(node);
            expandedProperty().addListener((o, a, b) -> {
                if (b) setExpanded(false);
            });
        }

        @Override
        public boolean isLeaf() {
            return false;
        }
    }

    // ------------------------------------------------------------------ logos

    private static Image logoFor(String displayType) {
        String file = switch (displayType == null ? "" : displayType) {
            case "SQL Server" -> "sqlserver.png";
            case "SQLite" -> "sqlite.png";
            default -> "postgresql.png";
        };
        return LOGOS.computeIfAbsent(file, f -> {
            URL url = ConnectionManagerController.class.getResource("/static/icons/" + f);
            return url != null ? new Image(url.toExternalForm(), 48, 48, true, true) : null;
        });
    }

    private static String displayType(String raw) {
        String t = raw == null ? "" : raw.toUpperCase();
        if (t.contains("SQLITE")) return "SQLite";
        if (t.contains("SQLSERVER") || t.contains("SQL SERVER") || t.contains("MSSQL")) return "SQL Server";
        return "PostgreSQL";
    }

    /** Selection cell: product logo + name. */
    private static final class LogoCell extends ListCell<String> {
        private final ImageView view = new ImageView();

        LogoCell() {
            view.setFitWidth(16);
            view.setFitHeight(16);
            view.setPreserveRatio(true);
        }

        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
            } else {
                view.setImage(logoFor(item));
                setText(item);
                setGraphic(view);
            }
        }
    }

    // ------------------------------------------------------------------ layout per type

    private static void show(javafx.scene.Node n, boolean visible) {
        n.setVisible(visible);
        n.setManaged(visible);
    }

    private void applyTypeLayout(String dbType) {
        if (dbType == null) return;
        boolean sqlite = "SQLite".equals(dbType);
        boolean mssql = "SQL Server".equals(dbType);

        headerLogo.setImage(logoFor(dbType));
        show(sqliteRow, sqlite);
        show(hostPortRow, !sqlite);
        show(authSection, !sqlite);
        show(authTypeRow, mssql);
        if (!mssql) windowsAuthSwitch.setSelected(false);
        boolean usesPassword = !sqlite && !windowsAuthSwitch.isSelected();
        show(usernameRow, !sqlite && !windowsAuthSwitch.isSelected());
        show(passwordRow, usesPassword);
        show(savePasswordRow, usesPassword);
        serverSection.setText(sqlite ? "TỆP DỮ LIỆU" : "MÁY CHỦ");

        if (mssql && portSpinner.getValue() == 5432) portSpinner.getValueFactory().setValue(1433);
        if ("PostgreSQL".equals(dbType) && portSpinner.getValue() == 1433) portSpinner.getValueFactory().setValue(5432);
        updateHeader();
    }

    private void updateHeader() {
        String name = nameField.getText();
        headerTitle.setText(name == null || name.isBlank() ? "Kết nối mới" : name);
        String type = dbTypeComboBox.getValue() != null ? dbTypeComboBox.getValue() : "";
        String where = "SQLite".equals(type)
                ? (sqliteFileField.getText() == null || sqliteFileField.getText().isBlank() ? "" : sqliteFileField.getText())
                : (hostField.getText() == null ? "" : hostField.getText()) + ":" + portSpinner.getValue();
        headerSubtitle.setText(type + (where.isBlank() ? "" : " · " + where) + (currentConnectionId == null ? " · chưa lưu" : ""));
    }

    private void updatePasswordExpiry() {
        if (!savePasswordCheckBox.isSelected()) {
            passwordExpiryLabel.setText("Mỗi lần kết nối sẽ hỏi mật khẩu.");
            return;
        }
        if (currentConnectionId == null) {
            passwordExpiryLabel.setText("Hiệu lực 30 ngày kể từ lần lưu.");
            return;
        }
        var policy = PasswordPolicyService.getInstance();
        if (policy.isExpired(currentConnectionId)) {
            passwordExpiryLabel.setText("Đã hết hạn, lần kết nối tới sẽ hỏi lại.");
        } else {
            long at = policy.savedAt(currentConnectionId);
            String since = at > 0
                    ? " (lưu ngày " + java.time.Instant.ofEpochMilli(at).atZone(java.time.ZoneId.systemDefault())
                    .toLocalDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) + ")" : "";
            passwordExpiryLabel.setText("Còn " + policy.daysLeft(currentConnectionId) + " ngày" + since + ", hết hạn sẽ hỏi lại.");
        }
    }

    @FXML
    public void handleToggleAdvanced() {
        boolean open = !advancedBox.isVisible();
        show(advancedBox, open);
        advancedIcon.setIconLiteral(open ? "fth-chevron-down" : "fth-chevron-right");
        updateAdvancedSummary();
    }

    private void updateAdvancedSummary() {
        if (advancedBox.isVisible()) {
            advancedSummary.setText("");
            return;
        }
        List<String> parts = new ArrayList<>();
        parts.add("timeout " + timeoutSpinner.getValue() + "s");
        if (trustCertCheckBox.isSelected()) parts.add("tin cậy chứng chỉ");
        if (readOnlyCheckBox.isSelected()) parts.add("chỉ đọc");
        advancedSummary.setText(String.join(" · ", parts));
    }

    // ------------------------------------------------------------------ list

    public void loadConnections() {
        ConnectionApi api = DesktopBootstrap.getInstance().getConnectionApi();
        Result<List<ConnectionProfileDto>> res = api.getConnections();
        profiles.clear();
        if (res.isSuccess()) profiles.addAll(res.getDataOrThrow());
        refreshGroupChoices();
        rebuildTree();
    }

    private void refreshGroupChoices() {
        String current = groupComboBox.getEditor().getText();
        groupComboBox.setItems(FXCollections.observableArrayList(ExplorerGroupService.getInstance().allGroups(profiles)));
        groupComboBox.getEditor().setText(current);
    }

    private final Set<String> collapsedGroups = new HashSet<>();

    private void rebuildTree() {
        String q = searchField.getText() != null ? searchField.getText().trim().toLowerCase(Locale.ROOT) : "";
        DatabaseNode rootData = new DatabaseNode("root", DatabaseNodeType.ROOT);
        ConnectionTreeBuilder.buildGroupedTree(rootData, profiles);
        TreeItem<DatabaseNode> root = new TreeItem<>(rootData);
        TreeItem<DatabaseNode>[] toSelect = new TreeItem[1];
        populate(rootData, root, q, toSelect);
        suppressSelection = true;
        connectionTree.setRoot(root);
        if (toSelect[0] != null) connectionTree.getSelectionModel().select(toSelect[0]);
        suppressSelection = false;
    }

    /** Groups first then connections, A→Z; search keeps matching connections and their folders. */
    private int populate(DatabaseNode data, TreeItem<DatabaseNode> item, String q, TreeItem<DatabaseNode>[] toSelect) {
        int matches = 0;
        List<DatabaseNode> children = new ArrayList<>(data.getChildren());
        children.sort(Comparator.comparing((DatabaseNode n) -> n.getType() == DatabaseNodeType.GROUP ? 0 : 1)
                .thenComparing(n -> n.getName().toLowerCase(Locale.ROOT)));
        for (DatabaseNode child : children) {
            if (child.getType() == DatabaseNodeType.GROUP) {
                TreeItem<DatabaseNode> g = new TreeItem<>(child);
                boolean groupMatch = !q.isEmpty() && child.getName().toLowerCase(Locale.ROOT).contains(q);
                int m = populate(child, g, groupMatch ? "" : q, toSelect);
                String path = child.getGroupPath();
                g.setExpanded(!q.isEmpty() || !collapsedGroups.contains(path));
                g.expandedProperty().addListener((o, was, now) -> {
                    if (now) collapsedGroups.remove(path); else collapsedGroups.add(path);
                    connectionTree.refresh();
                });
                if (q.isEmpty() || groupMatch || m > 0) {
                    item.getChildren().add(g);
                    matches += m + (groupMatch ? 1 : 0);
                }
            } else if (child.getType() == DatabaseNodeType.CONNECTION) {
                ConnectionProfileDto p = child.getConnectionProfile();
                if (!q.isEmpty() && (p == null || !matches(p, q))) continue;
                ConnectionItem c = new ConnectionItem(child);
                item.getChildren().add(c);
                matches++;
                if (p != null && p.id().equals(currentConnectionId)) toSelect[0] = c;
            }
        }
        return matches;
    }

    private static boolean matches(ConnectionProfileDto p, String q) {
        return (p.name() != null && p.name().toLowerCase(Locale.ROOT).contains(q))
                || (p.host() != null && p.host().toLowerCase(Locale.ROOT).contains(q))
                || (p.databaseType() != null && p.databaseType().toLowerCase(Locale.ROOT).contains(q));
    }

    private TreeItem<DatabaseNode> findItem(TreeItem<DatabaseNode> parent, String id) {
        if (parent == null) return null;
        for (TreeItem<DatabaseNode> c : parent.getChildren()) {
            DatabaseNode n = c.getValue();
            if (n.getType() == DatabaseNodeType.CONNECTION && id.equals(n.getConnectionId())) return c;
            TreeItem<DatabaseNode> r = findItem(c, id);
            if (r != null) return r;
        }
        return null;
    }

    // ------------------------------------------------------------------ form

    private void populateForm(ConnectionProfileDto profile) {
        this.currentConnectionId = profile.id();
        nameField.setText(profile.name());
        groupComboBox.getEditor().setText(profile.group() != null ? profile.group() : "");
        groupComboBox.setValue(profile.group() != null ? profile.group() : "");
        envComboBox.setValue(profile.environment() != null ? profile.environment() : "DEV");
        readOnlyCheckBox.setSelected(profile.readOnly());

        String type = displayType(profile.databaseType());
        dbTypeComboBox.setValue(type);

        hostField.setText(profile.host() != null ? profile.host() : "localhost");
        portSpinner.getValueFactory().setValue(profile.port() > 0 ? profile.port() : 5432);
        sqliteFileField.setText(profile.sqliteFilePath() != null ? profile.sqliteFilePath() : "");

        windowsAuthSwitch.setSelected("WINDOWS_AUTH".equalsIgnoreCase(profile.authType()));
        usernameField.setText(profile.username() != null ? profile.username() : "");

        passwordField.clear();
        if (profile.credentialRef() != null && !profile.credentialRef().isBlank()) {
            String resolved = DesktopBootstrap.getInstance().getCredentialVault().resolveSecret(profile.credentialRef());
            if (resolved != null && !resolved.equals(profile.credentialRef())) passwordField.setText(resolved);
        }
        savePasswordCheckBox.setSelected(profile.savePassword());

        databaseComboBox.getItems().clear();
        databaseComboBox.setValue(profile.databaseName() != null ? profile.databaseName() : "");
        schemaComboBox.getItems().clear();
        schemaComboBox.setValue(SCHEMA_PREFS.get(prefKey(profile.id()), defaultSchema(type)));
        timeoutSpinner.getValueFactory().setValue(profile.timeoutSeconds() > 0 ? profile.timeoutSeconds() : 15);
        trustCertCheckBox.setSelected(profile.trustServerCertificate());

        setStatus("", null);
        applyTypeLayout(type);
        updatePasswordExpiry();
        updateAdvancedSummary();
    }

    private static String defaultSchema(String type) {
        return switch (type) {
            case "SQL Server" -> "dbo";
            case "SQLite" -> "main";
            default -> "public";
        };
    }

    private static String prefKey(String id) {
        String k = id == null ? "" : id.replace('/', '_');
        return k.length() > Preferences.MAX_KEY_LENGTH ? k.substring(0, Preferences.MAX_KEY_LENGTH) : k;
    }

    public void startNewConnection() {
        handleNewConnection();
    }

    /** "New Connection" from a group in the Explorer: empty form with the group pre-filled. */
    public void startNewConnectionInGroup(String groupPath) {
        handleNewConnection();
        if (groupPath != null && !groupPath.isBlank()) {
            groupComboBox.getEditor().setText(groupPath);
            groupComboBox.setValue(groupPath);
        }
    }

    public String getConnectRequestedId() {
        return connectRequestedId;
    }

    public void selectConnectionByIdOrName(String idOrName) {
        if (idOrName == null || idOrName.isBlank()) {
            handleNewConnection();
            return;
        }
        ConnectionProfileDto match = profiles.stream()
                .filter(p -> p.id().equalsIgnoreCase(idOrName) || p.name().equalsIgnoreCase(idOrName))
                .findFirst()
                .orElseGet(() -> {
                    String q = idOrName.trim().toLowerCase(Locale.ROOT);
                    return profiles.stream()
                            .filter(p -> p.name().toLowerCase(Locale.ROOT).contains(q) || q.contains(p.name().toLowerCase(Locale.ROOT)))
                            .findFirst().orElse(null);
                });
        if (match != null) {
            populateForm(match);
            TreeItem<DatabaseNode> item = findItem(connectionTree.getRoot(), match.id());
            if (item != null) {
                suppressSelection = true;
                connectionTree.getSelectionModel().select(item);
                suppressSelection = false;
                connectionTree.scrollTo(Math.max(0, connectionTree.getRow(item) - 3));
            }
            return;
        }
        handleNewConnection();
        nameField.setText(idOrName);
    }

    @FXML
    public void handleNewConnection() {
        suppressSelection = true;
        connectionTree.getSelectionModel().clearSelection();
        suppressSelection = false;
        currentConnectionId = null;
        nameField.setText("");
        groupComboBox.getEditor().setText("");
        groupComboBox.setValue("");
        envComboBox.setValue("DEV");
        readOnlyCheckBox.setSelected(false);
        dbTypeComboBox.setValue("PostgreSQL");
        hostField.setText("localhost");
        portSpinner.getValueFactory().setValue(5432);
        sqliteFileField.clear();
        windowsAuthSwitch.setSelected(false);
        usernameField.setText("postgres");
        passwordField.clear();
        savePasswordCheckBox.setSelected(true);
        databaseComboBox.getItems().clear();
        databaseComboBox.setValue("postgres");
        schemaComboBox.getItems().clear();
        schemaComboBox.setValue("public");
        timeoutSpinner.getValueFactory().setValue(15);
        trustCertCheckBox.setSelected(true);
        setStatus("", null);
        applyTypeLayout("PostgreSQL");
        updatePasswordExpiry();
        updateAdvancedSummary();
        Platform.runLater(nameField::requestFocus);
    }

    @FXML
    public void handleDuplicateConnection() {
        if (currentConnectionId == null) {
            NotificationService.getInstance().showWarning("Nhân bản", "Chọn kết nối cần nhân bản.");
            return;
        }
        ConnectionProfileDto selected = currentProfile();
        if (selected == null) return;
        String newName = selected.name() + "_Copy";
        Result<ConnectionProfileDto> result = DesktopBootstrap.getInstance().getConnectionApi()
                .duplicateConnection(selected.id(), newName);
        if (result.isSuccess()) {
            currentConnectionId = result.getDataOrThrow().id();
            loadConnections();
            selectConnectionByIdOrName(currentConnectionId);
            NotificationService.getInstance().showSuccess("Nhân bản", "Đã tạo \"" + newName + "\".");
        } else {
            NotificationService.getInstance().showError("Nhân bản", result.getError().map(e -> e.message()).orElse("Không nhân bản được."));
        }
    }

    @FXML
    public void handleDeleteConnection() {
        ConnectionProfileDto selected = currentProfile();
        if (selected == null) {
            NotificationService.getInstance().showWarning("Xóa kết nối", "Chọn kết nối cần xóa.");
            return;
        }
        boolean ok = ConfirmDialog.show(stage(), "Xóa kết nối", "Xóa kết nối \"" + selected.name() + "\"?",
                "Cấu hình kết nối sẽ bị xóa khỏi máy. Database không bị ảnh hưởng.", "Xóa kết nối", true);
        if (!ok) return;
        Result<Void> result = DesktopBootstrap.getInstance().getConnectionApi().deleteConnection(selected.id());
        if (result.isSuccess()) {
            PasswordPolicyService.getInstance().clear(selected.id());
            NotificationService.getInstance().showSuccess("Xóa kết nối", "Đã xóa \"" + selected.name() + "\".");
            currentConnectionId = null;
            loadConnections();
            handleNewConnection();
        } else {
            NotificationService.getInstance().showError("Xóa kết nối", result.getError().map(e -> e.message()).orElse("Xóa thất bại."));
        }
    }

    @FXML
    public void handleChooseSqliteFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Chọn tệp SQLite");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("SQLite (*.db, *.sqlite, *.sqlite3)", "*.db", "*.sqlite", "*.sqlite3", "*.db3"),
                new FileChooser.ExtensionFilter("Tất cả tệp (*.*)", "*.*"));
        File file = chooser.showOpenDialog(stage());
        if (file != null) {
            sqliteFileField.setText(file.getAbsolutePath());
            if (nameField.getText() == null || nameField.getText().isBlank()) {
                nameField.setText(file.getName().replaceFirst("[.][^.]+$", ""));
            }
        }
    }

    // ------------------------------------------------------------------ test / fetch (mock while prototyping)

    @FXML
    public void handleTestConnection() {
        testButton.setDisable(true);
        show(testProgressIndicator, true);
        setStatus("Đang kiểm tra kết nối…", null);

        if (app.framework.backend.BackendFlags.mock(app.framework.backend.BackendFlags.Area.CONNECTIONS)) {
            long start = System.currentTimeMillis();
            MockMetadataProvider.deliver(() -> List.of(Boolean.TRUE), r -> {
                testButton.setDisable(false);
                show(testProgressIndicator, false);
                boolean ok = isFormValidForTest();
                if (ok) {
                    setStatus("✓ Kết nối thành công (dữ liệu mẫu) · " + (System.currentTimeMillis() - start) + " ms", "success");
                } else {
                    setStatus("✕ Thiếu host / tệp hoặc username.", "error");
                }
            });
            return;
        }

        String authType = windowsAuthSwitch.isSelected() ? "WINDOWS_AUTH" : "PASSWORD";
        TestConnectionDraftRequest request = new TestConnectionDraftRequest(
                nameField.getText(), dbTypeComboBox.getValue(), hostField.getText(), portSpinner.getValue(),
                databaseComboBox.getValue(), sqliteFileField.getText(), usernameField.getText(), authType,
                passwordField.getText(), timeoutSpinner.getValue(), trustCertCheckBox.isSelected(), Collections.emptyMap());
        AsyncTaskManager.getInstance().executeAsync(
                () -> DesktopBootstrap.getInstance().getConnectionApi().testConnection(request),
                result -> {
                    testButton.setDisable(false);
                    show(testProgressIndicator, false);
                    if (result.isSuccess() && Boolean.TRUE.equals(result.getDataOrThrow())) {
                        setStatus("✓ Kết nối thành công", "success");
                    } else {
                        setStatus("✕ " + result.getError().map(e -> e.message()).orElse("Kết nối thất bại"), "error");
                    }
                },
                error -> {
                    testButton.setDisable(false);
                    show(testProgressIndicator, false);
                    setStatus("✕ " + error.getMessage(), "error");
                });
    }

    private boolean isFormValidForTest() {
        String type = dbTypeComboBox.getValue();
        if ("SQLite".equals(type)) return sqliteFileField.getText() != null && !sqliteFileField.getText().isBlank();
        boolean host = hostField.getText() != null && !hostField.getText().isBlank();
        boolean user = windowsAuthSwitch.isSelected() || (usernameField.getText() != null && !usernameField.getText().isBlank());
        return host && user;
    }

    @FXML
    public void handleFetchDatabases() {
        ConnectionProfileDto draft = buildDraftProfile();
        fetchDbsButton.setDisable(true);
        setStatus("Đang tải danh sách database…", null);
        if (app.framework.backend.BackendFlags.mock(app.framework.backend.BackendFlags.Area.CONNECTIONS)) {
            MockMetadataProvider.deliver(() -> MockMetadataProvider.catalogs(draft), dbs -> {
                fetchDbsButton.setDisable(false);
                fillCombo(databaseComboBox, dbs);
                setStatus("✓ " + dbs.size() + " database (dữ liệu mẫu)", "success");
            });
            return;
        }
        String pass = passwordField.getText();
        AsyncTaskManager.getInstance().executeAsync(
                () -> ConnectionSessionManager.getInstance().fetchDatabases(draft, pass),
                result -> {
                    fetchDbsButton.setDisable(false);
                    if (result.isSuccess()) {
                        List<String> dbs = result.getDataOrThrow();
                        fillCombo(databaseComboBox, dbs);
                        setStatus("✓ " + dbs.size() + " database", "success");
                    } else {
                        setStatus("✕ " + result.getError().map(e -> e.message()).orElse("Không lấy được danh sách database"), "error");
                    }
                },
                error -> {
                    fetchDbsButton.setDisable(false);
                    setStatus("✕ " + error.getMessage(), "error");
                });
    }

    @FXML
    public void handleFetchSchemas() {
        ConnectionProfileDto draft = buildDraftProfile();
        fetchSchemasButton.setDisable(true);
        setStatus("Đang tải danh sách schema…", null);
        if (app.framework.backend.BackendFlags.mock(app.framework.backend.BackendFlags.Area.CONNECTIONS)) {
            MockMetadataProvider.deliver(() -> MockMetadataProvider.schemas(draft, databaseComboBox.getValue()), schemas -> {
                fetchSchemasButton.setDisable(false);
                fillCombo(schemaComboBox, schemas);
                setStatus("✓ " + schemas.size() + " schema (dữ liệu mẫu)", "success");
            });
            return;
        }
        String pass = passwordField.getText();
        String db = databaseComboBox.getValue();
        AsyncTaskManager.getInstance().executeAsync(
                () -> ConnectionSessionManager.getInstance().fetchSchemas(draft, db, pass),
                result -> {
                    fetchSchemasButton.setDisable(false);
                    if (result.isSuccess()) {
                        List<String> schemas = result.getDataOrThrow();
                        fillCombo(schemaComboBox, schemas);
                        setStatus("✓ " + schemas.size() + " schema", "success");
                    } else {
                        setStatus("✕ " + result.getError().map(e -> e.message()).orElse("Không lấy được danh sách schema"), "error");
                    }
                },
                error -> {
                    fetchSchemasButton.setDisable(false);
                    setStatus("✕ " + error.getMessage(), "error");
                });
    }

    private static void fillCombo(ComboBox<String> combo, List<String> values) {
        String current = combo.getValue();
        combo.setItems(FXCollections.observableArrayList(values));
        if (current == null || current.isBlank() || !values.contains(current)) {
            if (!values.isEmpty()) combo.setValue(values.get(0));
        } else {
            combo.setValue(current);
        }
        combo.show();
    }

    // ------------------------------------------------------------------ save / connect

    @FXML
    public void handleSaveConnection() {
        save(false);
    }

    /** @return true when saved successfully */
    private boolean save(boolean quiet) {
        String name = nameField.getText() != null ? nameField.getText().trim() : "";
        nameField.getStyleClass().remove("invalid");
        if (name.isEmpty()) {
            nameField.getStyleClass().add("invalid");
            setStatus("✕ Tên kết nối không được để trống.", "error");
            nameField.requestFocus();
            return false;
        }
        String group = ExplorerGroupService.normalize(groupComboBox.getEditor().getText());
        String authType = "SQLite".equals(dbTypeComboBox.getValue()) ? "NONE"
                : windowsAuthSwitch.isSelected() ? "WINDOWS_AUTH" : "PASSWORD";
        String password = passwordField.getText();
        boolean savePassword = savePasswordCheckBox.isSelected();
        ConnectionApi api = DesktopBootstrap.getInstance().getConnectionApi();

        Result<ConnectionProfileDto> res;
        if (currentConnectionId != null) {
            res = api.updateConnection(new UpdateConnectionRequest(
                    currentConnectionId, name, group, envComboBox.getValue(), readOnlyCheckBox.isSelected(),
                    dbTypeComboBox.getValue(), hostField.getText(), portSpinner.getValue(), databaseComboBox.getValue(),
                    sqliteFileField.getText(), usernameField.getText(), authType,
                    password == null || password.isEmpty() ? null : password, savePassword,
                    timeoutSpinner.getValue(), trustCertCheckBox.isSelected(), Collections.emptyMap()));
        } else {
            res = api.createConnection(new CreateConnectionRequest(
                    name, group, envComboBox.getValue(), readOnlyCheckBox.isSelected(),
                    dbTypeComboBox.getValue(), hostField.getText(), portSpinner.getValue(), databaseComboBox.getValue(),
                    sqliteFileField.getText(), usernameField.getText(), authType, password, savePassword,
                    timeoutSpinner.getValue(), trustCertCheckBox.isSelected(), Collections.emptyMap()));
        }
        if (!res.isSuccess()) {
            setStatus("✕ " + res.getError().map(e -> e.message()).orElse("Lưu thất bại."), "error");
            return false;
        }
        ConnectionProfileDto saved = res.getDataOrThrow();
        currentConnectionId = saved.id();

        // password lifetime: 30 days from the last save
        var policy = PasswordPolicyService.getInstance();
        if (savePassword && password != null && !password.isEmpty()) {
            policy.markSaved(saved.id());
        } else if (!savePassword) {
            policy.clear(saved.id());
        }
        String schema = schemaComboBox.getValue();
        try {
            if (schema != null && !schema.isBlank()) SCHEMA_PREFS.put(prefKey(saved.id()), schema.trim());
        } catch (Exception ignored) {
        }

        loadConnections();
        selectConnectionByIdOrName(saved.id());
        if (!quiet) setStatus("✓ Đã lưu \"" + name + "\".", "success");
        return true;
    }

    @FXML
    public void handleConnectNow() {
        if (!save(true)) return;
        connectRequestedId = currentConnectionId;
        GlobalDesktopState.getInstance().setActiveConnection(nameField.getText().trim());
        handleClose();
    }

    @FXML
    public void handleClose() {
        Stage stage = stage();
        if (stage != null) stage.close();
    }

    // ------------------------------------------------------------------ helpers

    private Stage stage() {
        return dialogRoot.getScene() != null && dialogRoot.getScene().getWindow() instanceof Stage s ? s : null;
    }

    private ConnectionProfileDto currentProfile() {
        if (currentConnectionId == null) return null;
        return profiles.stream().filter(p -> p.id().equals(currentConnectionId)).findFirst().orElse(null);
    }

    private void setStatus(String text, String kind) {
        statusLabel.setText(text);
        statusLabel.getStyleClass().removeAll("dialog-success", "dialog-error", "dialog-hint");
        statusLabel.getStyleClass().add(kind == null ? "dialog-hint" : "success".equals(kind) ? "dialog-success" : "dialog-error");
    }

    private ConnectionProfileDto buildDraftProfile() {
        String authType = windowsAuthSwitch.isSelected() ? "WINDOWS_AUTH" : "PASSWORD";
        return new ConnectionProfileDto(
                currentConnectionId != null ? currentConnectionId : UUID.randomUUID().toString(),
                nameField.getText(),
                groupComboBox.getEditor().getText(),
                envComboBox.getValue(),
                readOnlyCheckBox.isSelected(),
                dbTypeComboBox.getValue(),
                hostField.getText(),
                portSpinner.getValue(),
                databaseComboBox.getValue(),
                sqliteFileField.getText(),
                usernameField.getText(),
                authType,
                null,
                savePasswordCheckBox.isSelected(),
                timeoutSpinner.getValue(),
                trustCertCheckBox.isSelected(),
                Collections.emptyMap(),
                null,
                null);
    }
}
