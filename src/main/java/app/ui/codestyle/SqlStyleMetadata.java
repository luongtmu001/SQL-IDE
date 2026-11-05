package app.ui.codestyle;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.*;

/**
 * DLG-009 metadata: 15 categories (4 groups), 31 options and sample SQL per category,
 * taken from LLTool {@code sql-style-metadata.js} (resource /codestyle/sql-style-metadata.json).
 */
public final class SqlStyleMetadata {

    public record Category(String id, String name, String group, String groupLabel, int order) {
    }

    public record Choice(String value, String label) {
    }

    /** {@code equals} / {@code notEquals}: the option is enabled only when the other option matches. */
    public record Dependency(String optionId, Object equals, Object notEquals) {
    }

    public record Option(String id, String category, String label, String desc, String type,
                         Double min, Double max, Double step, Object defaultValue,
                         List<Choice> choices, List<Dependency> dependencies) {
    }

    private static SqlStyleMetadata instance;

    private final List<Category> categories = new ArrayList<>();
    private final List<Option> options = new ArrayList<>();
    private final Map<String, String> samples = new HashMap<>();

    public static synchronized SqlStyleMetadata get() {
        if (instance == null) instance = new SqlStyleMetadata();
        return instance;
    }

    private SqlStyleMetadata() {
        try (InputStream in = SqlStyleMetadata.class.getResourceAsStream("/codestyle/sql-style-metadata.json")) {
            JsonNode root = new ObjectMapper().readTree(in);
            for (JsonNode c : root.path("categories")) {
                categories.add(new Category(c.path("id").asText(), c.path("name").asText(),
                        c.path("group").asText(), c.path("groupLabel").asText(), c.path("order").asInt()));
            }
            categories.sort(Comparator.comparingInt(Category::order));
            for (JsonNode o : root.path("options")) {
                List<Choice> ch = new ArrayList<>();
                for (JsonNode x : o.path("options")) ch.add(new Choice(x.path("value").asText(), x.path("label").asText()));
                List<Dependency> deps = new ArrayList<>();
                for (JsonNode d : o.path("dependencies")) {
                    deps.add(new Dependency(d.path("optionId").asText(),
                            d.has("equals") ? value(d.get("equals")) : null,
                            d.has("notEquals") ? value(d.get("notEquals")) : null));
                }
                options.add(new Option(o.path("id").asText(), o.path("category").asText(), o.path("label").asText(),
                        o.path("desc").asText(""), o.path("type").asText(),
                        o.has("min") ? o.get("min").asDouble() : null, o.has("max") ? o.get("max").asDouble() : null,
                        o.has("step") ? o.get("step").asDouble() : null, value(o.get("default")), ch, deps));
            }
            root.path("samples").fields().forEachRemaining(e -> samples.put(e.getKey(), e.getValue().asText()));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot load SQL style metadata", e);
        }
    }

    private static Object value(JsonNode n) {
        if (n == null || n.isNull()) return null;
        if (n.isBoolean()) return n.asBoolean();
        if (n.isIntegralNumber()) return n.asInt();
        if (n.isNumber()) return n.asDouble();
        return n.asText();
    }

    public List<Category> categories() {
        return categories;
    }

    public List<Option> options() {
        return options;
    }

    public List<Option> options(String categoryId) {
        return options.stream().filter(o -> o.category().equals(categoryId)).toList();
    }

    public String sample(String categoryId) {
        return samples.getOrDefault(categoryId, samples.getOrDefault("default", "SELECT 1;"));
    }

    public Map<String, Object> defaults() {
        Map<String, Object> m = new LinkedHashMap<>();
        for (Option o : options) m.put(o.id(), o.defaultValue());
        return m;
    }
}
