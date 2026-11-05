package com.lltool.infrastructure.storage.local;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.lltool.core.domain.project.valueobject.ProjectId;
import com.lltool.core.domain.workspace.model.Workspace;
import com.lltool.core.domain.workspace.repository.WorkspaceRepository;
import com.lltool.core.domain.workspace.valueobject.*;
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
 * Local File System JSON Adapter for WorkspaceRepository (Phase 6.1).
 * Stores each workspace as a JSON file in ~/.lltool/data/workspaces/{workspaceId}.json.
 */
public class LocalWorkspaceRepository implements WorkspaceRepository {

    private static final Logger log = LoggerFactory.getLogger(LocalWorkspaceRepository.class);

    private final Path workspacesDir;
    private final ObjectMapper mapper;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TabRecord(
            String tabId,
            String title,
            String filePath,
            String connectionId,
            boolean active
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PanelLayoutRecord(
            boolean sidebarVisible,
            double sidebarWidth,
            boolean resultsVisible,
            double resultsHeight
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WorkspaceStateRecord(
            String activeFilePath,
            List<TabRecord> openTabs,
            String selectedConnectionId,
            String themePreference,
            PanelLayoutRecord panelLayout,
            Map<String, String> customProperties
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WorkspaceEntityRecord(
            String id,
            String name,
            List<String> projectIds,
            String activeProjectId,
            WorkspaceStateRecord state,
            String createdAt,
            String updatedAt
    ) {}

    public LocalWorkspaceRepository(AppDirectoryManager directoryManager) {
        this.workspacesDir = directoryManager.getDataDir().resolve("workspaces");
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .enable(SerializationFeature.INDENT_OUTPUT);
        try {
            Files.createDirectories(workspacesDir);
        } catch (IOException e) {
            log.error("Failed to create workspaces directory: {}", workspacesDir, e);
        }
    }

    @Override
    public Optional<Workspace> findById(WorkspaceId id) {
        if (id == null) return Optional.empty();
        Path file = getFile(id);
        if (!Files.exists(file)) return Optional.empty();

        try {
            WorkspaceEntityRecord record = mapper.readValue(file.toFile(), WorkspaceEntityRecord.class);
            return Optional.of(toDomain(record));
        } catch (IOException e) {
            log.error("Failed to read workspace file {}: {}", file, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public Optional<Workspace> findByName(WorkspaceName name) {
        if (name == null) return Optional.empty();
        return findAll().stream()
                .filter(w -> w.getName().equals(name))
                .findFirst();
    }

    @Override
    public List<Workspace> findAll() {
        if (!Files.exists(workspacesDir)) return Collections.emptyList();

        List<Workspace> list = new ArrayList<>();
        try (Stream<Path> stream = Files.list(workspacesDir)) {
            stream.filter(p -> p.toString().endsWith(".json")).forEach(p -> {
                try {
                    WorkspaceEntityRecord record = mapper.readValue(p.toFile(), WorkspaceEntityRecord.class);
                    list.add(toDomain(record));
                } catch (IOException e) {
                    log.warn("Corrupt workspace file {}: {}", p, e.getMessage());
                }
            });
        } catch (IOException e) {
            log.error("Failed to list workspaces from {}: {}", workspacesDir, e.getMessage());
        }
        return list;
    }

    @Override
    public void save(Workspace workspace) {
        Objects.requireNonNull(workspace, "workspace cannot be null");
        Path file = getFile(workspace.getId());

        WorkspaceEntityRecord record = toRecord(workspace);
        try {
            String json = mapper.writeValueAsString(record);
            AtomicFileWriter.writeString(file, json);
            log.debug("Saved workspace {} to {}", workspace.getId().value(), file);
        } catch (IOException e) {
            log.error("Failed to write workspace {}: {}", file, e.getMessage(), e);
            throw new RuntimeException("Cannot persist workspace to local storage", e);
        }
    }

    @Override
    public void delete(WorkspaceId id) {
        if (id == null) return;
        Path file = getFile(id);
        try {
            Files.deleteIfExists(file);
            log.debug("Deleted workspace file {}", file);
        } catch (IOException e) {
            log.error("Failed to delete workspace file {}: {}", file, e.getMessage());
        }
    }

    @Override
    public boolean existsById(WorkspaceId id) {
        return id != null && Files.exists(getFile(id));
    }

    private Path getFile(WorkspaceId id) {
        return workspacesDir.resolve(id.value() + ".json");
    }

    private WorkspaceEntityRecord toRecord(Workspace w) {
        List<String> projectIds = w.getProjectIds().stream()
                .map(ProjectId::value)
                .toList();

        String activeProjectId = w.getActiveProjectId()
                .map(ProjectId::value)
                .orElse(null);

        WorkspaceState s = w.getState();
        List<TabRecord> tabs = s.openTabs().stream()
                .map(t -> new TabRecord(t.tabId(), t.title(), t.filePath(), t.connectionId(), t.active()))
                .toList();

        PanelLayoutRecord layout = new PanelLayoutRecord(
                s.panelLayout().sidebarVisible(),
                s.panelLayout().sidebarWidth(),
                s.panelLayout().resultsVisible(),
                s.panelLayout().resultsHeight()
        );

        WorkspaceStateRecord stateRecord = new WorkspaceStateRecord(
                s.activeFilePath(),
                tabs,
                s.selectedConnectionId(),
                s.themePreference(),
                layout,
                s.customProperties()
        );

        return new WorkspaceEntityRecord(
                w.getId().value(),
                w.getName().value(),
                projectIds,
                activeProjectId,
                stateRecord,
                w.getCreatedAt().toString(),
                w.getUpdatedAt().toString()
        );
    }

    private Workspace toDomain(WorkspaceEntityRecord r) {
        WorkspaceState state = WorkspaceState.empty();
        if (r.state() != null) {
            List<TabState> tabs = r.state().openTabs() != null ? r.state().openTabs().stream()
                    .map(t -> TabState.of(t.tabId(), t.title(), t.filePath(), t.connectionId(), t.active()))
                    .toList() : List.of();

            PanelLayoutState layout = r.state().panelLayout() != null
                    ? PanelLayoutState.of(
                            r.state().panelLayout().sidebarVisible(),
                            r.state().panelLayout().sidebarWidth(),
                            r.state().panelLayout().resultsVisible(),
                            r.state().panelLayout().resultsHeight()
                      )
                    : PanelLayoutState.defaultLayout();

            state = WorkspaceState.of(
                    r.state().activeFilePath(),
                    tabs,
                    r.state().selectedConnectionId(),
                    r.state().themePreference(),
                    layout,
                    r.state().customProperties()
            );
        }

        Workspace w = new Workspace(
                WorkspaceId.of(r.id()),
                WorkspaceName.of(r.name()),
                state,
                Instant.parse(r.createdAt()),
                Instant.parse(r.updatedAt())
        );

        if (r.projectIds() != null) {
            for (String pId : r.projectIds()) {
                w.addProject(ProjectId.of(pId), Instant.parse(r.updatedAt()));
            }
        }
        if (r.activeProjectId() != null && !r.activeProjectId().isBlank()) {
            w.setActiveProject(ProjectId.of(r.activeProjectId()), Instant.parse(r.updatedAt()));
        }

        return w;
    }
}

