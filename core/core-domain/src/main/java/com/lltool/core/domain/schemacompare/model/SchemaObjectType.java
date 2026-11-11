package com.lltool.core.domain.schemacompare.model;

/**
 * Loại đối tượng cơ sở dữ liệu được so sánh.
 */
public enum SchemaObjectType {
    TABLE("Bảng dữ liệu", "TABLE", "fth-grid"),
    VIEW("Khung nhìn (View)", "VIEW", "fth-eye"),
    PROCEDURE("Thủ tục lưu trữ (Stored Procedure)", "PROCEDURE", "fth-cpu"),
    FUNCTION("Hàm người dùng (Function)", "FUNCTION", "fth-code"),
    TRIGGER("Trigger", "TRIGGER", "fth-zap");

    private final String displayName;
    private final String code;
    private final String iconLiteral;

    SchemaObjectType(String displayName, String code, String iconLiteral) {
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

