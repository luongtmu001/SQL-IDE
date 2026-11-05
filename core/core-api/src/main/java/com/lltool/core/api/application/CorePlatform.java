package com.lltool.core.api.application;

import com.lltool.core.api.connection.ConnectionApi;
import com.lltool.core.api.connection.DefaultConnectionApi;
import com.lltool.core.api.event.EventApi;
import com.lltool.core.api.project.DefaultProjectApi;
import com.lltool.core.api.project.ProjectApi;
import com.lltool.core.api.workspace.DefaultWorkspaceApi;
import com.lltool.core.api.workspace.WorkspaceApi;
import com.lltool.core.application.connection.port.CredentialVaultPort;
import com.lltool.core.application.connection.port.DatabaseConnectionTesterPort;
import com.lltool.core.application.connection.service.ConnectionApplicationService;
import com.lltool.core.application.port.InMemoryConnectionRepository;
import com.lltool.core.application.port.InMemoryProjectRepository;
import com.lltool.core.application.port.InMemoryWorkspaceRepository;
import com.lltool.core.application.project.service.ProjectApplicationService;
import com.lltool.core.application.workspace.service.WorkspaceApplicationService;
import com.lltool.core.common.event.EventBus;
import com.lltool.core.common.time.TimeProvider;
import com.lltool.core.domain.connection.repository.ConnectionRepository;
import com.lltool.core.domain.project.repository.ProjectRepository;
import com.lltool.core.domain.workspace.repository.WorkspaceRepository;

import java.util.Objects;

/**
 * Composition Root and Entry Point for Core Platform.
 * Bootstraps all layers using Constructor Injection without external DI frameworks.
 */
public class CorePlatform {

    private final ProjectApi projectApi;
    private final WorkspaceApi workspaceApi;
    private final ConnectionApi connectionApi;
    private final EventApi eventApi;
    private final EventBus eventBus;
    private final ProjectRepository projectRepository;
    private final WorkspaceRepository workspaceRepository;
    private final ConnectionRepository connectionRepository;
    private final TimeProvider timeProvider;

    public CorePlatform(
            ProjectApi projectApi,
            WorkspaceApi workspaceApi,
            ConnectionApi connectionApi,
            EventApi eventApi,
            EventBus eventBus,
            ProjectRepository projectRepository,
            WorkspaceRepository workspaceRepository,
            ConnectionRepository connectionRepository,
            TimeProvider timeProvider) {
        this.projectApi = Objects.requireNonNull(projectApi);
        this.workspaceApi = Objects.requireNonNull(workspaceApi);
        this.connectionApi = Objects.requireNonNull(connectionApi);
        this.eventApi = Objects.requireNonNull(eventApi);
        this.eventBus = Objects.requireNonNull(eventBus);
        this.projectRepository = Objects.requireNonNull(projectRepository);
        this.workspaceRepository = Objects.requireNonNull(workspaceRepository);
        this.connectionRepository = Objects.requireNonNull(connectionRepository);
        this.timeProvider = Objects.requireNonNull(timeProvider);
    }

    /**
     * Factory method creating a fully functional in-memory core instance.
     */
    public static CorePlatform createDefault() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public ProjectApi projectApi() {
        return projectApi;
    }

    public WorkspaceApi workspaceApi() {
        return workspaceApi;
    }

    public ConnectionApi connectionApi() {
        return connectionApi;
    }

    public EventApi eventApi() {
        return eventApi;
    }

    public EventBus eventBus() {
        return eventBus;
    }

    public ProjectRepository projectRepository() {
        return projectRepository;
    }

    public WorkspaceRepository workspaceRepository() {
        return workspaceRepository;
    }

    public ConnectionRepository connectionRepository() {
        return connectionRepository;
    }

    public TimeProvider timeProvider() {
        return timeProvider;
    }

    public static class Builder {
        private ProjectRepository projectRepository;
        private WorkspaceRepository workspaceRepository;
        private ConnectionRepository connectionRepository;
        private CredentialVaultPort credentialVaultPort;
        private DatabaseConnectionTesterPort connectionTesterPort;
        private EventBus eventBus;
        private TimeProvider timeProvider;

        public Builder projectRepository(ProjectRepository repository) {
            this.projectRepository = repository;
            return this;
        }

        public Builder workspaceRepository(WorkspaceRepository repository) {
            this.workspaceRepository = repository;
            return this;
        }

        public Builder connectionRepository(ConnectionRepository repository) {
            this.connectionRepository = repository;
            return this;
        }

        public Builder credentialVaultPort(CredentialVaultPort vaultPort) {
            this.credentialVaultPort = vaultPort;
            return this;
        }

        public Builder connectionTesterPort(DatabaseConnectionTesterPort testerPort) {
            this.connectionTesterPort = testerPort;
            return this;
        }

        public Builder eventBus(EventBus eventBus) {
            this.eventBus = eventBus;
            return this;
        }

        public Builder timeProvider(TimeProvider timeProvider) {
            this.timeProvider = timeProvider;
            return this;
        }

        public CorePlatform build() {
            if (projectRepository == null) {
                projectRepository = new InMemoryProjectRepository();
            }
            if (workspaceRepository == null) {
                workspaceRepository = new InMemoryWorkspaceRepository();
            }
            if (connectionRepository == null) {
                connectionRepository = new InMemoryConnectionRepository();
            }
            if (eventBus == null) {
                eventBus = new EventBus();
            }
            if (timeProvider == null) {
                timeProvider = TimeProvider.system();
            }

            ProjectApplicationService projectService = new ProjectApplicationService(
                    projectRepository, eventBus, timeProvider);

            DefaultProjectApi projectApi = new DefaultProjectApi(
                    projectService, projectService, projectService, projectService, projectService);

            WorkspaceApplicationService workspaceService = new WorkspaceApplicationService(
                    workspaceRepository, eventBus, timeProvider);

            DefaultWorkspaceApi workspaceApi = new DefaultWorkspaceApi(
                    workspaceService, workspaceService, workspaceService,
                    workspaceService, workspaceService, workspaceService, workspaceService);

            ConnectionApplicationService connectionService = new ConnectionApplicationService(
                    connectionRepository, credentialVaultPort, connectionTesterPort, eventBus, timeProvider);

            DefaultConnectionApi connectionApi = new DefaultConnectionApi(
                    connectionService, connectionService, connectionService,
                    connectionService, connectionService, connectionService, connectionService);

            EventApi eventApi = new EventApi(eventBus, eventBus);

            return new CorePlatform(
                    projectApi,
                    workspaceApi,
                    connectionApi,
                    eventApi,
                    eventBus,
                    projectRepository,
                    workspaceRepository,
                    connectionRepository,
                    timeProvider
            );
        }
    }
}
