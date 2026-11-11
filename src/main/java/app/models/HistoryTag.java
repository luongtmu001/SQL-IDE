package app.models;

import java.io.Serializable;

/**
 * Model representing a tag in Query History.
 */
public class HistoryTag implements Serializable {
    private long tagId;
    private String tagName;
    private String color;
    private int usageCount;

    public HistoryTag() {
    }

    public HistoryTag(long tagId, String tagName, String color, int usageCount) {
        this.tagId = tagId;
        this.tagName = tagName;
        this.color = color;
        this.usageCount = usageCount;
    }

    public long getTagId() { return tagId; }
    public void setTagId(long tagId) { this.tagId = tagId; }

    public String getTagName() { return tagName; }
    public void setTagName(String tagName) { this.tagName = tagName; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    public int getUsageCount() { return usageCount; }
    public void setUsageCount(int usageCount) { this.usageCount = usageCount; }

    @Override
    public String toString() {
        return tagName;
    }
}
