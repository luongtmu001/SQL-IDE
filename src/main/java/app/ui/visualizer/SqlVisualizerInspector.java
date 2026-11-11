package app.ui.visualizer;

import app.ui.components.AppButton;
import com.lltool.core.domain.visualizer.QueryFlowNode;
import com.lltool.core.domain.visualizer.SourceRange;
import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Inspector / Detail Drawer for selected Query Flow Operator (Phase 3 - Slice 6).
 * Shows operator properties, execution estimates, and the exact SQL fragment with copy action.
 */
public class SqlVisualizerInspector extends VBox {

    private final Label titleLabel = new Label("Chưa chọn toán tử nào");
    private final FontIcon headerIcon = new FontIcon("fth-info");

    private final Label lblOpType = new Label("-");
    private final Label lblStage = new Label("-");
    private final Label lblCost = new Label("-");
    private final Label lblRows = new Label("-");
    private final Label lblDescription = new Label("-");
    private final Label lblRange = new Label("-");

    private final Label sqlSnippetArea = new Label("-- Chọn một bước trên sơ đồ để xem đoạn SQL tương ứng");
    private final AppButton btnCopySql = new AppButton("Sao chép SQL", "fth-copy", AppButton.Variant.DEFAULT);

    private QueryFlowNode currentNode;

    public SqlVisualizerInspector() {
        getStyleClass().add("qf-inspector-root");
        setSpacing(10);
        setPadding(new Insets(12, 16, 12, 16));

        // Top Header
        HBox headerRow = new HBox(8);
        headerRow.setAlignment(Pos.CENTER_LEFT);
        headerIcon.setIconSize(16);
        titleLabel.getStyleClass().add("qf-inspector-header");
        headerRow.getChildren().addAll(headerIcon, titleLabel);

        // Grid of properties
        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(6);

        grid.add(createFieldLabel("Loại thao tác:"), 0, 0);
        grid.add(lblOpType, 1, 0);

        grid.add(createFieldLabel("Giai đoạn:"), 0, 1);
        grid.add(lblStage, 1, 1);

        grid.add(createFieldLabel("Chi phí ước tính:"), 2, 0);
        grid.add(lblCost, 3, 0);

        grid.add(createFieldLabel("Dữ liệu ước tính:"), 2, 1);
        grid.add(lblRows, 3, 1);

        grid.add(createFieldLabel("Mô tả:"), 0, 2);
        grid.add(lblDescription, 1, 2, 3, 1);

        grid.add(createFieldLabel("Vị trí câu lệnh:"), 0, 3);
        grid.add(lblRange, 1, 3, 3, 1);

        // SQL Snippet Container
        VBox snippetBox = new VBox(6);
        HBox snippetHeader = new HBox(8);
        snippetHeader.setAlignment(Pos.CENTER_LEFT);

        Label lblSnippetTitle = new Label("Đoạn mã SQL:");
        lblSnippetTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 0.85em; -fx-text-fill: -color-fg-muted;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        btnCopySql.setDisable(true);
        btnCopySql.setOnAction(e -> copySnippetToClipboard());

        snippetHeader.getChildren().addAll(lblSnippetTitle, spacer, btnCopySql);

        sqlSnippetArea.getStyleClass().add("qf-sql-snippet-box");
        sqlSnippetArea.setWrapText(true);
        sqlSnippetArea.setMaxWidth(Double.MAX_VALUE);
        sqlSnippetArea.setMinHeight(48);

        snippetBox.getChildren().addAll(snippetHeader, sqlSnippetArea);

        getChildren().addAll(headerRow, grid, snippetBox);
    }

    private Label createFieldLabel(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("qf-inspector-field-label");
        return l;
    }

    public void inspectNode(QueryFlowNode node) {
        this.currentNode = node;
        if (node == null) {
            titleLabel.setText("Chưa chọn toán tử nào");
            headerIcon.setIconLiteral("fth-info");
            lblOpType.setText("-");
            lblStage.setText("-");
            lblCost.setText("-");
            lblRows.setText("-");
            lblDescription.setText("-");
            lblRange.setText("-");
            sqlSnippetArea.setText("-- Chọn một bước trên sơ đồ để xem đoạn SQL tương ứng");
            btnCopySql.setDisable(true);
            return;
        }

        titleLabel.setText(String.format("Bước %d: %s (%s)", node.getStepIndex(), node.getTitle(), node.getType().name()));
        headerIcon.setIconLiteral("fth-cpu");

        lblOpType.setText(node.getType().name());
        lblStage.setText("Giai đoạn " + (node.getStageIndex() + 1));
        lblCost.setText(node.getEstimatedCost() != null ? node.getEstimatedCost() : "N/A");
        lblRows.setText(node.getRowCount() != null ? String.format("%,d dòng", node.getRowCount()) : "N/A");
        lblDescription.setText(node.getDetails() != null ? node.getDetails() : node.getSubtitle());

        SourceRange r = node.getSourceRange();
        if (r != null && r.getStartLine() > 0) {
            lblRange.setText(String.format("Dòng %d:%d -> Dòng %d:%d (ký tự %d-%d)",
                    r.getStartLine(), r.getStartCol(), r.getEndLine(), r.getEndCol(), r.getStartOffset(), r.getEndOffset()));
        } else {
            lblRange.setText("Tự động phân giải từ AST");
        }

        String sqlFrag = node.getSqlFragment();
        if (sqlFrag != null && !sqlFrag.isBlank()) {
            sqlSnippetArea.setText(sqlFrag.trim());
            btnCopySql.setDisable(false);
        } else {
            sqlSnippetArea.setText("-- Không có đoạn trích SQL cho bước này");
            btnCopySql.setDisable(true);
        }
    }

    private void copySnippetToClipboard() {
        if (currentNode != null && currentNode.getSqlFragment() != null) {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(currentNode.getSqlFragment());
            clipboard.setContent(content);

            btnCopySql.setText("Đã sao chép!");
            PauseTransition pause = new PauseTransition(Duration.seconds(2));
            pause.setOnFinished(e -> btnCopySql.setText("Sao chép SQL"));
            pause.play();
        }
    }

    public QueryFlowNode getCurrentNode() {
        return currentNode;
    }
}
