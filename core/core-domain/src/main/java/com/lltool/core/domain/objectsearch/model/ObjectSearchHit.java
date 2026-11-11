package com.lltool.core.domain.objectsearch.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents a matched SQL object containing keyword matches in its definition.
 */
public class ObjectSearchHit {

    private final String catalog;
    private final String schema;
    private final String name;
    private final String type;
    private int matchCount;
    private List<SearchMatchLocation> matches;
    private String ddl;

    public ObjectSearchHit(String catalog, String schema, String name, String type) {
        this(catalog, schema, name, type, 0, new ArrayList<>(), null);
    }

    public ObjectSearchHit(String catalog, String schema, String name, String type,
                           int matchCount, List<SearchMatchLocation> matches, String ddl) {
        this.catalog = catalog != null ? catalog : "";
        this.schema = schema != null ? schema : "dbo";
        this.name = name != null ? name : "";
        this.type = type != null ? type.toUpperCase() : "OBJECT";
        this.matchCount = matchCount;
        this.matches = matches != null ? new ArrayList<>(matches) : new ArrayList<>();
        this.ddl = ddl;
    }

    public String getCatalog() {
        return catalog;
    }

    public String getSchema() {
        return schema;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public int getMatchCount() {
        return matchCount;
    }

    public void setMatchCount(int matchCount) {
        this.matchCount = matchCount;
    }

    public List<SearchMatchLocation> getMatches() {
        return Collections.unmodifiableList(matches);
    }

    public void setMatches(List<SearchMatchLocation> matches) {
        this.matches = matches != null ? new ArrayList<>(matches) : new ArrayList<>();
        this.matchCount = this.matches.size();
    }

    public void addMatch(SearchMatchLocation match) {
        if (match != null) {
            this.matches.add(match);
            this.matchCount = this.matches.size();
        }
    }

    public String getDdl() {
        return ddl;
    }

    public void setDdl(String ddl) {
        this.ddl = ddl;
    }

    public String getQualifiedName() {
        if (schema.isEmpty()) {
            return name;
        }
        return schema + "." + name;
    }

    public String getFirstSnippet() {
        if (matches != null && !matches.isEmpty()) {
            return matches.get(0).snippet();
        }
        return "";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ObjectSearchHit that = (ObjectSearchHit) o;
        return Objects.equals(catalog, that.catalog) &&
                Objects.equals(schema, that.schema) &&
                Objects.equals(name, that.name) &&
                Objects.equals(type, that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(catalog, schema, name, type);
    }

    @Override
    public String toString() {
        return "ObjectSearchHit{" +
                "type='" + type + '\'' +
                ", qualifiedName='" + getQualifiedName() + '\'' +
                ", matchCount=" + matchCount +
                '}';
    }
}

