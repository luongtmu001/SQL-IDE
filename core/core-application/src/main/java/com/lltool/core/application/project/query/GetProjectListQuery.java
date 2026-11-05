package com.lltool.core.application.project.query;

public record GetProjectListQuery(boolean activeOnly) {

    public static GetProjectListQuery all() {
        return new GetProjectListQuery(false);
    }

    public static GetProjectListQuery onlyActive() {
        return new GetProjectListQuery(true);
    }
}
