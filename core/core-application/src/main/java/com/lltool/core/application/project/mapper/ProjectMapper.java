package com.lltool.core.application.project.mapper;

import com.lltool.core.application.project.dto.ProjectDto;
import com.lltool.core.domain.project.model.Project;

import java.time.format.DateTimeFormatter;

public final class ProjectMapper {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_INSTANT;

    private ProjectMapper() {}

    public static ProjectDto toDto(Project project) {
        if (project == null) return null;
        return new ProjectDto(
                project.getId().value(),
                project.getName().value(),
                project.getPath().value(),
                FORMATTER.format(project.getCreatedAt()),
                FORMATTER.format(project.getUpdatedAt()),
                project.isActive()
        );
    }
}

