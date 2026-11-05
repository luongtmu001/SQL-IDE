package com.lltool.core.api.application;

import com.lltool.core.api.request.CreateProjectRequest;
import com.lltool.core.application.project.dto.ProjectDto;
import com.lltool.core.common.result.Result;
import com.lltool.core.domain.project.event.ProjectCreatedDomainEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-End Acceptance Test defined in Section 13 of Phase 02 Architecture Plan:
 * Desktop/Client -> Core API -> CreateProjectUseCase -> Project Domain -> InMemoryRepository -> EventBus -> Subscriber
 */
class CorePlatformIntegrationTest {

    @Test
    @DisplayName("Section 13 Acceptance Flow: Create Project, publish Event, receive in Subscriber, query list")
    void testEndToEndPhase02Acceptance() {
        // Bootstrap Core Platform
        CorePlatform platform = CorePlatform.createDefault();

        // Step 4: Register an Event Subscriber
        List<ProjectCreatedDomainEvent> receivedEvents = new ArrayList<>();
        platform.eventApi().subscribe(ProjectCreatedDomainEvent.class, receivedEvents::add);

        // Step 1: Create Project through Core API
        CreateProjectRequest request = new CreateProjectRequest("CRM Analytics", "D:/projects/crm");
        Result<ProjectDto> createResult = platform.projectApi().createProject(request);

        // Step 2: Validate ProjectDto result
        assertTrue(createResult.isSuccess(), "Project creation must succeed");
        ProjectDto createdDto = createResult.getDataOrThrow();
        assertEquals("CRM Analytics", createdDto.name());
        assertEquals("D:/projects/crm", createdDto.path());
        assertNotNull(createdDto.id());
        assertTrue(createdDto.active());

        // Step 3 & 4: Verify Subscriber received ProjectCreatedDomainEvent
        assertEquals(1, receivedEvents.size(), "Subscriber must have received exactly one event");
        ProjectCreatedDomainEvent event = receivedEvents.getFirst();
        assertEquals(createdDto.id(), event.projectId().value());
        assertEquals("CRM Analytics", event.projectName().value());

        // Step 5: Query Project List through Core API
        Result<List<ProjectDto>> listResult = platform.projectApi().getProjects();

        // Step 6: Validate returned data
        assertTrue(listResult.isSuccess());
        List<ProjectDto> projectList = listResult.getDataOrThrow();
        assertEquals(1, projectList.size());
        assertEquals(createdDto.id(), projectList.getFirst().id());
        assertEquals("CRM Analytics", projectList.getFirst().name());
    }
}

