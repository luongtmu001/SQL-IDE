package app.ui.dataeditor;

import app.ui.components.PaneHeader;
import com.lltool.core.domain.tabledesigner.model.CheckDesign;
import com.lltool.core.domain.tabledesigner.model.ForeignKeyDesign;
import com.lltool.core.domain.tabledesigner.model.TableDesign;
import com.lltool.core.domain.tabledesigner.model.UniqueDesign;
import com.lltool.core.domain.tableeditor.model.TableEditorMetadata;
import javafx.animation.TranslateTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.*;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Slide-out inspector drawer for displaying table schema metadata and constraints.
 */
public class ConstraintInspectorPane extends VBox {

    private final Runnable onClose;
    private final VBox contentBox = new VBox(12);

    public ConstraintInspectorPane(Runnable onClose) {
        this.onClose = onClose;
        setPrefWidth(300);
        setMinWidth(300);
        setMaxWidth(300);
        getStyleClass().add("tde-inspector-drawer");

        PaneHeader header = new PaneHeader("Thông tin bảng", "fth-info");

        Button btnClose = new Button();
        btnClose.getStyleClass().addAll("button", "small");
        FontIcon xIcon = new FontIcon("fth-x");
        xIcon.setIconSize(12);
        btnClose.setGraphic(xIcon);
        btnClose.setOnAction(e -> {
            if (onClose != null) onClose.run();
        });
        header.addAction(btnClose);

        ScrollPane sp = new ScrollPane(contentBox);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        VBox.setVgrow(sp, Priority.ALWAYS);

        contentBox.setPadding(new Insets(10));

        getChildren().addAll(header, sp);
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

        // 1. General Overview Section
        VBox generalCard = createSectionCard("Tổng quan", "fth-database", "-color-accent-fg");
        generalCard.getChildren().addAll(
                new Label("Bảng: " + schema + "." + table),
                new Label("Số cột: " + (meta != null ? meta.getColumns().size() : 0))
        );
        contentBox.getChildren().add(generalCard);

        // 2. Primary Key Section
        if (meta != null && !meta.getPrimaryKeyNames().isEmpty()) {
            VBox pkCard = createSectionCard("Khoá chính", "fth-key", "-color-warning-emphasis");
            pkCard.getChildren().add(new Label("Cột: " + String.join(", ", meta.getPrimaryKeyNames())));
            contentBox.getChildren().add(pkCard);
        }

        // 3. Foreign Keys Section
        if (design != null && !design.getForeignKeys().isEmpty()) {
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
        if (design != null && !design.getUniques().isEmpty()) {
            VBox uqCard = createSectionCard("Ràng buộc duy nhất", "fth-shield", "-color-purple-emphasis");
            for (UniqueDesign uq : design.getUniques()) {
                Label lbl = new Label(uq.getName() + " (" + String.join(", ", uq.getColumns()) + ")");
                lbl.setStyle("-fx-font-family: monospace; -fx-font-size: 0.85em;");
                uqCard.getChildren().add(lbl);
            }
            contentBox.getChildren().add(uqCard);
        }

        // 5. Check Constraints Section
        if (design != null && !design.getChecks().isEmpty()) {
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

    private VBox createSectionCard(String title, String iconLiteral, String iconColorVar) {
        VBox card = new VBox(6);
        card.setStyle("-fx-background-color: -color-bg-subtle; -fx-padding: 8; -fx-border-color: -color-border-subtle; -fx-border-radius: 4; -fx-background-radius: 4;");

        HBox cardHeader = new HBox(6);
        cardHeader.setAlignment(Pos.CENTER_LEFT);

        FontIcon icon = new FontIcon(iconLiteral);
        icon.setIconSize(12);
        icon.setStyle("-fx-icon-color: " + iconColorVar + ";");

        Label lbl = new Label(title);
        lbl.setStyle("-fx-font-weight: bold; -fx-font-size: 0.85em; -fx-text-fill: -color-fg-default;");

        cardHeader.getChildren().addAll(icon, lbl);
        card.getChildren().add(cardHeader);

        return card;
    }
}
