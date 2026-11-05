package app.models;

import com.lltool.core.application.connection.dto.ConnectionProfileDto;
import com.lltool.infrastructure.database.api.metadata.ColumnMetadata;
import com.lltool.infrastructure.database.api.metadata.TableMetadata;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DatabaseNode {

    private final String name;
    private final DatabaseNodeType type;
    private String subText;
    private final String iconLiteral;
    private final boolean isPrimaryKey;
    private final boolean isForeignKey;
    private String connectionId;
    private ConnectionProfileDto connectionProfile;
    private String catalog;
    private String schema;
    private TableMetadata tableMetadata;
    private ColumnMetadata columnMetadata;
    private boolean loaded = false;
    /** Full group path ('A/B') for GROUP nodes and the group of CONNECTION nodes. */
    private String groupPath;
    private final List<DatabaseNode> children = new ArrayList<>();

    public DatabaseNode(String name, DatabaseNodeType type) {
        this(name, type, null, null, false, false);
    }

    public DatabaseNode(String name, DatabaseNodeType type, String subText) {
        this(name, type, subText, null, false, false);
    }

    public DatabaseNode(String name, DatabaseNodeType type, String subText, String iconLiteral) {
        this(name, type, subText, iconLiteral, false, false);
    }

    public DatabaseNode(String name, DatabaseNodeType type, String subText, String iconLiteral, boolean isPrimaryKey, boolean isForeignKey) {
        this.name = name;
        this.type = type;
        this.subText = subText;
        this.iconLiteral = iconLiteral;
        this.isPrimaryKey = isPrimaryKey;
        this.isForeignKey = isForeignKey;
    }

    public String getName() {
        return name;
    }

    public DatabaseNodeType getType() {
        return type;
    }

    public String getSubText() {
        return subText;
    }

    public DatabaseNode setSubText(String subText) {
        this.subText = subText;
        return this;
    }

    public String getIconLiteral() {
        return iconLiteral;
    }

    /**
     * Resolves the appropriate icon for this node type and metadata.
     * Uses the explicitly configured iconLiteral if provided, otherwise infers
     * the most specific icon (e.g. data-type-aware column icons, table, view, etc.).
     */
    public String getEffectiveIconLiteral() {
        if (iconLiteral != null && !iconLiteral.isBlank()) {
            return iconLiteral;
        }

        return switch (type) {
            case ROOT -> "fth-database";
            case GROUP -> "fth-folder";
            case CONNECTION -> "fth-server";
            case CATEGORY_DATABASES, DATABASE -> "mdi2d-database";
            case CATEGORY_SCHEMAS, SCHEMA -> "fth-folder";
            case CATEGORY_TABLES -> "mdi2t-table-multiple";
            case TABLE -> "mdi2t-table";
            case CATEGORY_COLUMNS -> "fth-folder";
            case CATEGORY_KEYS -> "fth-folder";
            case KEY -> isPrimaryKey ? "mdi2k-key-variant" : "mdi2k-key-link";
            case CATEGORY_CONSTRAINTS -> "fth-folder";
            case CONSTRAINT -> "mdi2s-shield-check-outline";
            case CATEGORY_VIEWS -> "mdi2t-table-headers-eye";
            case VIEW -> "mdi2t-table-eye";
            case CATEGORY_MATERIALIZED_VIEWS, MATERIALIZED_VIEW -> "mdi2l-layers-triple-outline";
            case CATEGORY_FUNCTIONS -> "mdi2c-calculator";
            case FUNCTION -> "mdi2f-function-variant";
            case CATEGORY_PROCEDURES -> "mdi2c-code-tags";
            case PROCEDURE -> "mdi2c-cog-outline";
            case CATEGORY_TRIGGERS -> (tableMetadata != null) ? "fth-folder" : "fth-zap";
            case TRIGGER -> "fth-zap";
            case CATEGORY_INDEXES -> (tableMetadata != null) ? "fth-folder" : "fth-bookmark";
            case INDEX -> "fth-bookmark";
            case CATEGORY_SEQUENCES, SEQUENCE -> "mdi2a-arrow-up-down";
            case CATEGORY_USER_TYPES, USER_TYPE -> "mdi2c-cube-outline";
            case ROLES_USERS -> "mdi2a-account-group";
            case SERVER_OBJECTS -> "fth-server";
            case LOADING -> "fth-loader";
            case COLUMN -> resolveColumnIcon();
        };
    }

    private String resolveColumnIcon() {
        if (isPrimaryKey) {
            return "mdi2k-key-variant";
        }
        if (isForeignKey) {
            return "mdi2k-key-link";
        }
        if (subText != null) {
            String lower = subText.toLowerCase();
            if (lower.contains("int") || lower.contains("numeric") || lower.contains("decimal") ||
                lower.contains("float") || lower.contains("double") || lower.contains("bigint") ||
                lower.contains("number") || lower.contains("real")) {
                return "mdi2n-numeric";
            }
            if (lower.contains("char") || lower.contains("text") || lower.contains("string") ||
                lower.contains("clob") || lower.contains("varchar")) {
                return "mdi2f-format-letter-case";
            }
            if (lower.contains("time") || lower.contains("date")) {
                return "mdi2c-calendar-clock";
            }
        }
        return "mdi2t-table-column";
    }

    public boolean isPrimaryKey() {
        return isPrimaryKey;
    }

    public boolean isForeignKey() {
        return isForeignKey;
    }

    public String getConnectionId() {
        return connectionId;
    }

    public DatabaseNode setConnectionId(String connectionId) {
        this.connectionId = connectionId;
        return this;
    }

    public ConnectionProfileDto getConnectionProfile() {
        return connectionProfile;
    }

    public DatabaseNode setConnectionProfile(ConnectionProfileDto connectionProfile) {
        this.connectionProfile = connectionProfile;
        if (connectionProfile != null) {
            this.connectionId = connectionProfile.id();
        }
        return this;
    }

    public String getGroupPath() {
        return groupPath;
    }

    public DatabaseNode setGroupPath(String groupPath) {
        this.groupPath = groupPath;
        return this;
    }

    public String getCatalog() {
        return catalog;
    }

    public DatabaseNode setCatalog(String catalog) {
        this.catalog = catalog;
        return this;
    }

    public String getSchema() {
        return schema;
    }

    public DatabaseNode setSchema(String schema) {
        this.schema = schema;
        return this;
    }

    public TableMetadata getTableMetadata() {
        return tableMetadata;
    }

    public DatabaseNode setTableMetadata(TableMetadata tableMetadata) {
        this.tableMetadata = tableMetadata;
        return this;
    }

    public ColumnMetadata getColumnMetadata() {
        return columnMetadata;
    }

    public DatabaseNode setColumnMetadata(ColumnMetadata columnMetadata) {
        this.columnMetadata = columnMetadata;
        return this;
    }

    public boolean isLoaded() {
        return loaded;
    }

    public DatabaseNode setLoaded(boolean loaded) {
        this.loaded = loaded;
        return this;
    }

    public void clearChildren() {
        children.clear();
    }

    public List<DatabaseNode> getChildren() {
        return Collections.unmodifiableList(children);
    }

    public DatabaseNode addChild(DatabaseNode child) {
        children.add(child);
        return this;
    }

    public boolean hasChildren() {
        return !children.isEmpty();
    }

    @Override
    public String toString() {
        return name;
    }
}
