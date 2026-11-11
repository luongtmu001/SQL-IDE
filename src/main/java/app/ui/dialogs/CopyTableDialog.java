package app.ui.dialogs;

import com.lltool.core.domain.tabledesigner.model.CopyTableOptions;
import com.lltool.core.domain.tabledesigner.model.TableDesign;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Window;

import java.util.*;

/**
 * Copy Table Dialog (SCR-TD-001 / TD5 / REG-TD-013).
 * Allows configuring destination schema, new table name, and which structural components to clone.
 * Fixes Bug B10 (safe identifier length, existing table verification, trigger script warnings).
 */
public final class CopyTableDialog {

    public record CopyTableResult(
            String targetSchema,
            String targetTableName,
            CopyTableOptions options
    ) {}

    private CopyTableDialog() {}

    public static Optional<CopyTableResult> show(
            Window owner,
            String engine,
            String sourceCatalog,
            String sourceSchema,
            String sourceTable,
            List<String> availableSchemas,
            List<String> existingTables) {

        int maxLen = ("postgresql".equalsIgnoreCase(engine) || "postgres".equalsIgnoreCase(engine)) ? 63 : 128;
        String defaultCopyName = TableDesign.makeSafeIdentifier(sourceTable, "_Copy", engine);

        DialogChrome chrome = DialogChrome.create(owner, "Sao chép cấu trúc bảng", "fth-copy");
        chrome.subtitle("[" + (sourceSchema != null ? sourceSchema : "dbo") + "].[" + (sourceTable != null ? sourceTable : "Table") + "]");

        VBox content = new VBox(12);
        content.setPadding(new Insets(10, 14, 10, 14));
        content.setPrefWidth(460);

        // Header info
        Label lblHeader = new Label("Tạo bảng mới bằng cách nhân bản cấu trúc từ bảng nguồn:");
        lblHeader.getStyleClass().add("dialog-message");

        // Form GridPane
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        ColumnConstraints c1 = new ColumnConstraints();
        c1.setMinWidth(110);
        ColumnConstraints c2 = new ColumnConstraints();
        c2.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(c1, c2);

        // Target Schema
        Label lblSchema = new Label("Schema đích:");
        lblSchema.getStyleClass().add("dialog-label");
        ComboBox<String> cbSchema = new ComboBox<>();
        cbSchema.setEditable(true);
        cbSchema.setMaxWidth(Double.MAX_VALUE);
        if (availableSchemas != null && !availableSchemas.isEmpty()) {
            cbSchema.setItems(FXCollections.observableArrayList(availableSchemas));
            if (availableSchemas.contains(sourceSchema)) {
                cbSchema.setValue(sourceSchema);
            } else {
                cbSchema.setValue(availableSchemas.get(0));
            }
        } else {
            cbSchema.setItems(FXCollections.observableArrayList(sourceSchema != null ? sourceSchema : "dbo"));
            cbSchema.setValue(sourceSchema != null ? sourceSchema : "dbo");
        }
        grid.add(lblSchema, 0, 0);
        grid.add(cbSchema, 1, 0);

        // Target Table Name
        Label lblName = new Label("Tên bảng mới:");
        lblName.getStyleClass().add("dialog-label");
        TextField txtName = new TextField(defaultCopyName);
        txtName.setMaxWidth(Double.MAX_VALUE);
        grid.add(lblName, 0, 1);
        grid.add(txtName, 1, 1);

        // Feedback / Validation Label
        Label lblValidation = new Label();
        lblValidation.setWrapText(true);
        lblValidation.getStyleClass().add("dialog-error-label");
        lblValidation.setVisible(false);
        lblValidation.setManaged(false);

        // Checkbox options
        Label lblOptions = new Label("Thành phần sao chép:");
        lblOptions.getStyleClass().add("dialog-label");

        CheckBox chkColumns = new CheckBox("Cột & Kiểu dữ liệu (Bắt buộc)");
        chkColumns.setSelected(true);
        chkColumns.setDisable(true);

        CheckBox chkPk = new CheckBox("Khóa chính (Primary Key)");
        chkPk.setSelected(true);

        CheckBox chkIndexes = new CheckBox("Chỉ mục (Indexes - tự động thêm hậu tố _Copy)");
        chkIndexes.setSelected(true);

        CheckBox chkForeignKeys = new CheckBox("Khóa ngoại (Foreign Keys)");
        chkForeignKeys.setSelected(true);

        CheckBox chkConstraints = new CheckBox("Ràng buộc Unique & Check");
        chkConstraints.setSelected(true);

        CheckBox chkTriggers = new CheckBox("Triggers (Mặc định tắt - kiểm tra kỹ script)");
        chkTriggers.setSelected(false);

        CheckBox chkData = new CheckBox("Dữ liệu bảng (chưa hỗ trợ)");
        chkData.setSelected(false);
        chkData.setDisable(true);

        VBox checkList = new VBox(6, chkColumns, chkPk, chkIndexes, chkForeignKeys, chkConstraints, chkTriggers, chkData);
        checkList.setPadding(new Insets(4, 0, 4, 8));

        content.getChildren().addAll(lblHeader, grid, lblValidation, lblOptions, checkList);

        // Footer buttons
        Button cancel = DialogChrome.cancel("Hủy");
        Button ok = DialogChrome.primary("Tạo bản sao");
        ok.setDefaultButton(true);

        // Table validation logic
        Set<String> lowerExisting = new HashSet<>();
        if (existingTables != null) {
            for (String t : existingTables) {
                if (t != null) lowerExisting.add(t.toLowerCase(Locale.ROOT));
            }
        }

        Runnable validate = () -> {
            String name = txtName.getText() != null ? txtName.getText().trim() : "";
            if (name.isEmpty()) {
                lblValidation.setText("✕ Tên bảng không được để trống.");
                lblValidation.getStyleClass().setAll("dialog-error-label");
                lblValidation.setVisible(true);
                lblValidation.setManaged(true);
                ok.setDisable(true);
                return;
            }
            if (name.length() > maxLen) {
                lblValidation.setText("✕ Tên bảng vượt quá giới hạn tối đa (" + maxLen + " ký tự).");
                lblValidation.getStyleClass().setAll("dialog-error-label");
                lblValidation.setVisible(true);
                lblValidation.setManaged(true);
                ok.setDisable(true);
                return;
            }
            if (!name.matches("^[a-zA-Z_#@][a-zA-Z0-9_#$@]*$")) {
                lblValidation.setText("✕ Tên bảng chứa ký tự không hợp lệ.");
                lblValidation.getStyleClass().setAll("dialog-error-label");
                lblValidation.setVisible(true);
                lblValidation.setManaged(true);
                ok.setDisable(true);
                return;
            }
            if (lowerExisting.contains(name.toLowerCase(Locale.ROOT))) {
                lblValidation.setText("⚠ Cảnh báo: Bảng \"" + name + "\" đã tồn tại trong schema này.");
                lblValidation.getStyleClass().setAll("dialog-warning-label");
                lblValidation.setVisible(true);
                lblValidation.setManaged(true);
                ok.setDisable(false); // Warning, allow proceed if user chooses to overwrite/alter
                return;
            }
            lblValidation.setVisible(false);
            lblValidation.setManaged(false);
            ok.setDisable(false);
        };

        txtName.textProperty().addListener((obs, o, n) -> validate.run());
        validate.run();

        final CopyTableResult[] result = new CopyTableResult[1];

        cancel.setOnAction(e -> chrome.close());
        ok.setOnAction(e -> {
            String targetSchema = cbSchema.getValue() != null && !cbSchema.getValue().isBlank()
                    ? cbSchema.getValue().trim() : (sourceSchema != null ? sourceSchema : "dbo");
            String targetTable = txtName.getText().trim();
            CopyTableOptions opt = new CopyTableOptions(
                    true,
                    chkPk.isSelected(),
                    chkIndexes.isSelected(),
                    chkForeignKeys.isSelected(),
                    chkConstraints.isSelected(),
                    chkTriggers.isSelected()
            );
            result[0] = new CopyTableResult(targetSchema, targetTable, opt);
            chrome.close();
        });

        chrome.content(content).footer(null, cancel, ok).size(480, -1);
        chrome.showAndWait();

        return Optional.ofNullable(result[0]);
    }
}

