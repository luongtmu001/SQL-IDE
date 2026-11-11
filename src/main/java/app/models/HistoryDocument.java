package app.models;

import java.io.Serializable;

/**
 * Model representing a document (query tab or file) in Query History.
 */
public class HistoryDocument implements Serializable {
    private String docId;
    private String displayName;
    private String filePath;
    private boolean isStarred;
    private long createdAt;
    private long lastRunAt;
    private String lastServer;
    private String lastDatabase;
    private boolean isDeleted;
    private int entryCount;

    public HistoryDocument() {
    }

    public HistoryDocument(String docId, String displayName, String filePath, boolean isStarred,
                           long createdAt, long lastRunAt, String lastServer, String lastDatabase,
                           boolean isDeleted, int entryCount) {
        this.docId = docId;
        this.displayName = displayName;
        this.filePath = filePath;
        this.isStarred = isStarred;
        this.createdAt = createdAt;
        this.lastRunAt = lastRunAt;
        this.lastServer = lastServer;
        this.lastDatabase = lastDatabase;
        this.isDeleted = isDeleted;
        this.entryCount = entryCount;
    }

    public String getDocId() { return docId; }
    public void setDocId(String docId) { this.docId = docId; }

    public String getDisplayName() { return displayName != null ? displayName : "Untitled.sql"; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public boolean isStarred() { return isStarred; }
    public void setStarred(boolean starred) { isStarred = starred; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getLastRunAt() { return lastRunAt; }
    public void setLastRunAt(long lastRunAt) { this.lastRunAt = lastRunAt; }

    public String getLastServer() { return lastServer; }
    public void setLastServer(String lastServer) { this.lastServer = lastServer; }

    public String getLastDatabase() { return lastDatabase; }
    public void setLastDatabase(String lastDatabase) { this.lastDatabase = lastDatabase; }

    public boolean isDeleted() { return isDeleted; }
    public void setDeleted(boolean deleted) { isDeleted = deleted; }

    public int getEntryCount() { return entryCount; }
    public void setEntryCount(int entryCount) { this.entryCount = entryCount; }

    @Override
    public String toString() {
        return getDisplayName() + " (" + entryCount + ")";
    }
}
