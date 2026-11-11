package app.ui.designer;

import com.lltool.core.domain.tabledesigner.model.ColumnDesign;
import com.lltool.core.domain.tabledesigner.valueobject.ColumnId;
import javafx.beans.property.*;

import java.util.Objects;

/**
 * Observable ViewModel representing a single column row in Table Designer.
 * Tracks cell edits, dirty state, row status markers (+, ~, ↻, −), and enforces invariants.
 */
public class ColumnDesignViewModel {

    private final ColumnId id;
    private final String originalName;
    private final ColumnDesign originalState;

    private final StringProperty name = new SimpleStringProperty("");
    private final StringProperty dataType = new SimpleStringProperty("varchar");
    private final ObjectProperty<Integer> length = new SimpleObjectProperty<>(null);
    private final ObjectProperty<Integer> scale = new SimpleObjectProperty<>(null);
    private final BooleanProperty notNull = new SimpleBooleanProperty(false);
    private final BooleanProperty primaryKey = new SimpleBooleanProperty(false);
    private final StringProperty defaultValue = new SimpleStringProperty(null);
    private final BooleanProperty defaultIsExpression = new SimpleBooleanProperty(false);
    private final StringProperty defaultConstraintName = new SimpleStringProperty(null);
    private final StringProperty collation = new SimpleStringProperty(null);
    private final BooleanProperty identity = new SimpleBooleanProperty(false);
    private final ObjectProperty<Long> identitySeed = new SimpleObjectProperty<>(1L);
    private final ObjectProperty<Long> identityIncrement = new SimpleObjectProperty<>(1L);
    private final StringProperty sequenceName = new SimpleStringProperty(null);
    private final StringProperty computedFormula = new SimpleStringProperty(null);
    private final BooleanProperty computedPersisted = new SimpleBooleanProperty(false);
    private final StringProperty comment = new SimpleStringProperty(null);

    private final BooleanProperty deleted = new SimpleBooleanProperty(false);
    private final StringProperty statusMarker = new SimpleStringProperty("");
    private ColumnDesignSnapshot initialSnapshot;

    public record ColumnDesignSnapshot(
            String name,
            String dataType,
            Integer length,
            Integer scale,
            boolean notNull,
            boolean primaryKey,
            String defaultValue,
            boolean defaultIsExpression,
            String defaultConstraintName,
            String collation,
            boolean identity,
            Long identitySeed,
            Long identityIncrement,
            String sequenceName,
            String computedFormula,
            boolean computedPersisted,
            String comment
    ) {
        public static ColumnDesignSnapshot of(ColumnDesignViewModel vm) {
            return new ColumnDesignSnapshot(
                    vm.name.get(),
                    vm.dataType.get(),
                    vm.length.get(),
                    vm.scale.get(),
                    vm.notNull.get(),
                    vm.primaryKey.get(),
                    vm.defaultValue.get(),
                    vm.defaultIsExpression.get(),
                    vm.defaultConstraintName.get(),
                    vm.collation.get(),
                    vm.identity.get(),
                    vm.identitySeed.get(),
                    vm.identityIncrement.get(),
                    vm.sequenceName.get(),
                    vm.computedFormula.get(),
                    vm.computedPersisted.get(),
                    vm.comment.get()
            );
        }

        public boolean matches(ColumnDesignViewModel vm) {
            return Objects.equals(name, vm.name.get())
                    && Objects.equals(dataType, vm.dataType.get())
                    && Objects.equals(length, vm.length.get())
                    && Objects.equals(scale, vm.scale.get())
                    && notNull == vm.notNull.get()
                    && primaryKey == vm.primaryKey.get()
                    && Objects.equals(defaultValue, vm.defaultValue.get())
                    && defaultIsExpression == vm.defaultIsExpression.get()
                    && Objects.equals(defaultConstraintName, vm.defaultConstraintName.get())
                    && Objects.equals(collation, vm.collation.get())
                    && identity == vm.identity.get()
                    && (!identity || (Objects.equals(identitySeed, vm.identitySeed.get()) && Objects.equals(identityIncrement, vm.identityIncrement.get())))
                    && Objects.equals(sequenceName, vm.sequenceName.get())
                    && Objects.equals(computedFormula, vm.computedFormula.get())
                    && computedPersisted == vm.computedPersisted.get()
                    && Objects.equals(comment, vm.comment.get());
        }
    }

