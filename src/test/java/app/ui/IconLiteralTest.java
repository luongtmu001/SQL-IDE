package app.ui;

import com.lltool.core.domain.dependency.DependencyNodeType;
import com.lltool.core.domain.schemacompare.model.DiffStatus;
import com.lltool.core.domain.schemacompare.model.SchemaObjectType;
import com.lltool.core.domain.visualizer.QueryNodeType;
import org.junit.jupiter.api.Test;
import org.kordamp.ikonli.javafx.FontIcon;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class IconLiteralTest {

    @Test
    void testAllEnumIconLiteralsAreValid() {
        for (SchemaObjectType type : SchemaObjectType.values()) {
            assertNotNull(type.getIconLiteral(), "SchemaObjectType " + type + " has null icon");
            assertDoesNotThrow(() -> new FontIcon(type.getIconLiteral()), "Invalid icon for SchemaObjectType " + type);
        }

        for (DiffStatus status : DiffStatus.values()) {
            assertNotNull(status.getIconLiteral(), "DiffStatus " + status + " has null icon");
            assertDoesNotThrow(() -> new FontIcon(status.getIconLiteral()), "Invalid icon for DiffStatus " + status);
        }

        for (DependencyNodeType type : DependencyNodeType.values()) {
            assertNotNull(type.getIconLiteral(), "DependencyNodeType " + type + " has null icon");
            assertDoesNotThrow(() -> new FontIcon(type.getIconLiteral()), "Invalid icon for DependencyNodeType " + type);
        }

        for (QueryNodeType type : QueryNodeType.values()) {
            assertNotNull(type.getIconLiteral(), "QueryNodeType " + type + " has null icon");
            assertDoesNotThrow(() -> new FontIcon(type.getIconLiteral()), "Invalid icon for QueryNodeType " + type);
        }
    }
}

