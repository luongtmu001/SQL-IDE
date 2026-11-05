package app.framework.metadata.filter;

import java.util.ArrayList;
import java.util.List;

/**
 * Filter for objects in a category (Tables, Views, Functions, etc.).
 * Supports multi-condition rules (Name, Schema) with AND/OR logic.
 */
public class ObjectFilter {

    public enum Operator {
        CONTAINS("Contains"),
        STARTS_WITH("Starts with"),
        ENDS_WITH("Ends with"),
        EQUALS("Equals"),
        NOT_EQUALS("Not equals");

        private final String displayName;

        Operator(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public static Operator fromDisplayName(String name) {
            for (Operator op : values()) {
                if (op.displayName.equalsIgnoreCase(name) || op.name().equalsIgnoreCase(name)) {
                    return op;
                }
            }
            return CONTAINS;
        }
    }

    public enum Logic {
        AND, OR
    }

    public record Condition(String field, Operator operator, String value) {
        public boolean matches(String targetName, String targetSchema) {
            if (value == null || value.isBlank()) return true;
            String text = "schema".equalsIgnoreCase(field) ? targetSchema : targetName;
            if (text == null) text = "";

            String tLower = text.toLowerCase();
            String vLower = value.trim().toLowerCase();

            return switch (operator) {
                case CONTAINS -> tLower.contains(vLower);
                case STARTS_WITH -> tLower.startsWith(vLower);
                case ENDS_WITH -> tLower.endsWith(vLower);
                case EQUALS -> tLower.equals(vLower);
                case NOT_EQUALS -> !tLower.equals(vLower);
            };
        }
    }

    private final List<Condition> conditions = new ArrayList<>();
    private Logic logic = Logic.AND;

    public ObjectFilter() {}

    public ObjectFilter(List<Condition> conditions, Logic logic) {
        if (conditions != null) {
            this.conditions.addAll(conditions);
        }
        this.logic = logic != null ? logic : Logic.AND;
    }

    public boolean isActive() {
        return !conditions.isEmpty();
    }

    public List<Condition> getConditions() {
        return conditions;
    }

    public void setConditions(List<Condition> conditions) {
        this.conditions.clear();
        if (conditions != null) {
            this.conditions.addAll(conditions);
        }
    }

    public Logic getLogic() {
        return logic;
    }

    public void setLogic(Logic logic) {
        this.logic = logic;
    }

    public boolean matches(String targetName, String targetSchema) {
        if (conditions.isEmpty()) return true;

        if (logic == Logic.AND) {
            for (Condition c : conditions) {
                if (!c.matches(targetName, targetSchema)) {
                    return false;
                }
            }
            return true;
        } else {
            for (Condition c : conditions) {
                if (c.matches(targetName, targetSchema)) {
                    return true;
                }
            }
            return false;
        }
    }

    public void clear() {
        conditions.clear();
        logic = Logic.AND;
    }
}

