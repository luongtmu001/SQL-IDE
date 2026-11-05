package com.lltool.core.api.project;

import com.lltool.core.api.request.CreateProjectRequest;
import com.lltool.core.api.request.RenameProjectRequest;
import com.lltool.core.application.project.dto.ProjectDto;
import com.lltool.core.common.result.Result;

import java.util.List;

/**
 * Public Core Contract for Project operations.
 * Clean boundaries for Desktop UI, Web platforms, and future extensions.
 */
public interface ProjectApi {

    Result<ProjectDto> createProject(CreateProjectRequest request);

    Result<ProjectDto> getProject(String projectId);

    Result<List<ProjectDto>> getProjects();

    Result<ProjectDto> renameProject(RenameProjectRequest request);

    Result<Void> deleteProject(String projectId);
}