    public void captureSnapshot() {
        this.initialSnapshot = ColumnDesignSnapshot.of(this);
    }

    public ColumnDesignViewModel(ColumnId id, String originalName, ColumnDesign originalState) {
        this.id = id != null ? id : ColumnId.generate();
        this.originalName = originalName;
        this.originalState = originalState;

        // Auto-enforce NotNull when PrimaryKey is enabled
        primaryKey.addListener((obs, oldV, newV) -> {
            if (Boolean.TRUE.equals(newV)) {
                notNull.set(true);
            }
            updateStatusMarker();
        });

        // Prevent clearing NotNull while PrimaryKey is active
        notNull.addListener((obs, oldV, newV) -> {
            if (Boolean.FALSE.equals(newV) && primaryKey.get()) {
                notNull.set(true);
            }
            updateStatusMarker();
        });

        // Listen to property changes to update status marker
        name.addListener((obs, o, n) -> updateStatusMarker());
        dataType.addListener((obs, o, n) -> updateStatusMarker());
        length.addListener((obs, o, n) -> updateStatusMarker());
        scale.addListener((obs, o, n) -> updateStatusMarker());
        notNull.addListener((obs, o, n) -> updateStatusMarker());
        defaultValue.addListener((obs, o, n) -> updateStatusMarker());
        defaultIsExpression.addListener((obs, o, n) -> updateStatusMarker());
        defaultConstraintName.addListener((obs, o, n) -> updateStatusMarker());
        collation.addListener((obs, o, n) -> updateStatusMarker());
        identity.addListener((obs, o, n) -> updateStatusMarker());
        identitySeed.addListener((obs, o, n) -> updateStatusMarker());
        identityIncrement.addListener((obs, o, n) -> updateStatusMarker());
        sequenceName.addListener((obs, o, n) -> updateStatusMarker());
        computedFormula.addListener((obs, o, n) -> updateStatusMarker());
        computedPersisted.addListener((obs, o, n) -> updateStatusMarker());
        comment.addListener((obs, o, n) -> updateStatusMarker());
        deleted.addListener((obs, o, n) -> updateStatusMarker());

        updateStatusMarker();
    }

    public static ColumnDesignViewModel fromDomain(ColumnDesign domain) {
        ColumnDesignViewModel vm = new ColumnDesignViewModel(domain.getId(), domain.getOriginalName(), domain);
        vm.name.set(domain.getName());
        vm.dataType.set(domain.getDataType());
        vm.length.set(domain.getLength());
        vm.scale.set(domain.getScale());
        vm.notNull.set(domain.isNotNull());
        vm.primaryKey.set(domain.isPrimaryKey());
        vm.defaultValue.set(domain.getDefaultValue());
        vm.defaultIsExpression.set(domain.isDefaultIsExpression());
        vm.defaultConstraintName.set(domain.getDefaultConstraintName());
        vm.collation.set(domain.getCollation());
        vm.identity.set(domain.isIdentity());
        vm.identitySeed.set(domain.getIdentitySeed() != null ? domain.getIdentitySeed() : 1L);
        vm.identityIncrement.set(domain.getIdentityIncrement() != null ? domain.getIdentityIncrement() : 1L);
        vm.sequenceName.set(domain.getSequenceName());
        vm.computedFormula.set(domain.getComputedFormula());
        vm.computedPersisted.set(domain.isComputedPersisted());
        vm.comment.set(domain.getComment());
        vm.captureSnapshot();
        vm.updateStatusMarker();
        return vm;
    }

    public static ColumnDesignViewModel newColumn(String name, String dataType) {
        ColumnDesign domain = ColumnDesign.of(name, dataType);
        return fromDomain(domain);
    }

