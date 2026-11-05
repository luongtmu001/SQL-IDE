package com.lltool.core.application.connection.query;

public record GetConnectionListQuery() {
    private static final GetConnectionListQuery INSTANCE = new GetConnectionListQuery();

    public static GetConnectionListQuery all() {
        return INSTANCE;
    }
}

