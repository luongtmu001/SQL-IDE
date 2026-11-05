package com.lltool.core.application.workspace.query;

public record GetWorkspaceListQuery() {
    private static final GetWorkspaceListQuery INSTANCE = new GetWorkspaceListQuery();

    public static GetWorkspaceListQuery all() {
        return INSTANCE;
    }
}

