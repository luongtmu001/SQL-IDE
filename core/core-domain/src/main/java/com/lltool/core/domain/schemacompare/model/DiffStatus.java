package com.lltool.core.domain.schemacompare.model;

/**
 * Trạng thái khác biệt giữa Source và Target.
 */
public enum DiffStatus {
    IDENTICAL("Đồng nhất", "identical", "fth-check-circle"),
    DIFFERENT("Khác biệt cấu trúc", "different", "fth-alert-triangle"),
    MISSING_IN_TARGET("Chỉ có ở Source (Cần tạo mới)", "missing_in_target", "fth-plus-circle"),
    EXTRA_IN_TARGET("Chỉ có ở Target (Cần xóa)", "extra_in_target", "fth-minus-circle");

    private final String displayName;
    private final String code;
    private final String iconLiteral;

    DiffStatus(String displayName, String code, String iconLiteral) {
        this.displayName = displayName;
        this.code = code;
        this.iconLiteral = iconLiteral;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getCode() {
        return code;
    }

    public String getIconLiteral() {
        return iconLiteral;
    }
}

