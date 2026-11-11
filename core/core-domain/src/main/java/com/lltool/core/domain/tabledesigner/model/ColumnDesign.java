package com.lltool.core.domain.tabledesigner.model;

import com.lltool.core.domain.tabledesigner.valueobject.ColumnId;

import java.io.Serializable;
import java.util.Objects;

/**
 * Domain entity representing a table column definition in Table Designer.
 */
public class ColumnDesign implements Serializable, Cloneable {

    private final ColumnId id;
    private String originalName;
    private String name;
    private String dataType;
    private Integer length;
    private Integer scale;
    private boolean notNull;
    private boolean primaryKey;
    private String defaultValue;
    private boolean defaultIsExpression;
    private String defaultConstraintName;
    private String collation;
    private boolean identity;
    private Long identitySeed;
    private Long identityIncrement;
    private String sequenceName;
    private String computedFormula;
    private boolean computedPersisted;
    private String comment;

    public ColumnDesign(ColumnId id, String name, String dataType) {
        this.id = id != null ? id : ColumnId.generate();
        this.name = name != null ? name.trim() : "";
        this.dataType = dataType != null ? dataType.trim().toLowerCase() : "varchar";
        this.identitySeed = 1L;
        this.identityIncrement = 1L;
    }

    public static ColumnDesign of(String name, String dataType) {
        return new ColumnDesign(ColumnId.generate(), name, dataType);
    }

    public static ColumnDesign loadedFromDb(String name, String dataType) {
        ColumnDesign col = new ColumnDesign(ColumnId.generate(), name, dataType);
        col.setOriginalName(name);
        return col;
    }

    /**
     * Clones this column for duplicate operations (Fixes Bug B1).
     * The duplicate receives a brand new immutable ColumnId and originalName is reset to null,
     * ensuring the differ never confuses a duplicate column with renaming the original.
     */
    public ColumnDesign duplicate(String newName) {
        ColumnDesign copy = new ColumnDesign(ColumnId.generate(), newName, this.dataType);
        copy.originalName = null;
        copy.length = this.length;
        copy.scale = this.scale;
        copy.notNull = this.notNull;
        copy.primaryKey = false; // Primary key flag is NOT copied by default
        copy.defaultValue = this.defaultValue;
        copy.defaultIsExpression = this.defaultIsExpression;
        copy.defaultConstraintName = null;
        copy.collation = this.collation;
        copy.identity = false; // Identity is not copied
        copy.identitySeed = this.identitySeed;
        copy.identityIncrement = this.identityIncrement;
        copy.sequenceName = null;
        copy.computedFormula = this.computedFormula;
        copy.computedPersisted = this.computedPersisted;
        copy.comment = this.comment;
        return copy;
    }

    public ColumnId getId() {
        return id;
    }

    public String getOriginalName() {
        return originalName;
    }

    public void setOriginalName(String originalName) {
        this.originalName = originalName != null && !originalName.isBlank() ? originalName.trim() : null;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name != null ? name.trim() : "";
    }

    public String getDataType() {
        return dataType;
    }

    public void setDataType(String dataType) {
        this.dataType = dataType != null ? dataType.trim() : "";
    }

    public Integer getLength() {
        return length;
    }

    public void setLength(Integer length) {
        this.length = length;
    }

    public Integer getScale() {
        return scale;
    }

    public void setScale(Integer scale) {
        this.scale = scale;
    }

    public boolean isNotNull() {
        return notNull;
    }

    public void setNotNull(boolean notNull) {
        this.notNull = notNull;
    }

    public boolean isPrimaryKey() {
        return primaryKey;
    }

    public void setPrimaryKey(boolean primaryKey) {
        this.primaryKey = primaryKey;
        if (primaryKey) {
            this.notNull = true;
        }
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue != null && !defaultValue.isBlank() ? defaultValue.trim() : null;
    }

    public boolean isDefaultIsExpression() {
        return defaultIsExpression;
    }

    public void setDefaultIsExpression(boolean defaultIsExpression) {
        this.defaultIsExpression = defaultIsExpression;
    }

    public String getDefaultConstraintName() {
        return defaultConstraintName;
    }

    public void setDefaultConstraintName(String defaultConstraintName) {
        this.defaultConstraintName = defaultConstraintName != null && !defaultConstraintName.isBlank()
                ? defaultConstraintName.trim() : null;
    }

    public String getCollation() {
        return collation;
    }

    public void setCollation(String collation) {
        this.collation = collation != null && !collation.isBlank() ? collation.trim() : null;
    }

    public boolean isIdentity() {
        return identity;
    }

    public void setIdentity(boolean identity) {
        this.identity = identity;
    }

    public Long getIdentitySeed() {
        return identitySeed;
    }

    public void setIdentitySeed(Long identitySeed) {
        this.identitySeed = identitySeed != null ? identitySeed : 1L;
    }

    public Long getIdentityIncrement() {
        return identityIncrement;
    }

    public void setIdentityIncrement(Long identityIncrement) {
        this.identityIncrement = identityIncrement != null ? identityIncrement : 1L;
    }

    public String getSequenceName() {
        return sequenceName;
    }

    public void setSequenceName(String sequenceName) {
        this.sequenceName = sequenceName != null && !sequenceName.isBlank() ? sequenceName.trim() : null;
    }

    public String getComputedFormula() {
        return computedFormula;
    }

    public void setComputedFormula(String computedFormula) {
        this.computedFormula = computedFormula != null && !computedFormula.isBlank() ? computedFormula.trim() : null;
    }

    public boolean isComputed() {
        return computedFormula != null && !computedFormula.isBlank();
    }

    public boolean isComputedPersisted() {
        return computedPersisted;
    }

    public void setComputedPersisted(boolean computedPersisted) {
        this.computedPersisted = computedPersisted;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment != null && !comment.isBlank() ? comment.trim() : null;
    }

    public boolean isRenamed() {
        return originalName != null && !originalName.equalsIgnoreCase(name);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ColumnDesign that = (ColumnDesign) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "ColumnDesign{" +
                "name='" + name + '\'' +
                ", dataType='" + dataType + '\'' +
                ", length=" + length +
                ", scale=" + scale +
                ", notNull=" + notNull +
                ", primaryKey=" + primaryKey +
                '}';
    }
}

