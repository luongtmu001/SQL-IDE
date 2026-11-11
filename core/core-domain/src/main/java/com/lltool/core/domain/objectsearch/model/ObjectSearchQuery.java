package com.lltool.core.domain.objectsearch.model;

import java.util.*;

/**
 * Encapsulates the search query criteria for searching SQL object definitions.
 */
public class ObjectSearchQuery {

    public static final Set<String> ALL_TYPES = Set.of("VIEW", "PROCEDURE", "FUNCTION", "TRIGGER");

    private String keyword;
    private String catalog;
    private String schema;
    private Set<String> objectTypes = new LinkedHashSet<>(ALL_TYPES);
    private boolean caseSensitive = false;
    private boolean wholeWord = false;
    private boolean isRegex = false;

    public ObjectSearchQuery() {}

    public ObjectSearchQuery(String keyword) {
        this.keyword = keyword;
    }

    public ObjectSearchQuery(String keyword, String catalog, String schema, Set<String> objectTypes,
                             boolean caseSensitive, boolean wholeWord, boolean isRegex) {
        this.keyword = keyword;
        this.catalog = catalog;
        this.schema = schema;
        if (objectTypes != null && !objectTypes.isEmpty()) {
            this.objectTypes = new LinkedHashSet<>(objectTypes);
        }
        this.caseSensitive = caseSensitive;
        this.wholeWord = wholeWord;
        this.isRegex = isRegex;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public String getCatalog() {
        return catalog;
    }

    public void setCatalog(String catalog) {
        this.catalog = catalog;
    }

    public String getSchema() {
        return schema;
    }

    public void setSchema(String schema) {
        this.schema = schema;
    }

    public Set<String> getObjectTypes() {
        return Collections.unmodifiableSet(objectTypes);
    }

    public void setObjectTypes(Set<String> objectTypes) {
        if (objectTypes == null || objectTypes.isEmpty()) {
            this.objectTypes = new LinkedHashSet<>(ALL_TYPES);
        } else {
            this.objectTypes = new LinkedHashSet<>(objectTypes);
        }
    }

    public boolean isCaseSensitive() {
        return caseSensitive;
    }

    public void setCaseSensitive(boolean caseSensitive) {
        this.caseSensitive = caseSensitive;
    }

    public boolean isWholeWord() {
        return wholeWord;
    }

    public void setWholeWord(boolean wholeWord) {
        this.wholeWord = wholeWord;
    }

    public boolean isRegex() {
        return isRegex;
    }

    public void setRegex(boolean regex) {
        isRegex = regex;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ObjectSearchQuery that = (ObjectSearchQuery) o;
        return caseSensitive == that.caseSensitive &&
                wholeWord == that.wholeWord &&
                isRegex == that.isRegex &&
                Objects.equals(keyword, that.keyword) &&
                Objects.equals(catalog, that.catalog) &&
                Objects.equals(schema, that.schema) &&
                Objects.equals(objectTypes, that.objectTypes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(keyword, catalog, schema, objectTypes, caseSensitive, wholeWord, isRegex);
    }

    @Override
    public String toString() {
        return "ObjectSearchQuery{" +
                "keyword='" + keyword + '\'' +
                ", catalog='" + catalog + '\'' +
                ", schema='" + schema + '\'' +
                ", objectTypes=" + objectTypes +
                ", caseSensitive=" + caseSensitive +
                ", wholeWord=" + wholeWord +
                ", isRegex=" + isRegex +
                '}';
    }
}

