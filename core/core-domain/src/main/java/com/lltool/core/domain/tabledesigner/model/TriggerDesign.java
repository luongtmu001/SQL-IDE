package com.lltool.core.domain.tabledesigner.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Domain entity representing a table trigger definition.
 */
public class TriggerDesign implements Serializable {

    private String name;
    private String timing;
    private List<String> events = new ArrayList<>();
    private String definition;

    public TriggerDesign() {}

    public TriggerDesign(String name, String definition) {
        this.name = name != null ? name.trim() : "";
        this.definition = definition != null ? definition.trim() : "";
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name != null ? name.trim() : "";
    }

    public String getTiming() {
        return timing;
    }

    public void setTiming(String timing) {
        this.timing = timing != null ? timing.trim() : null;
    }

    public List<String> getEvents() {
        return events;
    }

    public void setEvents(List<String> events) {
        this.events = events != null ? new ArrayList<>(events) : new ArrayList<>();
    }

    public String getDefinition() {
        return definition;
    }

    public void setDefinition(String definition) {
        this.definition = definition != null ? definition.trim() : "";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TriggerDesign that = (TriggerDesign) o;
        return Objects.equals(name != null ? name.toLowerCase() : null, that.name != null ? that.name.toLowerCase() : null) &&
                Objects.equals(definition != null ? definition.trim() : null, that.definition != null ? that.definition.trim() : null);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name != null ? name.toLowerCase() : null, definition != null ? definition.trim() : null);
    }
}

