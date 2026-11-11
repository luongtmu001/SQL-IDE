package app.ui.visualizer;

import app.ui.components.AppButton;
import app.ui.components.PaneHeader;
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

/**
 * Inspector / Detail Drawer for selected Query Flow Operator (Phase 3 - Slice 6).
 * Shows operator properties, execution estimates, and the exact SQL fragment with copy action.
 * Standardized with PaneHeader and fixed 140px 2-column GridPane (G-SV-6, G-SV-3).
 */
public class SqlVisualizerInspector extends VBox {

    private final PaneHeader header = new PaneHeader("Chưa chọn toán tử nào", "fth-info");
    private final GridPane grid = new GridPane();

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
        setPadding(new Insets(10, 14, 10, 14));
        setMinWidth(0);

        // Grid of properties: 2 columns (label: 140px fixed, value: stretch)
        grid.setHgap(16);
        grid.setVgap(6);
        ColumnConstraints col1 = new ColumnConstraints();
        col1.setMinWidth(140);
        col1.setPrefWidth(140);
        col1.setMaxWidth(140);
        ColumnConstraints col2 = new ColumnConstraints();
        col2.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(col1, col2);

        // SQL Snippet Container
        VBox snippetBox = new VBox(6);
        HBox snippetHeader = new HBox(8);
        snippetHeader.setAlignment(Pos.CENTER_LEFT);

        Label lblSnippetTitle = new Label("Đoạn mã SQL:");
        lblSnippetTitle.getStyleClass().add("qf-inspector-field-label");

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

        getChildren().addAll(header, grid, snippetBox);

        inspectNode(null);
    }

    private Label createFieldLabel(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("qf-inspector-field-label");
        return l;
    }

    public void inspectNode(QueryFlowNode node) {
        this.currentNode = node;
        grid.getChildren().clear();

        if (node == null) {
            header.setTitle("Chưa chọn toán tử nào");
            header.setIconLiteral("fth-info");
            header.setSubtitle(null);

            lblOpType.setText("-");
            lblStage.setText("-");
            lblDescription.setText("-");
            lblRange.setText("-");

            grid.add(createFieldLabel("Loại thao tác:"), 0, 0);
            grid.add(lblOpType, 1, 0);

            grid.add(createFieldLabel("Giai đoạn:"), 0, 1);
            grid.add(lblStage, 1, 1);

            sqlSnippetArea.setText("-- Chọn một bước trên sơ đồ để xem đoạn SQL tương ứng");
            btnCopySql.setDisable(true);
            return;
        }

        header.setTitle(String.format("Bước %d: %s", node.getStepIndex(), node.getTitle()));
        header.setIconLiteral("fth-cpu");
        header.setSubtitle("Loại: " + node.getType().name());

        lblOpType.setText(node.getType().name());
        lblStage.setText("Giai đoạn " + (node.getStageIndex() + 1));
        lblDescription.setText(node.getDetails() != null ? node.getDetails() : node.getSubtitle());

        SourceRange r = node.getSourceRange();
        if (r != null && r.getStartLine() > 0) {
            lblRange.setText(String.format("Dòng %d:%d -> Dòng %d:%d (ký tự %d-%d)",
                    r.getStartLine(), r.getStartCol(), r.getEndLine(), r.getEndCol(), r.getStartOffset(), r.getEndOffset()));
        } else {
            lblRange.setText("Tự động phân giải từ AST");
        }

        int row = 0;
        grid.add(createFieldLabel("Loại thao tác:"), 0, row);
        grid.add(lblOpType, 1, row++);

        grid.add(createFieldLabel("Giai đoạn:"), 0, row);
        grid.add(lblStage, 1, row++);

        // G-SV-3: Omit cost and rows if null
        if (node.getEstimatedCost() != null && !node.getEstimatedCost().isBlank()) {
            lblCost.setText(node.getEstimatedCost());
            grid.add(createFieldLabel("Chi phí ước tính:"), 0, row);
            grid.add(lblCost, 1, row++);
        }

        if (node.getRowCount() != null) {
            lblRows.setText(String.format("%,d dòng", node.getRowCount()));
            grid.add(createFieldLabel("Dữ liệu ước tính:"), 0, row);
            grid.add(lblRows, 1, row++);
        }

        grid.add(createFieldLabel("Mô tả:"), 0, row);
        grid.add(lblDescription, 1, row++);

        grid.add(createFieldLabel("Vị trí câu lệnh:"), 0, row);
        grid.add(lblRange, 1, row++);

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
