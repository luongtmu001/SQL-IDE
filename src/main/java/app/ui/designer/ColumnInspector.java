package app.ui.designer;

import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.List;

/**
 * Property Inspector panel for the currently selected column (SCR-TD-001 / TD3).
 * Provides granular controls for Defaults (Fix B7), Collation, Identity/Sequence, Computed columns,
 * and displays active constraint dependencies.
 */
public class ColumnInspector extends VBox {

    private final String engine;
    private final Runnable onChangeCallback;

    private ColumnDesignViewModel activeColumn;
    private ChangeListener<String> nameListener;

    // UI Controls
    private final Label titleLabel = new Label("Thuộc tính cột");
    private final Label placeholderLabel = new Label("Chọn một cột trên lưới để xem thuộc tính");

    private final VBox contentBox = new VBox(10);

    // Group 1: Default & Computed
    private final TextField txtDefaultValue = new TextField();
    private final RadioButton rbDefaultLiteral = new RadioButton("Giá trị");
    private final RadioButton rbDefaultExpression = new RadioButton("Biểu thức");
    private final ToggleGroup defaultGroup = new ToggleGroup();
    private final TextField txtDefaultConstraintName = new TextField();
    private final TextArea txtComputedFormula = new TextArea();
    private final CheckBox chkComputedPersisted = new CheckBox("Lưu trữ vật lý");

    // Group 2: Collation & Identity
    private final ComboBox<String> cbCollation = new ComboBox<>();
    private final CheckBox chkIdentity = new CheckBox("Tự tăng");
    private final TextField txtIdentitySeed = new TextField("1");
    private final TextField txtIdentityIncrement = new TextField("1");
    private final TextField txtSequenceName = new TextField();

    // Group 3: Comment & Dependencies
    private final TextArea txtComment = new TextArea();
    private final Label lblDependencies = new Label("Không có ràng buộc phụ thuộc");

    public ColumnInspector(String engine, Runnable onChangeCallback) {
        this.engine = engine != null ? engine.toLowerCase() : "sqlserver";
        this.onChangeCallback = onChangeCallback;

        getStyleClass().add("td-inspector-pane");
        setMinWidth(240);
        setPrefWidth(290);
        setMaxWidth(450);
        setPadding(new Insets(10));

        setupUI();
        setColumn(null);
    }

    private void setupUI() {
        // Header
        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("td-inspector-header");
        FontIcon icon = new FontIcon("fth-sliders");
        icon.setIconSize(16);
        titleLabel.getStyleClass().add("td-inspector-title");
        header.getChildren().addAll(icon, titleLabel);

        // Placeholder
        placeholderLabel.getStyleClass().add("td-inspector-placeholder");
        placeholderLabel.setWrapText(true);
        placeholderLabel.setAlignment(Pos.CENTER);

        // Group 1: Default & Computed
        VBox sec1 = createSection("Giá trị mặc định & Tính toán");

        rbDefaultLiteral.setToggleGroup(defaultGroup);
        rbDefaultExpression.setToggleGroup(defaultGroup);
        rbDefaultLiteral.setSelected(true);
        HBox defaultKindBox = new HBox(12, rbDefaultLiteral, rbDefaultExpression);

        txtComputedFormula.setPrefRowCount(2);
        txtComputedFormula.setWrapText(true);

        sec1.getChildren().addAll(
                fieldLabel("Giá trị mặc định:"),
                txtDefaultValue,
                defaultKindBox
        );

        if (!"postgresql".equalsIgnoreCase(engine)) {
            sec1.getChildren().addAll(
                    fieldLabel("Tên Constraint mặc định:"),
                    txtDefaultConstraintName
            );
        }

        sec1.getChildren().addAll(
                fieldLabel("Công thức tính toán:"),
                txtComputedFormula,
                chkComputedPersisted
        );

        // Group 2: Collation & Identity
        VBox sec2 = createSection("Collation & Định danh");
        cbCollation.setEditable(true);
        cbCollation.setItems(FXCollections.observableArrayList(getCommonCollations()));

        HBox idParamsBox = new HBox(8,
                new VBox(2, new Label("Seed:"), txtIdentitySeed),
                new VBox(2, new Label("Increment:"), txtIdentityIncrement)
        );

        sec2.getChildren().addAll(
                fieldLabel("Collation:"),
                cbCollation,
                chkIdentity,
                idParamsBox
        );

        if ("postgresql".equalsIgnoreCase(engine)) {
            sec2.getChildren().addAll(
                    fieldLabel("Tên Sequence:"),
                    txtSequenceName
            );
        }

        // Group 3: Comment & Dependencies
        VBox sec3 = createSection("Mô tả & Phụ thuộc");
        txtComment.setPrefRowCount(3);
        txtComment.setWrapText(true);

        lblDependencies.getStyleClass().add("td-inspector-hint");
        lblDependencies.setWrapText(true);

        sec3.getChildren().addAll(
                fieldLabel("Mô tả:"),
                txtComment,
                fieldLabel("Đối tượng phụ thuộc:"),
                lblDependencies
        );

        contentBox.getChildren().addAll(sec1, sec2, sec3);

        ScrollPane scroll = new ScrollPane(contentBox);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("edge-to-edge");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        getChildren().addAll(header, scroll);

        setupEventHandlers();
    }

    private VBox createSection(String title) {
        VBox box = new VBox(6);
        box.getStyleClass().add("td-inspector-section");
        box.setPadding(new Insets(8));
        Label lbl = new Label(title);
        lbl.getStyleClass().add("td-inspector-section-title");
        box.getChildren().add(lbl);
        return box;
    }

