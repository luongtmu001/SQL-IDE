package app.ui.dataeditor;

import com.lltool.core.domain.tabledesigner.model.*;
import com.lltool.core.domain.tableeditor.model.TableEditorMetadata;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Slide-out Inspector Drawer for Table Data Editor.
 * Displays comprehensive table metadata, Primary Key, Foreign Keys, Unique/Check constraints.
 */
public class ConstraintInspectorPane extends VBox {

    private final VBox contentBox = new VBox(10);
    private Runnable onClose;

    public ConstraintInspectorPane(Runnable onClose) {
        this.onClose = onClose;
        setPrefWidth(320);
        setMinWidth(260);
        setMaxWidth(450);
        getStyleClass().add("tde-inspector-drawer");
        setStyle("-fx-background-color: -color-bg-default; -fx-border-color: -color-border-subtle; -fx-border-width: 0 0 0 1;");

        HBox header = buildHeader();
        ScrollPane sp = new ScrollPane(contentBox);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        VBox.setVgrow(sp, Priority.ALWAYS);

        contentBox.setPadding(new Insets(10));

        getChildren().addAll(header, sp);
    }

    private HBox buildHeader() {
        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(4, 10, 4, 10));
        header.getStyleClass().add("tde-pane-header");
        header.setStyle("-fx-background-color: -color-bg-subtle; -fx-border-color: -color-border-subtle; -fx-border-width: 0 0 1 0;");

        FontIcon infoIcon = new FontIcon("fth-info");
        infoIcon.setIconSize(13);
        infoIcon.setStyle("-fx-icon-color: -color-accent-fg;");

        Label lblTitle = new Label("Schema & Constraints Inspector");
        lblTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 0.85em; -fx-text-fill: -color-fg-default;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button btnClose = new Button();
        btnClose.getStyleClass().addAll("button", "small");
        FontIcon xIcon = new FontIcon("fth-x");
        xIcon.setIconSize(12);
        btnClose.setGraphic(xIcon);
        btnClose.setOnAction(e -> {
            if (onClose != null) onClose.run();
        });