    public ColumnDesignViewModel duplicate(String newName) {
        ColumnDesignViewModel copy = new ColumnDesignViewModel(ColumnId.generate(), null, null);
        copy.name.set(newName != null ? newName : this.name.get() + "_copy");
        copy.dataType.set(this.dataType.get());
        copy.length.set(this.length.get());
        copy.scale.set(this.scale.get());
        copy.notNull.set(this.notNull.get());
        copy.primaryKey.set(false); // Primary key is NOT copied (Fix B1)
        copy.defaultValue.set(this.defaultValue.get());
        copy.defaultIsExpression.set(this.defaultIsExpression.get());
        copy.defaultConstraintName.set(null);
        copy.collation.set(this.collation.get());
        copy.identity.set(false);
        copy.identitySeed.set(this.identitySeed.get());
        copy.identityIncrement.set(this.identityIncrement.get());
        copy.sequenceName.set(null);
        copy.computedFormula.set(this.computedFormula.get());
        copy.computedPersisted.set(this.computedPersisted.get());
        copy.comment.set(this.comment.get());
        copy.updateStatusMarker();
        return copy;
    }

    public ColumnDesign toDomain() {
        ColumnDesign domain = new ColumnDesign(this.id, this.name.get(), this.dataType.get());
        domain.setOriginalName(this.originalName);
        domain.setLength(this.length.get());
        domain.setScale(this.scale.get());
        domain.setNotNull(this.notNull.get());
        domain.setPrimaryKey(this.primaryKey.get());
        domain.setDefaultValue(this.defaultValue.get());
        domain.setDefaultIsExpression(this.defaultIsExpression.get());
        domain.setDefaultConstraintName(this.defaultConstraintName.get());
        domain.setCollation(this.collation.get());
        domain.setIdentity(this.identity.get());
        domain.setIdentitySeed(this.identitySeed.get());
        domain.setIdentityIncrement(this.identityIncrement.get());
        domain.setSequenceName(this.sequenceName.get());
        domain.setComputedFormula(this.computedFormula.get());
        domain.setComputedPersisted(this.computedPersisted.get());
        domain.setComment(this.comment.get());
        return domain;
    }

    public void updateStatusMarker() {
        if (deleted.get()) {
            statusMarker.set("−");
        } else if (originalName == null || originalState == null) {
            statusMarker.set("+");
        } else if (!originalName.equalsIgnoreCase(name.get())) {
            statusMarker.set("↻");
        } else if (isModifiedFromOriginal()) {
            statusMarker.set("~");
        } else {
            statusMarker.set("");
        }
    }

    public boolean isModifiedFromOriginal() {
        if (originalState == null) return false;
        if (initialSnapshot != null) {
            return !initialSnapshot.matches(this);
        }
        if (!Objects.equals(dataType.get(), originalState.getDataType())) return true;
        if (!Objects.equals(length.get(), originalState.getLength())) return true;
        if (!Objects.equals(scale.get(), originalState.getScale())) return true;
        if (notNull.get() != originalState.isNotNull()) return true;
        if (primaryKey.get() != originalState.isPrimaryKey()) return true;
        if (!Objects.equals(defaultValue.get(), originalState.getDefaultValue())) return true;
        if (defaultIsExpression.get() != originalState.isDefaultIsExpression()) return true;
        if (!Objects.equals(defaultConstraintName.get(), originalState.getDefaultConstraintName())) return true;
        if (!Objects.equals(collation.get(), originalState.getCollation())) return true;
        if (identity.get() != originalState.isIdentity()) return true;
        if (!Objects.equals(identitySeed.get(), originalState.getIdentitySeed())) return true;
        if (!Objects.equals(identityIncrement.get(), originalState.getIdentityIncrement())) return true;
        if (!Objects.equals(sequenceName.get(), originalState.getSequenceName())) return true;
        if (!Objects.equals(computedFormula.get(), originalState.getComputedFormula())) return true;
        if (computedPersisted.get() != originalState.isComputedPersisted()) return true;
        if (!Objects.equals(comment.get(), originalState.getComment())) return true;
        return false;
    }

