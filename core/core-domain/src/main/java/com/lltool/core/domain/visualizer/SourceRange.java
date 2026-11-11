package com.lltool.core.domain.visualizer;

import java.util.Objects;

/**
 * 1-based text coordinates for linking AST visual nodes to SQL text positions.
 */
public class SourceRange {
    private final int startLine;
    private final int startCol;
    private final int endLine;
    private final int endCol;
    private final int startOffset;
    private final int endOffset;

    public SourceRange() {
        this(1, 1, 1, 1, 0, 0);
    }

    public SourceRange(int startLine, int startCol, int endLine, int endCol, int startOffset, int endOffset) {
        this.startLine = Math.max(1, startLine);
        this.startCol = Math.max(1, startCol);
        this.endLine = Math.max(this.startLine, endLine);
        this.endCol = Math.max(1, endCol);
        this.startOffset = Math.max(0, startOffset);
        this.endOffset = Math.max(this.startOffset, endOffset);
    }

    public boolean contains(int line, int col) {
        if (line < startLine || line > endLine) return false;
        if (line == startLine && col < startCol) return false;
        if (line == endLine && col > endCol) return false;
        return true;
    }

    public int getStartLine() {
        return startLine;
    }

    public int getStartCol() {
        return startCol;
    }

    public int getEndLine() {
        return endLine;
    }

    public int getEndCol() {
        return endCol;
    }

    public int getStartOffset() {
        return startOffset;
    }

    public int getEndOffset() {
        return endOffset;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SourceRange that)) return false;
        return startLine == that.startLine && startCol == that.startCol &&
                endLine == that.endLine && endCol == that.endCol &&
                startOffset == that.startOffset && endOffset == that.endOffset;
    }

    @Override
    public int hashCode() {
        return Objects.hash(startLine, startCol, endLine, endCol, startOffset, endOffset);
    }

    @Override
    public String toString() {
        return String.format("[%d:%d - %d:%d]", startLine, startCol, endLine, endCol);
    }
}