        header.getChildren().addAll(infoIcon, lblTitle, spacer, btnClose);
        return header;
    }

    public void update(TableDesign design, TableEditorMetadata meta) {
        contentBox.getChildren().clear();

        if (design == null && meta == null) {
            Label lbl = new Label("Chưa có thông tin bảng.");
            lbl.setStyle("-fx-text-fill: -color-fg-muted;");
            contentBox.getChildren().add(lbl);
            return;
        }

        String table = design != null ? design.getTable() : (meta != null ? meta.getTable() : "");
        String schema = design != null ? design.getSchema() : (meta != null ? meta.getSchema() : "");
        String engine = design != null ? design.getEngine() : (meta != null ? meta.getEngine() : "");

        // 1. Table Summary Card
        VBox summaryCard = new VBox(4);
        summaryCard.setPadding(new Insets(8));
        summaryCard.setStyle("-fx-background-color: -color-bg-subtle; -fx-border-color: -color-border-subtle; -fx-border-radius: 4; -fx-background-radius: 4;");

        Label lblSumTitle = new Label("THÔNG TIN BẢNG");
        lblSumTitle.setStyle("-fx-font-size: 0.75em; -fx-font-weight: bold; -fx-text-fill: -color-fg-muted;");

        summaryCard.getChildren().addAll(
                lblSumTitle,
                rowLabel("Bảng:", "[" + schema + "].[" + table + "]"),
                rowLabel("Hệ quản trị:", engine.toUpperCase()),
                rowLabel("Khóa chính:", (meta != null && !meta.getPrimaryKeyNames().isEmpty()) ? String.join(", ", meta.getPrimaryKeyNames()) : "Không có PK")
        );
        contentBox.getChildren().add(summaryCard);

        if (design == null) return;

        // 2. Primary Key Section
        if (meta != null && !meta.getPrimaryKeyNames().isEmpty()) {
            VBox pkCard = createSectionCard("Khoá chính", "fth-key", "-color-warning-emphasis");
            pkCard.getChildren().add(new Label("Cột: " + String.join(", ", meta.getPrimaryKeyNames())));
            contentBox.getChildren().add(pkCard);
        }

        // 3. Foreign Keys Section
        if (!design.getForeignKeys().isEmpty()) {
            VBox fkCard = createSectionCard("Khoá ngoại", "fth-link", "-color-accent-fg");
            for (ForeignKeyDesign fk : design.getForeignKeys()) {
                VBox fkItem = new VBox(2);
                fkItem.setStyle("-fx-background-color: -color-bg-default; -fx-padding: 4; -fx-border-color: -color-border-subtle; -fx-border-radius: 3; -fx-background-radius: 3;");
                Label nameLbl = new Label(fk.getName() != null ? fk.getName() : "FK");
                nameLbl.setStyle("-fx-font-weight: bold; -fx-font-family: monospace;");
                Label targetLbl = new Label("→ " + fk.getTargetTable() + " (On Delete: " + fk.getOnDelete() + ")");
                targetLbl.setStyle("-fx-text-fill: -color-fg-muted; -fx-font-size: 0.85em;");
                fkItem.getChildren().addAll(nameLbl, targetLbl);
                fkCard.getChildren().add(fkItem);
            }
            contentBox.getChildren().add(fkCard);
        }

        // 4. Unique Constraints Section
        if (!design.getUniques().isEmpty()) {
            VBox uqCard = createSectionCard("Ràng buộc duy nhất", "fth-shield", "-color-purple-emphasis");
            for (UniqueDesign uq : design.getUniques()) {
                Label lbl = new Label(uq.getName() + " (" + String.join(", ", uq.getColumns()) + ")");
                lbl.setStyle("-fx-font-family: monospace; -fx-font-size: 0.85em;");
                uqCard.getChildren().add(lbl);
            }
            contentBox.getChildren().add(uqCard);
        }

        // 5. Check Constraints Section
        if (!design.getChecks().isEmpty()) {
            VBox chkCard = createSectionCard("Ràng buộc kiểm tra", "fth-check-circle", "-color-success-emphasis");
            for (CheckDesign chk : design.getChecks()) {
                VBox item = new VBox(2);
                Label name = new Label(chk.getName());
                name.setStyle("-fx-font-weight: bold; -fx-font-family: monospace;");
                Label expr = new Label(chk.getCheckClause());
                expr.setStyle("-fx-font-family: monospace; -fx-font-size: 0.8em; -fx-text-fill: -color-accent-fg;");
                item.getChildren().addAll(name, expr);
                chkCard.getChildren().add(item);
            }
            contentBox.getChildren().add(chkCard);
        }
    }

    private HBox rowLabel(String label, String value) {
        HBox box = new HBox(6);
        Label l = new Label(label);
        l.setStyle("-fx-text-fill: -color-fg-muted; -fx-font-size: 0.85em;");
        Label v = new Label(value);
        v.setStyle("-fx-font-weight: bold; -fx-font-size: 0.85em; -fx-font-family: monospace;");
        box.getChildren().addAll(l, v);
        return box;
    }

    private VBox createSectionCard(String title, String iconLit, String accentColor) {
        VBox card = new VBox(6);
        card.setPadding(new Insets(8));
        card.setStyle("-fx-background-color: -color-bg-subtle; -fx-border-color: -color-border-subtle; -fx-border-radius: 4; -fx-background-radius: 4;");

        HBox titleBox = new HBox(6);
        titleBox.setAlignment(Pos.CENTER_LEFT);
        FontIcon icon = new FontIcon(iconLit);
        icon.setIconSize(12);
        icon.setStyle("-fx-icon-color: " + accentColor + ";");
        Label lbl = new Label(title);
        lbl.setStyle("-fx-font-weight: bold; -fx-font-size: 0.85em; -fx-text-fill: " + accentColor + ";");
        titleBox.getChildren().addAll(icon, lbl);

        card.getChildren().add(titleBox);
        return card;
    }
}
