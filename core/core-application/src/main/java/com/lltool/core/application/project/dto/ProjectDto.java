package com.lltool.core.application.project.dto;

import java.io.Serializable;

/**
 * Standard Data Transfer Object for Project.
 * Clean record independent of JavaFX and DB objects.
 */
public record ProjectDto(
        String id,
        String name,
        String path,
        String createdAt,
        String updatedAt,
        boolean active
) implements Serializable {}

