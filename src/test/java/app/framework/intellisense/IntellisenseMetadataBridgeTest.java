package app.framework.intellisense;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.framework.metadata.DesktopMetadataService.RoutineParam;
import app.framework.metadata.ObjectScriptService;
import com.lltool.infrastructure.database.api.DatabaseType;
import com.lltool.infrastructure.database.api.metadata.ColumnMetadata;
import com.lltool.infrastructure.database.api.metadata.KeyMetadata;
import com.lltool.infrastructure.database.api.metadata.RoutineMetadata;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/** The rows the JavaScript engine reads must keep the field names of the Python API. */
class IntellisenseMetadataBridgeTest {

    @Test
    void columnsUseThePythonFieldNames() {
        var rows = IntellisenseMetadataBridge.columnRows(List.of(new ColumnMetadata("Id", "int", 4, false, true, false, null, 1)));
        assertEquals("Id", rows.get(0).get("name"));
        assertEquals("int", rows.get(0).get("data_type"));
        assertEquals(false, rows.get(0).get("is_nullable"));
        assertEquals(true, rows.get(0).get("is_pk"));
    }

    @Test
    void returnValueIsNotAParameterAndOutIsDetected() {
        var rows = IntellisenseMetadataBridge.parameterRows(List.of(
                new RoutineParam("Return Value", "int", "RETURN"),
                new RoutineParam("@a", "int", "IN"),
                new RoutineParam("@b", "int", "INOUT")));
        assertEquals(2, rows.size());
        assertEquals(false, rows.get(0).get("is_output"));
        assertEquals(true, rows.get(1).get("is_output"));
    }

    @Test
    void postgresOverloadsBecomeOneSuggestion() {
        var rows = IntellisenseMetadataBridge.routineRows(List.of(
                new RoutineMetadata("calc(integer)", "public", "db", "FUNCTION", "int"),
                new RoutineMetadata("calc(text, integer)", "public", "db", "FUNCTION", "int"),
                new RoutineMetadata("run_job", null, "db", "PROCEDURE", null)), "public");
        assertEquals(2, rows.size());
        assertEquals("calc", rows.get(0).get("name"));
        assertEquals("function", rows.get(0).get("type"));
        assertEquals("procedure", rows.get(1).get("type"));
        assertEquals("public", rows.get(1).get("schema"));
    }

    @Test
    void foreignKeysKeepOnlyRealReferences() {
        var rows = IntellisenseMetadataBridge.foreignKeyRows(List.of(
                new KeyMetadata("PK_o", "PRIMARY KEY", "Orders", List.of("Id")),
                new KeyMetadata("FK_o_c", "FOREIGN KEY", "Orders", List.of("CustomerId"), "Customers", List.of("Id"))), "Orders");
        assertEquals(1, rows.size());
        assertEquals("Orders", rows.get(0).get("from_table"));
        assertEquals(List.of("CustomerId"), rows.get(0).get("from_columns"));
        assertEquals("Customers", rows.get(0).get("to_table"));
        assertEquals(List.of("Id"), rows.get(0).get("to_columns"));
    }

    @Test
    void definitionOnlyForObjectsThatHaveOne() {
        assertEquals(ObjectScriptService.Kind.PROCEDURE, IntellisenseMetadataBridge.kindOf("procedure"));
        assertEquals(ObjectScriptService.Kind.TABLE, IntellisenseMetadataBridge.kindOf("TABLE"));
        assertNull(IntellisenseMetadataBridge.kindOf("keyword"));
        assertNull(IntellisenseMetadataBridge.kindOf(null));
    }

    @Test
    void schemaFallsBackToTheDialectDefault() {
        assertEquals("dbo", IntellisenseMetadataBridge.defaultSchema(DatabaseType.SQLSERVER, null));
        assertEquals("public", IntellisenseMetadataBridge.defaultSchema(DatabaseType.POSTGRESQL, " "));
        assertEquals("sales", IntellisenseMetadataBridge.defaultSchema(DatabaseType.SQLSERVER, "sales"));
        assertNull(IntellisenseMetadataBridge.defaultSchema(DatabaseType.SQLITE, null));
    }

    @Test
    void badRequestIsAnsweredWithAnError() {
        List<String> answers = new ArrayList<>();
        var bridge = new IntellisenseMetadataBridge((id, json) -> answers.add(id + ":" + json));
        bridge.handle("r1", "objects", "not json");
        assertEquals(1, answers.size());
        assertTrue(answers.get(0).startsWith("r1:"));
        assertTrue(answers.get(0).contains("\"ok\":false"));
        assertFalse(answers.get(0).contains("\"ok\":true"));
    }
}