    private Label fieldLabel(String text) {
        Label lbl = new Label(text);
        lbl.getStyleClass().add("td-inspector-field-label");
        return lbl;
    }

    private void setupEventHandlers() {
        txtDefaultValue.textProperty().addListener((obs, o, n) -> {
            if (activeColumn != null) {
                activeColumn.setDefaultValue(n);
                notifyChanged();
            }
        });

        defaultGroup.selectedToggleProperty().addListener((obs, o, n) -> {
            if (activeColumn != null) {
                activeColumn.setDefaultIsExpression(n == rbDefaultExpression);
                notifyChanged();
            }
        });

        txtDefaultConstraintName.textProperty().addListener((obs, o, n) -> {
            if (activeColumn != null) {
                activeColumn.setDefaultConstraintName(n);
                notifyChanged();
            }
        });

        txtComputedFormula.textProperty().addListener((obs, o, n) -> {
            if (activeColumn != null) {
                activeColumn.setComputedFormula(n);
                notifyChanged();
            }
        });

        chkComputedPersisted.selectedProperty().addListener((obs, o, n) -> {
            if (activeColumn != null) {
                activeColumn.setComputedPersisted(n);
                notifyChanged();
            }
        });

        cbCollation.valueProperty().addListener((obs, o, n) -> {
            if (activeColumn != null) {
                activeColumn.setCollation(n);
                notifyChanged();
            }
        });

        chkIdentity.selectedProperty().addListener((obs, o, n) -> {
            if (activeColumn != null) {
                activeColumn.setIdentity(n);
                txtIdentitySeed.setDisable(!n);
                txtIdentityIncrement.setDisable(!n);
                notifyChanged();
            }
        });

        txtIdentitySeed.textProperty().addListener((obs, o, n) -> {
            if (activeColumn != null && n != null && !n.isBlank()) {
                try {
                    activeColumn.setIdentitySeed(Long.parseLong(n.trim()));
                    notifyChanged();
                } catch (NumberFormatException ignored) {}
            }
        });

        txtIdentityIncrement.textProperty().addListener((obs, o, n) -> {
            if (activeColumn != null && n != null && !n.isBlank()) {
                try {
                    activeColumn.setIdentityIncrement(Long.parseLong(n.trim()));
                    notifyChanged();
                } catch (NumberFormatException ignored) {}
            }
        });

        txtSequenceName.textProperty().addListener((obs, o, n) -> {
            if (activeColumn != null) {
                activeColumn.setSequenceName(n);
                notifyChanged();
            }
        });

        txtComment.textProperty().addListener((obs, o, n) -> {
            if (activeColumn != null) {
                activeColumn.setComment(n);
                notifyChanged();
            }
        });
    }

    public void setColumn(ColumnDesignViewModel column) {
        if (this.activeColumn != null && nameListener != null) {
            this.activeColumn.nameProperty().removeListener(nameListener);
        }

        this.activeColumn = column;

        if (column == null) {
            titleLabel.setText("Thuộc tính cột");
            contentBox.setVisible(false);
            contentBox.setManaged(false);
            if (!getChildren().contains(placeholderLabel)) {
                getChildren().add(placeholderLabel);
            }
            return;
        }

        getChildren().remove(placeholderLabel);
        contentBox.setVisible(true);
        contentBox.setManaged(true);

        updateTitle(column.getName());
        nameListener = (obs, o, n) -> updateTitle(n);
        column.nameProperty().addListener(nameListener);

        txtDefaultValue.setText(column.getDefaultValue() != null ? column.getDefaultValue() : "");
        if (column.isDefaultIsExpression()) {
            rbDefaultExpression.setSelected(true);
        } else {
            rbDefaultLiteral.setSelected(true);
        }

        txtDefaultConstraintName.setText(column.getDefaultConstraintName() != null ? column.getDefaultConstraintName() : "");
        txtComputedFormula.setText(column.getComputedFormula() != null ? column.getComputedFormula() : "");
        chkComputedPersisted.setSelected(column.isComputedPersisted());

        cbCollation.setValue(column.getCollation() != null ? column.getCollation() : "");
        chkIdentity.setSelected(column.isIdentity());
        txtIdentitySeed.setText(String.valueOf(column.getIdentitySeed() != null ? column.getIdentitySeed() : 1L));
        txtIdentityIncrement.setText(String.valueOf(column.getIdentityIncrement() != null ? column.getIdentityIncrement() : 1L));
        txtIdentitySeed.setDisable(!column.isIdentity());
        txtIdentityIncrement.setDisable(!column.isIdentity());

        txtSequenceName.setText(column.getSequenceName() != null ? column.getSequenceName() : "");
        txtComment.setText(column.getComment() != null ? column.getComment() : "");
    }

    private void updateTitle(String name) {
        titleLabel.setText("Cột: " + (name != null && !name.isBlank() ? name : "(chưa đặt tên)"));
    }

    public void setDependenciesInfo(String info) {
        lblDependencies.setText(info != null && !info.isBlank() ? info : "Không có ràng buộc phụ thuộc");
    }

    private void notifyChanged() {
        if (onChangeCallback != null) {
            onChangeCallback.run();
        }
    }

    private List<String> getCommonCollations() {
        if ("postgresql".equalsIgnoreCase(engine)) {
            return List.of("default", "C", "POSIX", "en_US.utf8", "vi_VN.utf8");
        } else {
            return List.of(
                    "DATABASE_DEFAULT",
                    "SQL_Latin1_General_CP1_CI_AS",
                    "Latin1_General_CI_AS",
                    "Latin1_General_100_CI_AS_SC_UTF8",
                    "Vietnamese_CI_AS"
            );
        }
    }
}

