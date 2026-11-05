package com.lltool.infrastructure.storage.local;

import com.lltool.core.domain.project.model.Project;
import com.lltool.core.domain.project.valueobject.ProjectId;
import com.lltool.core.domain.project.valueobject.ProjectName;
import com.lltool.core.domain.project.valueobject.ProjectPath;
import com.lltool.infrastructure.filesystem.AppDirectoryManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class LocalProjectRepositoryTest {

    @Test
    @DisplayName("LocalProjectRepository persists projects to JSON files and retrieves them")
    void testSaveAndFindProject(@TempDir Path tempDir) {
        AppDirectoryManager dirManager = AppDirectoryManager.createIsolated(tempDir);
        LocalProjectRepository repository = new LocalProjectRepository(dirManager);

        ProjectId id = ProjectId.generate();
        Instant now = Instant.now();
        Project p = Project.create(id, ProjectName.of("Fintech Platform"), ProjectPath.of("/var/fintech"), now);

        repository.save(p);

        assertTrue(repository.existsById(id));
        assertTrue(repository.existsByName(ProjectName.of("Fintech Platform")));

        Optional<Project> found = repository.findById(id);
        assertTrue(found.isPresent());
        assertEquals("Fintech Platform", found.get().getName().value());

        List<Project> all = repository.findAll();
        assertEquals(1, all.size());

        repository.delete(id);
        assertFalse(repository.existsById(id));
    }
}

