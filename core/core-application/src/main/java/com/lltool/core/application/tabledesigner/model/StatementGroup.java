package com.lltool.core.application.tabledesigner.model;

/**
 * Logical categorization of migration statements for review and execution order.
 */
public enum StatementGroup {
    TABLE_RENAME("Đổi tên bảng"),
    CONSTRAINT_DROP("Xóa ràng buộc"),
    INDEX_DROP("Xóa chỉ mục"),
    COLUMN_RENAME("Đổi tên cột"),
    COLUMN_DROP("Xóa cột"),
    COLUMN_ADD("Thêm cột"),
    COLUMN_ALTER("Sửa cột"),
    CONSTRAINT_ADD("Thêm ràng buộc"),
    INDEX_ADD("Thêm chỉ mục"),
    COMMENT("Mô tả / Extended Property"),
    TRIGGER("Trigger");

    private final String displayName;

    StatementGroup(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}

