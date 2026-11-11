package com.lltool.core.domain.schemacompare.model;

/**
 * Hành động di chuyển để đưa Target về trạng thái của Source.
 */
public enum DiffAction {
    NONE("Không làm gì"),
    CREATE("Tạo mới trên Target"),
    DROP("Xóa khỏi Target (Phá hủy)"),
    ALTER("Điều chỉnh cấu trúc trên Target");

    private final String description;

    DiffAction(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}

