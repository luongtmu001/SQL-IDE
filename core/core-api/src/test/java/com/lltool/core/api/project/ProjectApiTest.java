package com.lltool.core.api.project;

import com.lltool.core.api.application.CorePlatform;
import com.lltool.core.api.request.CreateProjectRequest;
import com.lltool.core.api.request.RenameProjectRequest;
import com.lltool.core.application.project.dto.ProjectDto;
import com.lltool.core.common.error.AppError;
import com.lltool.core.common.result.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProjectApiTest {

    private CorePlatform platform;
    private ProjectApi api;

    @BeforeEach
    void setUp() {
        platform = CorePlatform.createDefault();
        api = platform.projectApi();
    }

    @Test
    @DisplayName("ProjectApi creates, queries, renames, and deletes projects via unified Result")
    void testProjectApiCrud() {
        // 1. Create
        Result<ProjectDto> createRes = api.createProject(new CreateProjectRequest("Data Warehouse", "/var/data/dw"));
        assertTrue(createRes.isSuccess());
        ProjectDto created = createRes.getDataOrThrow();
        assertEquals("Data Warehouse", created.name());
        assertNotNull(created.id());

        // 2. Query Detail
        Result<ProjectDto> detailRes = api.getProject(created.id());
        assertTrue(detailRes.isSuccess());
        assertEquals("Data Warehouse", detailRes.getDataOrThrow().name());

        // 3. Rename
        Result<ProjectDto> renameRes = api.renameProject(new RenameProjectRequest(created.id(), "Lakehouse"));
        assertTrue(renameRes.isSuccess());
        assertEquals("Lakehouse", renameRes.getDataOrThrow().name());

        // 4. Query List
        Result<List<ProjectDto>> listRes = api.getProjects();
        assertTrue(listRes.isSuccess());
        assertEquals(1, listRes.getDataOrThrow().size());

        // 5. Delete
        Result<Void> delRes = api.deleteProject(created.id());
        assertTrue(delRes.isSuccess());

        // 6. Verify Not Found
        Result<ProjectDto> deletedDetail = api.getProject(created.id());
        assertTrue(deletedDetail.isFailure());
        assertEquals(AppError.PROJECT_NOT_FOUND, deletedDetail.getError().get().code());
    }
}

