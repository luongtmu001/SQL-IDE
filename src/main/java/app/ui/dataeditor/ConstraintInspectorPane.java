package app.ui.dataeditor;

import app.ui.components.IconScale;
import app.ui.components.Icons;
import app.ui.components.PaneHeader;
import com.lltool.core.domain.tabledesigner.model.CheckDesign;
import com.lltool.core.domain.tabledesigner.model.ForeignKeyDesign;
import com.lltool.core.domain.tabledesigner.model.TableDesign;
import com.lltool.core.domain.tabledesigner.model.UniqueDesign;
import com.lltool.core.domain.tableeditor.model.TableEditorMetadata;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.*;
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
        btnClose.getStyleClass().add("button");
        btnClose.setGraphic(Icons.of("fth-x", IconScale.SM));
        btnClose.setOnAction(e -> {
            if (onClose != null) onClose.run();
        });
        header.addAction(btnClose);

        ScrollPane sp = new ScrollPane(contentBox);
        sp.setFitToWidth(true);
        sp.getStyleClass().add("tde-inspector-scroll");
        VBox.setVgrow(sp, Priority.ALWAYS);

        contentBox.setPadding(new Insets(10));

        getChildren().addAll(header, sp);
    }

    public void update(TableDesign design, TableEditorMetadata meta) {
        contentBox.getChildren().clear();

        if (design == null && meta == null) {
            Label lbl = new Label("Chưa có thông tin bảng.");
            lbl.getStyleClass().add("text-secondary");
            contentBox.getChildren().add(lbl);
            return;
        }

        String table = design != null ? design.getTable() : (meta != null ? meta.getTable() : "");
        String schema = design != null ? design.getSchema() : (meta != null ? meta.getSchema() : "");

        // 1. General Overview Section
        VBox generalCard = createSectionCard("Tổng quan", "fth-database", "icon-accent");
        generalCard.getChildren().addAll(
                new Label("Bảng: " + schema + "." + table),
                new Label("Số cột: " + (meta != null ? meta.getColumns().size() : 0))
        );
        contentBox.getChildren().add(generalCard);

        // 2. Primary Key Section
        if (meta != null && !meta.getPrimaryKeyNames().isEmpty()) {
            VBox pkCard = createSectionCard("Khoá chính", "fth-key", "icon-warning");
            pkCard.getChildren().add(new Label("Cột: " + String.join(", ", meta.getPrimaryKeyNames())));
            contentBox.getChildren().add(pkCard);
        }

        // 3. Foreign Keys Section
        if (design != null && !design.getForeignKeys().isEmpty()) {
            VBox fkCard = createSectionCard("Khoá ngoại", "fth-link", "icon-accent");
            for (ForeignKeyDesign fk : design.getForeignKeys()) {
                VBox fkItem = new VBox(2);
                fkItem.getStyleClass().add("tde-fk-item");
                Label nameLbl = new Label(fk.getName() != null ? fk.getName() : "FK");
                nameLbl.getStyleClass().add("tde-fk-title");
                Label targetLbl = new Label(fk.getTargetTable() + " (On Delete: " + fk.getOnDelete() + ")");
                targetLbl.setGraphic(app.ui.components.Icons.of("fth-arrow-right", app.ui.components.IconScale.SM));
                targetLbl.getStyleClass().add("tde-fk-target");
                fkItem.getChildren().addAll(nameLbl, targetLbl);
                fkCard.getChildren().add(fkItem);
            }
            contentBox.getChildren().add(fkCard);
        }

        // 4. Unique Constraints Section
        if (design != null && !design.getUniques().isEmpty()) {
            VBox uqCard = createSectionCard("Ràng buộc duy nhất", "fth-shield", "icon-accent");
            for (UniqueDesign uq : design.getUniques()) {
                Label lbl = new Label(uq.getName() + " (" + String.join(", ", uq.getColumns()) + ")");
                lbl.getStyleClass().addAll("code-inline", "text-secondary");
                uqCard.getChildren().add(lbl);
            }
            contentBox.getChildren().add(uqCard);
        }

        // 5. Check Constraints Section
        if (design != null && !design.getChecks().isEmpty()) {
            VBox chkCard = createSectionCard("Ràng buộc kiểm tra", "fth-check-circle", "icon-success");
            for (CheckDesign chk : design.getChecks()) {
                VBox item = new VBox(2);
                Label name = new Label(chk.getName());
                name.getStyleClass().add("tde-fk-title");
                Label expr = new Label(chk.getCheckClause());
                expr.getStyleClass().add("tde-check-expr");
                item.getChildren().addAll(name, expr);
                chkCard.getChildren().add(item);
            }
            contentBox.getChildren().add(chkCard);
        }
    }

    private VBox createSectionCard(String title, String iconLiteral, String iconStyleClass) {
        VBox card = new VBox(6);
        card.getStyleClass().add("tde-section-card");

        HBox cardHeader = new HBox(6);
        cardHeader.setAlignment(Pos.CENTER_LEFT);

        FontIcon icon = Icons.of(iconLiteral, IconScale.SM);
        if (iconStyleClass != null && !iconStyleClass.isBlank()) {
            icon.getStyleClass().add(iconStyleClass);
        }

        Label lbl = new Label(title);
        lbl.getStyleClass().add("tde-section-title");

        cardHeader.getChildren().addAll(icon, lbl);
        card.getChildren().add(cardHeader);

        return card;
    }
}
