package com.lltool.infrastructure.storage.local;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.lltool.core.domain.project.model.Project;
import com.lltool.core.domain.project.repository.ProjectRepository;
import com.lltool.core.domain.project.valueobject.ProjectId;
import com.lltool.core.domain.project.valueobject.ProjectName;
import com.lltool.core.domain.project.valueobject.ProjectPath;
import com.lltool.infrastructure.filesystem.AppDirectoryManager;
import com.lltool.infrastructure.filesystem.AtomicFileWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;

/**
 * Local File System JSON Adapter for ProjectRepository (Phase 3.4 &amp; 3.8).
 * Stores each project as a JSON file in ~/.lltool/data/projects/{projectId}.json.
 */
public class LocalProjectRepository implements ProjectRepository {

    private static final Logger log = LoggerFactory.getLogger(LocalProjectRepository.class);

    private final Path projectsDir;
    private final ObjectMapper mapper;

    public record ProjectEntityRecord(
            String id,
            String name,
            String path,
            String createdAt,
            String updatedAt,
            boolean active
    ) {}

    public LocalProjectRepository(AppDirectoryManager directoryManager) {
        this.projectsDir = directoryManager.getDataDir().resolve("projects");
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .enable(SerializationFeature.INDENT_OUTPUT);
        try {
            Files.createDirectories(projectsDir);
        } catch (IOException e) {
            log.error("Failed to create projects directory: {}", projectsDir, e);
        }
    }

    @Override
    public Optional<Project> findById(ProjectId id) {
        if (id == null) return Optional.empty();
        Path file = getFile(id);
        if (!Files.exists(file)) return Optional.empty();

        try {
            ProjectEntityRecord record = mapper.readValue(file.toFile(), ProjectEntityRecord.class);
            return Optional.of(toDomain(record));
        } catch (IOException e) {
            log.error("Failed to read project file {}: {}", file, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public Optional<Project> findByName(ProjectName name) {
        if (name == null) return Optional.empty();
        return findAll().stream()
                .filter(p -> p.getName().equals(name))
                .findFirst();
    }

    @Override
    public List<Project> findAll() {
        if (!Files.exists(projectsDir)) return Collections.emptyList();

        List<Project> list = new ArrayList<>();
        try (Stream<Path> stream = Files.list(projectsDir)) {
            stream.filter(p -> p.toString().endsWith(".json")).forEach(p -> {
                try {
                    ProjectEntityRecord record = mapper.readValue(p.toFile(), ProjectEntityRecord.class);
                    list.add(toDomain(record));
                } catch (IOException e) {
                    log.warn("Corrupt project file {}: {}", p, e.getMessage());
                }
            });
        } catch (IOException e) {
            log.error("Failed to list projects from {}: {}", projectsDir, e.getMessage());
        }
        return list;
    }

    @Override
    public void save(Project project) {
        Objects.requireNonNull(project, "project cannot be null");
        Path file = getFile(project.getId());
        ProjectEntityRecord record = new ProjectEntityRecord(
                project.getId().value(),
                project.getName().value(),
                project.getPath().value(),
                project.getCreatedAt().toString(),
                project.getUpdatedAt().toString(),
                project.isActive()
        );
        try {
            String json = mapper.writeValueAsString(record);
            AtomicFileWriter.writeString(file, json);
            log.debug("Saved project {} to {}", project.getId().value(), file);
        } catch (IOException e) {
            log.error("Failed to write project {}: {}", file, e.getMessage(), e);
            throw new RuntimeException("Cannot persist project to local storage", e);
        }
    }

    @Override
    public void delete(ProjectId id) {
        if (id == null) return;
        Path file = getFile(id);
        try {
            Files.deleteIfExists(file);
            log.debug("Deleted project file {}", file);
        } catch (IOException e) {
            log.error("Failed to delete project file {}: {}", file, e.getMessage());
        }
    }

    @Override
    public boolean existsById(ProjectId id) {
        return id != null && Files.exists(getFile(id));
    }

    @Override
    public boolean existsByName(ProjectName name) {
        return findByName(name).isPresent();
    }

    private Path getFile(ProjectId id) {
        return projectsDir.resolve(id.value() + ".json");
    }

    private Project toDomain(ProjectEntityRecord r) {
        return new Project(
                ProjectId.of(r.id()),
                ProjectName.of(r.name()),
                ProjectPath.of(r.path()),
                Instant.parse(r.createdAt()),
                Instant.parse(r.updatedAt()),
                r.active()
        );
    }
}

