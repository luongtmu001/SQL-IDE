package app.ui.components;

import app.models.DatabaseNode;
import app.models.DatabaseNodeType;
import app.services.ExplorerGroupService;
import com.lltool.core.application.connection.dto.ConnectionProfileDto;

import java.util.*;

/**
 * Builds the grouped connection tree (GROUP folders nested by '/', connections inside).
 * Shared by the Object Explorer and the Connection Manager list so both look identical.
 */
public final class ConnectionTreeBuilder {

    private ConnectionTreeBuilder() {}

    public static void buildGroupedTree(DatabaseNode root, List<ConnectionProfileDto> list) {
        Map<String, DatabaseNode> groups = new HashMap<>();
        Set<String> paths = ExplorerGroupService.getInstance().allGroups(list);
        // parents first (sorted set puts "A" before "A/B")
        for (String path : paths) {
            DatabaseNode g = new DatabaseNode(ExplorerGroupService.nameOf(path), DatabaseNodeType.GROUP);
            g.setGroupPath(path);
            g.setLoaded(true);
            groups.put(path.toLowerCase(Locale.ROOT), g);
            String parent = ExplorerGroupService.parentOf(path);
            DatabaseNode parentNode = parent.isEmpty() ? root : groups.get(parent.toLowerCase(Locale.ROOT));
            (parentNode != null ? parentNode : root).addChild(g);
        }
        List<ConnectionProfileDto> sorted = new ArrayList<>(list);
        sorted.sort(Comparator.comparing(p -> p.name() != null ? p.name().toLowerCase(Locale.ROOT) : ""));
        for (ConnectionProfileDto profile : sorted) {
            String g = ExplorerGroupService.normalize(profile.group());
            DatabaseNode connNode = createConnectionNode(profile);
            connNode.setGroupPath(g);
            DatabaseNode parentNode = g.isEmpty() ? root : groups.get(g.toLowerCase(Locale.ROOT));
            (parentNode != null ? parentNode : root).addChild(connNode);
        }
    }

    public static DatabaseNode createConnectionNode(ConnectionProfileDto profile) {
        String hostInfo = profile.host() != null && !profile.host().isBlank()
                ? profile.host() + ":" + profile.port()
                : (profile.sqliteFilePath() != null && !profile.sqliteFilePath().isBlank() ? profile.sqliteFilePath() : "localhost");
        String env = profile.environment() != null ? profile.environment() : "DEV";

        DatabaseNode connNode = new DatabaseNode(profile.name(), DatabaseNodeType.CONNECTION, hostInfo + " • " + env, "fth-server");
        connNode.setConnectionId(profile.id());
        connNode.setConnectionProfile(profile);
        return connNode;
    }
}
