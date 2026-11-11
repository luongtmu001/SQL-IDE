package app.framework.syntax;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SqlServerSyntaxAdapterTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void testBatchesGoCorpus() throws Exception {
        InputStream in = getClass().getResourceAsStream("/corpus/syntax/batches_go.json");
        assertNotNull(in, "batches_go.json corpus resource must be present");

        List<Map<String, Object>> cases = json.readValue(in, new TypeReference<>() {});
        assertFalse(cases.isEmpty());

        for (var c : cases) {
            String id = (String) c.get("id");
            String sql = (String) c.get("sql");
            @SuppressWarnings("unchecked")
            Map<String, Object> expect = (Map<String, Object>) c.get("expect");

            int expectedCount = ((Number) expect.get("batchCount")).intValue();
            @SuppressWarnings("unchecked")
            List<Number> expectedStartLines = (List<Number>) expect.get("startLines");

            List<SqlServerSyntaxAdapter.SqlBatch> batches = SqlServerSyntaxAdapter.splitSqlServerBatches(sql);
            assertEquals(expectedCount, batches.size(), "Batch count mismatch for " + id);

            for (int i = 0; i < expectedCount; i++) {
                assertEquals(expectedStartLines.get(i).intValue(), batches.get(i).startLine(),
                        "Start line mismatch for " + id + " batch " + i);
            }
        }
    }

    @Test
    void testComputeTokenPosition() {
        String line = "SELECT my_col FROM t";
        int[] pos = SqlServerSyntaxAdapter.computeTokenPosition(line, "my_col");
        assertEquals(8, pos[0]);
        assertEquals(14, pos[1]);
    }

    @Test
    void testComputeTokenPositionWordBoundary() {
        String line = "SELECT col_a, col_b FROM t";
        int[] pos = SqlServerSyntaxAdapter.computeTokenPosition(line, "col_b");
        assertEquals(15, pos[0]);
        assertEquals(20, pos[1]);
    }
}

