package com.lltool.core.application.project.service;

import com.lltool.core.application.port.InMemoryProjectRepository;
import com.lltool.core.application.project.command.CreateProjectCommand;
import com.lltool.core.application.project.command.DeleteProjectCommand;
import com.lltool.core.application.project.command.RenameProjectCommand;
import com.lltool.core.application.project.dto.ProjectDto;
import com.lltool.core.application.project.query.GetProjectDetailQuery;
import com.lltool.core.application.project.query.GetProjectListQuery;
import com.lltool.core.common.error.AppError;
import com.lltool.core.common.event.AppEvent;
import com.lltool.core.common.event.EventBus;
import com.lltool.core.common.result.Result;
import com.lltool.core.common.time.TestTimeProvider;
import com.lltool.core.domain.project.event.ProjectCreatedDomainEvent;
import com.lltool.core.domain.project.event.ProjectRenamedDomainEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProjectApplicationServiceTest {

    private InMemoryProjectRepository repository;
    private EventBus eventBus;
    private TestTimeProvider timeProvider;
    private ProjectApplicationService service;
    private List<AppEvent> publishedEvents;

    @BeforeEach
    void setUp() {
        repository = new InMemoryProjectRepository();
        eventBus = new EventBus();
        timeProvider = new TestTimeProvider(Instant.parse("2026-03-01T10:00:00Z"));
        service = new ProjectApplicationService(repository, eventBus, timeProvider);

        publishedEvents = new ArrayList<>();
        eventBus.subscribe(AppEvent.class, publishedEvents::add);
    }

    @Test
    @DisplayName("Create project successfully publishes event and returns DTO")
    void testCreateProjectSuccess() {
        CreateProjectCommand cmd = new CreateProjectCommand("E-Commerce DB", "D:/databases/ecommerce");
        Result<ProjectDto> result = service.execute(cmd);

        assertTrue(result.isSuccess());
        ProjectDto dto = result.getDataOrThrow();
        assertEquals("E-Commerce DB", dto.name());
        assertEquals("D:/databases/ecommerce", dto.path());
        assertTrue(dto.active());

        // Verify event was published
        assertEquals(1, publishedEvents.size());
        assertInstanceOf(ProjectCreatedDomainEvent.class, publishedEvents.getFirst());
        ProjectCreatedDomainEvent event = (ProjectCreatedDomainEvent) publishedEvents.getFirst();
        assertEquals("E-Commerce DB", event.projectName().value());
    }

    @Test
    @DisplayName("Create project rejects duplicate name")
    void testCreateProjectDuplicateName() {
        service.execute(new CreateProjectCommand("Analytics", "C:/db1"));

        Result<ProjectDto> dup = service.execute(new CreateProjectCommand("Analytics", "C:/db2"));
        assertTrue(dup.isFailure());
        assertEquals(AppError.PROJECT_ALREADY_EXISTS, dup.getError().get().code());
    }

    @Test
    @DisplayName("Create project rejects invalid name")
    void testCreateProjectInvalidName() {
        Result<ProjectDto> res = service.execute(new CreateProjectCommand("Bad/Name", "C:/db"));
        assertTrue(res.isFailure());
        assertEquals(AppError.INVALID_PROJECT_NAME, res.getError().get().code());
    }

    @Test
    @DisplayName("Rename project updates name and publishes rename event")
    void testRenameProject() {
        ProjectDto created = service.execute(new CreateProjectCommand("Original", "C:/db")).getDataOrThrow();

        publishedEvents.clear();
        timeProvider.set(Instant.parse("2026-03-02T12:00:00Z"));

        Result<ProjectDto> renamedRes = service.execute(new RenameProjectCommand(created.id(), "Updated Name"));
        assertTrue(renamedRes.isSuccess());
        assertEquals("Updated Name", renamedRes.getDataOrThrow().name());

        assertEquals(1, publishedEvents.size());
        assertInstanceOf(ProjectRenamedDomainEvent.class, publishedEvents.getFirst());
    }

    @Test
    @DisplayName("Delete project removes it from repository and list")
    void testDeleteProject() {
        ProjectDto p1 = service.execute(new CreateProjectCommand("Proj 1", "C:/1")).getDataOrThrow();
        ProjectDto p2 = service.execute(new CreateProjectCommand("Proj 2", "C:/2")).getDataOrThrow();

        assertEquals(2, service.execute(GetProjectListQuery.all()).getDataOrThrow().size());

        Result<Void> delRes = service.execute(new DeleteProjectCommand(p1.id()));
        assertTrue(delRes.isSuccess());

        List<ProjectDto> remaining = service.execute(GetProjectListQuery.all()).getDataOrThrow();
        assertEquals(1, remaining.size());
        assertEquals("Proj 2", remaining.getFirst().name());

        Result<ProjectDto> detailRes = service.execute(new GetProjectDetailQuery(p1.id()));
        assertTrue(detailRes.isFailure());
        assertEquals(AppError.PROJECT_NOT_FOUND, detailRes.getError().get().code());
    }
}

