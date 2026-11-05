package app.ui.editor;

import javafx.application.Platform;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;

import java.util.function.BiConsumer;

public class SqlCodeArea extends CodeArea {

    private final IntegerProperty currentLine = new SimpleIntegerProperty(1);
    private final IntegerProperty currentColumn = new SimpleIntegerProperty(1);
    private BiConsumer<Integer, Integer> cursorChangeListener;

    public SqlCodeArea() {
        this("");
    }

    public SqlCodeArea(String text) {
        super(text);
        initializeEditor();
    }

    private void initializeEditor() {
        getStyleClass().add("sql-code-area");

        // Line Numbers Gutter
        setParagraphGraphicFactory(LineNumberFactory.get(this));

        // Initial Syntax Highlighting
        applyHighlighting();

        // Highlighting on Text Change
        textProperty().addListener((obs, oldText, newText) -> {
            applyHighlighting();
        });

        // Caret Position Tracking (Line & Column)
        caretPositionProperty().addListener((obs, oldPos, newPos) -> {
            int line = getCurrentParagraph() + 1;
            int col = getCaretColumn() + 1;
            currentLine.set(line);
            currentColumn.set(col);

            if (cursorChangeListener != null) {
                cursorChangeListener.accept(line, col);
            }
        });

        // Drag and Drop Target (Accepts table/column drops from TreeView)
        setupDragAndDrop();
    }

    private void applyHighlighting() {
        String text = getText();
        if (text != null && !text.isEmpty()) {
            try {
                setStyleSpans(0, SqlSyntaxHighlighter.computeHighlighting(text));
            } catch (Exception ignored) {
            }
        }
    }

    private void setupDragAndDrop() {
        setOnDragOver(event -> {
            if (event.getGestureSource() != this && event.getDragboard().hasString()) {
                event.acceptTransferModes(TransferMode.COPY);
            }
            event.consume();
        });

        setOnDragDropped((DragEvent event) -> {
            Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasString()) {
                String droppedText = db.getString();
                int targetCaret = getCaretPosition();
                insertText(targetCaret, droppedText);
                moveTo(targetCaret + droppedText.length());
                requestFocus();
                success = true;
            }
            event.setDropCompleted(success);
            event.consume();
        });
    }

    public void setCursorChangeListener(BiConsumer<Integer, Integer> listener) {
        this.cursorChangeListener = listener;
    }

    public int getCurrentLine() {
        return currentLine.get();
    }

    public int getCurrentColumn() {
        return currentColumn.get();
    }
}

