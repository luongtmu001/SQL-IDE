package com.lltool.core.domain.project.model;

import com.lltool.core.domain.project.valueobject.ProjectId;
import com.lltool.core.domain.project.valueobject.ProjectName;
import com.lltool.core.domain.project.valueobject.ProjectPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class ProjectTest {

    @Test
    @DisplayName("Project creation enforces business invariants")
    void testProjectCreation() {
        Instant now = Instant.now();
        ProjectId id = ProjectId.generate();
        ProjectName name = ProjectName.of("My Database Analytics");
        ProjectPath path = ProjectPath.of("D:/workspace/analytics");

        Project p = Project.create(id, name, path, now);

        assertEquals(id, p.getId());
        assertEquals("My Database Analytics", p.getName().value());
        assertEquals("D:/workspace/analytics", p.getPath().value());
        assertTrue(p.isActive());
        assertEquals(now, p.getCreatedAt());
        assertEquals(now, p.getUpdatedAt());
    }

    @Test
    @DisplayName("Invalid project name throws IllegalArgumentException")
    void testInvalidProjectName() {
        assertThrows(IllegalArgumentException.class, () -> ProjectName.of(""));
        assertThrows(IllegalArgumentException.class, () -> ProjectName.of("   "));
        assertThrows(IllegalArgumentException.class, () -> ProjectName.of("Bad/Name"));
        assertThrows(IllegalArgumentException.class, () -> ProjectName.of("Bad:Name"));
    }

    @Test
    @DisplayName("Renaming project updates name and updatedAt timestamp")
    void testProjectRename() {
        Instant t1 = Instant.parse("2026-01-01T00:00:00Z");
        Instant t2 = Instant.parse("2026-01-02T12:00:00Z");

        Project p = Project.create(ProjectId.generate(), ProjectName.of("Old Name"), ProjectPath.of("C:/proj"), t1);
        p.rename(ProjectName.of("New Name"), t2);

        assertEquals("New Name", p.getName().value());
        assertEquals(t2, p.getUpdatedAt());
    }
}

