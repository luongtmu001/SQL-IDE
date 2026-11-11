package com.lltool.core.domain.objectsearch.model;

import java.util.Objects;

/**
 * Represents the location of a keyword match within an SQL object definition.
 */
public record SearchMatchLocation(
        int line,
        int column,
        int length,
        String snippet
) {
    public SearchMatchLocation {
        if (line < 1) line = 1;
        if (column < 1) column = 1;
        if (length < 0) length = 0;
        if (snippet == null) snippet = "";
    }

    @Override
    public String toString() {
        return "Line " + line + ", Col " + column + ": " + (snippet.length() > 60 ? snippet.substring(0, 57) + "..." : snippet);
    }
}

