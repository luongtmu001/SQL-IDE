package app.models;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Model representing an execution or snapshot entry in Query History.
 */
public class HistoryEntry implements Serializable {
    public static final int TYPE_EXECUTION = 1;
    public static final int TYPE_SNAPSHOT = 2;

    public static final int STATUS_OK = 0;
    public static final int STATUS_ERROR = 1;
    public static final int STATUS_CANCELLED = 2;

    private long entryId;
    private String docId;
    private String contentHash;
    private int entryType = TYPE_EXECUTION;
    private long executedAt;
    private String server;
    private String databaseName;
    private String loginName;
    private boolean isSelection;
    private Long durationMs;
    private Integer status; // 0=OK, 1=Error, 2=Cancelled
    private String errorSummary;
    private int runCount = 1;
    private String note;
    private String tag;
    private boolean isFavorite;
    private List<String> tags = new ArrayList<>();
    private String sql;
    private long rawSize;

    public HistoryEntry() {
    }

    public long getEntryId() { return entryId; }
    public void setEntryId(long entryId) { this.entryId = entryId; }

    public String getDocId() { return docId; }
    public void setDocId(String docId) { this.docId = docId; }

    public String getContentHash() { return contentHash; }
    public void setContentHash(String contentHash) { this.contentHash = contentHash; }

    public int getEntryType() { return entryType; }
    public void setEntryType(int entryType) { this.entryType = entryType; }

    public long getExecutedAt() { return executedAt; }
    public void setExecutedAt(long executedAt) { this.executedAt = executedAt; }

    public String getServer() { return server != null ? server : ""; }
    public void setServer(String server) { this.server = server; }

    public String getDatabaseName() { return databaseName != null ? databaseName : ""; }
    public void setDatabaseName(String databaseName) { this.databaseName = databaseName; }

    public String getLoginName() { return loginName; }
    public void setLoginName(String loginName) { this.loginName = loginName; }

    public boolean isSelection() { return isSelection; }
    public void setSelection(boolean selection) { isSelection = selection; }

    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public String getErrorSummary() { return errorSummary; }
    public void setErrorSummary(String errorSummary) { this.errorSummary = errorSummary; }

    public int getRunCount() { return runCount; }
    public void setRunCount(int runCount) { this.runCount = runCount; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public String getTag() { return tag; }
    public void setTag(String tag) { this.tag = tag; }

    public boolean isFavorite() { return isFavorite; }
    public void setFavorite(boolean favorite) { isFavorite = favorite; }

    public List<String> getTags() { return tags != null ? tags : new ArrayList<>(); }
    public void setTags(List<String> tags) { this.tags = tags; }

    public String getSql() { return sql; }
    public void setSql(String sql) { this.sql = sql; }

    public long getRawSize() { return rawSize; }
    public void setRawSize(long rawSize) { this.rawSize = rawSize; }

    public boolean isSuccess() {
        return status != null && status == STATUS_OK;
    }

    public boolean isError() {
        return status != null && status == STATUS_ERROR;
    }

    public boolean isCancelled() {
        return status != null && status == STATUS_CANCELLED;
    }
}
