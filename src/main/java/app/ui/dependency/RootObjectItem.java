package app.ui.dependency;

import com.lltool.core.domain.dependency.DependencyNodeType;

import java.util.Objects;

/**
 * Item descriptor for the Root Object ComboBox in DependencyGraphTab (DG-2).
 * Prevents string splitting bugs on dotted or spaced object names.
 */
public record RootObjectItem(String schema, String name, DependencyNodeType type) {

    public RootObjectItem {
        schema = (schema != null && !schema.isBlank()) ? schema : "dbo";
        Objects.requireNonNull(name, "name must not be null");
        type = type != null ? type : DependencyNodeType.TABLE;
    }

    public String getDisplayName() {
        return (schema != null && !schema.isBlank() ? schema + "." : "") + name;
    }

    @Override
    public String toString() {
        return getDisplayName();
    }
}

