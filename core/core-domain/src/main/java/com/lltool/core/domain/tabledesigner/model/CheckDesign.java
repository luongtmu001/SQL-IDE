package com.lltool.core.domain.tabledesigner.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * Domain entity representing a CHECK constraint.
 */
public class CheckDesign implements Serializable {

    private String name;
    private String checkClause;

    public CheckDesign() {}

    public CheckDesign(String name, String checkClause) {
        this.name = name != null ? name.trim() : "";
        this.checkClause = checkClause != null ? checkClause.trim() : "";
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name != null ? name.trim() : "";
    }

    public String getCheckClause() {
        return checkClause;
    }

    public void setCheckClause(String checkClause) {
        this.checkClause = checkClause != null ? checkClause.trim() : "";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CheckDesign that = (CheckDesign) o;
        return Objects.equals(name != null ? name.toLowerCase() : null, that.name != null ? that.name.toLowerCase() : null) &&
                Objects.equals(checkClause != null ? checkClause.replaceAll("\\s+", " ") : null,
                        that.checkClause != null ? that.checkClause.replaceAll("\\s+", " ") : null);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name != null ? name.toLowerCase() : null,
                checkClause != null ? checkClause.replaceAll("\\s+", " ") : null);
    }
}

