package app.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SQL Snippet Model compatible with Red Gate SQL Prompt 10 and Monaco Editor snippets.
 * Supports auto-conversion of Red Gate placeholders ($var$) to Monaco tabstops (${1:var}),
 * automatically highlighting/selecting the first variable on expansion.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SqlSnippet implements Serializable, Cloneable {

    private String id;
    private String prefix;
    private String description;
    private String body;
    private String dbType = "all"; // "all", "sqlserver", "postgresql"
    private boolean isPinned = false;
    private List<String> tags = new ArrayList<>();
    private List<Placeholder> placeholders = new ArrayList<>();

    public SqlSnippet() {
        this.id = UUID.randomUUID().toString();
    }

    public SqlSnippet(String prefix, String description, String body) {
        this();
        this.prefix = prefix;
        this.description = description;
        this.body = body;
    }

    public SqlSnippet(String prefix, String description, String body, String dbType) {
        this(prefix, description, body);
        this.dbType = dbType != null ? dbType : "all";
    }

    // ─────────────────────────────────────────────────────────────
    // Monaco Snippet Converter
    // ─────────────────────────────────────────────────────────────

    /**
     * Converts Red Gate template body (e.g. "SELECT * FROM $table$ WHERE $column$ = $value$")
     * into Monaco snippet format with tabstops (e.g. "SELECT * FROM ${1:table} WHERE ${2:column} = ${3:value}").
     * When Monaco inserts this, it automatically focuses on and selects/highlights the first variable ("table").
     */
    public String toMonacoSnippetBody() {
        if (body == null || body.isEmpty()) {
            return "";
        }
        // If it already contains Monaco tabstops like ${1:...} or $1, return as is
        if (body.matches("(?s).*\\$\\{\\d+:.*\\}.*") || body.matches("(?s).*\\$\\d+.*")) {
            return body;
        }

        String result = body;

        // Convert $SELECTIONSTART$...$SELECTIONEND$ if present
        if (result.contains("$SELECTIONSTART$") && result.contains("$SELECTIONEND$")) {
            result = result.replaceAll("\\$SELECTIONSTART\\$(.*?)\\$SELECTIONEND\\$", "${1:$TM_SELECTED_TEXT}");
        }

        Map<String, Integer> varIndices = new LinkedHashMap<>();
        int[] counter = {1};

        Pattern pattern = Pattern.compile("\\$([a-zA-Z0-9_]+)\\$");
        Matcher matcher = pattern.matcher(result);
        StringBuilder sb = new StringBuilder();

        while (matcher.find()) {
            String varName = matcher.group(1);
            String lower = varName.toLowerCase();
            if (lower.equals("selectionstart") || lower.equals("selectionend")) {
                matcher.appendReplacement(sb, "");
                continue;
            }
            if (lower.equals("selectedtext")) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement("${TM_SELECTED_TEXT}"));
                continue;
            }
            int index = varIndices.computeIfAbsent(varName, k -> counter[0]++);
            String replacement = "${" + index + ":" + varName + "}";
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    // ─────────────────────────────────────────────────────────────
    // Getters & Setters
    // ─────────────────────────────────────────────────────────────

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPrefix() {
        return prefix != null ? prefix : "";
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public String getDescription() {
        return description != null ? description : "";
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getBody() {
        return body != null ? body : "";
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getDbType() {
        return dbType != null ? dbType : "all";
    }

    public void setDbType(String dbType) {
        this.dbType = dbType;
    }

    @JsonProperty("isPinned")
    public boolean isPinned() {
        return isPinned;
    }

    public void setPinned(boolean pinned) {
        isPinned = pinned;
    }

    public List<String> getTags() {
        if (tags == null) tags = new ArrayList<>();
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags != null ? tags : new ArrayList<>();
    }

    public List<Placeholder> getPlaceholders() {
        if (placeholders == null) placeholders = new ArrayList<>();
        return placeholders;
    }

    public void setPlaceholders(List<Placeholder> placeholders) {
        this.placeholders = placeholders != null ? placeholders : new ArrayList<>();
    }

    @Override
    public SqlSnippet clone() {
        try {
            SqlSnippet copy = (SqlSnippet) super.clone();
            copy.tags = new ArrayList<>(this.tags);
            copy.placeholders = new ArrayList<>();
            for (Placeholder p : this.placeholders) {
                copy.placeholders.add(new Placeholder(p.getName(), p.getDefaultValue()));
            }
            return copy;
        } catch (CloneNotSupportedException e) {
            SqlSnippet copy = new SqlSnippet(this.prefix, this.description, this.body, this.dbType);
            copy.id = this.id;
            copy.isPinned = this.isPinned;
            copy.setTags(new ArrayList<>(this.tags));
            return copy;
        }
    }

    @Override
    public String toString() {
        return prefix + " — " + description;
    }

    // ─────────────────────────────────────────────────────────────
    // Nested Placeholder Class
    // ─────────────────────────────────────────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Placeholder implements Serializable {
        private String name;
        private String defaultValue;

        public Placeholder() {}

        public Placeholder(String name, String defaultValue) {
            this.name = name;
            this.defaultValue = defaultValue;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDefaultValue() {
            return defaultValue;
        }

        public void setDefaultValue(String defaultValue) {
            this.defaultValue = defaultValue;
        }
    }
}