    public boolean isDirty() {
        return !statusMarker.get().isEmpty();
    }

    // Getters & Properties
    public ColumnId getId() { return id; }
    public String getOriginalName() { return originalName; }

    public String getName() { return name.get(); }
    public void setName(String val) { name.set(val != null ? val.trim() : ""); }
    public StringProperty nameProperty() { return name; }

    public String getDataType() { return dataType.get(); }
    public void setDataType(String val) { dataType.set(val != null ? val.trim().toLowerCase() : "varchar"); }
    public StringProperty dataTypeProperty() { return dataType; }

    public Integer getLength() { return length.get(); }
    public void setLength(Integer val) { length.set(val); }
    public ObjectProperty<Integer> lengthProperty() { return length; }

    public Integer getScale() { return scale.get(); }
    public void setScale(Integer val) { scale.set(val); }
    public ObjectProperty<Integer> scaleProperty() { return scale; }

    public boolean isNotNull() { return notNull.get(); }
    public void setNotNull(boolean val) {
        if (!val && isPrimaryKey()) {
            this.notNull.set(true);
            return;
        }
        notNull.set(val);
    }
    public BooleanProperty notNullProperty() { return notNull; }

    public boolean isPrimaryKey() { return primaryKey.get(); }
    public void setPrimaryKey(boolean val) { primaryKey.set(val); }
    public BooleanProperty primaryKeyProperty() { return primaryKey; }

    public String getDefaultValue() { return defaultValue.get(); }
    public void setDefaultValue(String val) { defaultValue.set(val); }
    public StringProperty defaultValueProperty() { return defaultValue; }

    public boolean isDefaultIsExpression() { return defaultIsExpression.get(); }
    public void setDefaultIsExpression(boolean val) { defaultIsExpression.set(val); }
    public BooleanProperty defaultIsExpressionProperty() { return defaultIsExpression; }

    public String getDefaultConstraintName() { return defaultConstraintName.get(); }
    public void setDefaultConstraintName(String val) { defaultConstraintName.set(val); }
    public StringProperty defaultConstraintNameProperty() { return defaultConstraintName; }

    public String getCollation() { return collation.get(); }
    public void setCollation(String val) { collation.set(val); }
    public StringProperty collationProperty() { return collation; }

    public boolean isIdentity() { return identity.get(); }
    public void setIdentity(boolean val) { identity.set(val); }
    public BooleanProperty identityProperty() { return identity; }

    public Long getIdentitySeed() { return identitySeed.get(); }
    public void setIdentitySeed(Long val) { identitySeed.set(val != null ? val : 1L); }
    public ObjectProperty<Long> identitySeedProperty() { return identitySeed; }

    public Long getIdentityIncrement() { return identityIncrement.get(); }
    public void setIdentityIncrement(Long val) { identityIncrement.set(val != null ? val : 1L); }
    public ObjectProperty<Long> identityIncrementProperty() { return identityIncrement; }

    public String getSequenceName() { return sequenceName.get(); }
    public void setSequenceName(String val) { sequenceName.set(val); }
    public StringProperty sequenceNameProperty() { return sequenceName; }

    public String getComputedFormula() { return computedFormula.get(); }
    public void setComputedFormula(String val) { computedFormula.set(val); }
    public StringProperty computedFormulaProperty() { return computedFormula; }

    public boolean isComputedPersisted() { return computedPersisted.get(); }
    public void setComputedPersisted(boolean val) { computedPersisted.set(val); }
    public BooleanProperty computedPersistedProperty() { return computedPersisted; }

    public String getComment() { return comment.get(); }
    public void setComment(String val) { comment.set(val); }
    public StringProperty commentProperty() { return comment; }

    public boolean isDeleted() { return deleted.get(); }
    public void setDeleted(boolean val) { deleted.set(val); }
    public BooleanProperty deletedProperty() { return deleted; }

    public String getStatusMarker() { return statusMarker.get(); }
    public StringProperty statusMarkerProperty() { return statusMarker; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ColumnDesignViewModel that = (ColumnDesignViewModel) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
