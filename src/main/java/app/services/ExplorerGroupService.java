package app.services;

import app.framework.bootstrap.DesktopBootstrap;
import com.lltool.core.api.request.UpdateConnectionRequest;
import com.lltool.core.application.connection.dto.ConnectionProfileDto;
import com.lltool.core.common.result.Result;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.prefs.Preferences;

/**
 * Connection groups of the Object Explorer (SCR-MAIN-002, Gate B 2026-11-05, Q2 = a).
 *
 * <p>A group is the {@code group} field of a connection profile, nested with '/'
 * ("BRAVO-SERVER/POSTGRE"). Moving, renaming and deleting groups rewrite that field through the
 * existing {@code ConnectionApi.updateConnection} (local connection config, never a database).
 * Groups that contain no connection yet are kept in {@link Preferences} so they survive restarts.</p>
 */
public final class ExplorerGroupService {

    public static final String SEPARATOR = "/";
    private static final String PREF_KEY = "empty_groups";
    private static final ExplorerGroupService INSTANCE = new ExplorerGroupService();

    private final Preferences prefs = Preferences.userNodeForPackage(ExplorerGroupService.class).node("explorer");

    private ExplorerGroupService() {
    }

    public static ExplorerGroupService getInstance() {
        return INSTANCE;
    }

    // ------------------------------------------------------------------ paths

    public static String normalize(String path) {
        if (path == null) return "";
        List<String> parts = new ArrayList<>();
        for (String p : path.split(SEPARATOR)) {
            String t = p.trim();
            if (!t.isEmpty()) parts.add(t);
        }
        return String.join(SEPARATOR, parts);
    }

    public static String parentOf(String path) {
        String n = normalize(path);
        int i = n.lastIndexOf(SEPARATOR);
        return i < 0 ? "" : n.substring(0, i);
    }

    public static String nameOf(String path) {
        String n = normalize(path);
        int i = n.lastIndexOf(SEPARATOR);
        return i < 0 ? n : n.substring(i + 1);
    }

    public static String join(String parent, String name) {
        String p = normalize(parent);
        String n = normalize(name);
        return p.isEmpty() ? n : (n.isEmpty() ? p : p + SEPARATOR + n);
    }

    /** True if {@code path} equals {@code ancestor} or lies below it (case-insensitive, like LLTool). */
    public static boolean isSameOrBelow(String path, String ancestor) {
        String p = normalize(path).toLowerCase(Locale.ROOT);
        String a = normalize(ancestor).toLowerCase(Locale.ROOT);
        return !a.isEmpty() && (p.equals(a) || p.startsWith(a + SEPARATOR));
    }

    /** Validates a single group name; returns an error message or {@code null}. */
    public static String validateName(String name) {
        if (name == null || name.trim().isEmpty()) return "Tên nhóm không được để trống.";
        if (name.contains(SEPARATOR)) return "Tên nhóm không được chứa ký tự '/'.";
        if (name.trim().length() > 80) return "Tên nhóm tối đa 80 ký tự.";
        return null;
    }

    // ------------------------------------------------------------------ queries

    public Set<String> getEmptyGroups() {
        Set<String> set = new LinkedHashSet<>();
        String raw = prefs.get(PREF_KEY, "");
        for (String line : raw.split("\n")) {
            String n = normalize(line);
            if (!n.isEmpty()) set.add(n);
        }
        return set;
    }

    private void saveEmptyGroups(Collection<String> groups) {
        try {
            prefs.put(PREF_KEY, String.join("\n", new TreeSet<>(groups)));
        } catch (Exception ignored) {
            // preference store unavailable
        }
    }

    /** All group paths (including every ancestor) from profiles + empty groups. */
    public Set<String> allGroups(List<ConnectionProfileDto> profiles) {
        Set<String> result = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        List<String> sources = new ArrayList<>(getEmptyGroups());
        if (profiles != null) {
            for (ConnectionProfileDto p : profiles) sources.add(normalize(p.group()));
        }
        for (String g : sources) {
            String cur = "";
            for (String part : normalize(g).split(SEPARATOR)) {
                if (part.isEmpty()) continue;
                cur = join(cur, part);
                result.add(cur);
            }
        }
        return result;
    }

    public boolean exists(String path, List<ConnectionProfileDto> profiles) {
        String n = normalize(path);
        return allGroups(profiles).stream().anyMatch(g -> g.equalsIgnoreCase(n));
    }

    // ------------------------------------------------------------------ commands

    public String createGroup(String parentPath, String name, List<ConnectionProfileDto> profiles) {
        String error = validateName(name);
        if (error != null) return error;
        String path = join(parentPath, name);
        if (exists(path, profiles)) return "Nhóm \"" + path + "\" đã tồn tại.";
        Set<String> groups = getEmptyGroups();
        groups.add(path);
        saveEmptyGroups(groups);
        return null;
    }

    public String renameGroup(String oldPath, String newName, List<ConnectionProfileDto> profiles) {
        String error = validateName(newName);
        if (error != null) return error;
        String from = normalize(oldPath);
        String to = join(parentOf(from), newName);
        if (from.equalsIgnoreCase(to)) return null;
        if (exists(to, profiles)) return "Nhóm \"" + to + "\" đã tồn tại.";
        return relocate(from, to, profiles);
    }

    /** Moves group {@code path} (with everything inside) under {@code newParent} ("" = root). */
    public String moveGroup(String path, String newParent, List<ConnectionProfileDto> profiles) {
        String from = normalize(path);
        String parent = normalize(newParent);
        if (isSameOrBelow(parent, from)) return "Không thể chuyển nhóm vào chính nó hoặc nhóm con của nó.";
        String to = join(parent, nameOf(from));
        if (from.equalsIgnoreCase(to)) return null;
        if (exists(to, profiles)) return "Nhóm \"" + to + "\" đã tồn tại ở vị trí đích.";
        return relocate(from, to, profiles);
    }

    /** Deletes a group; connections inside move to "no group" (LLTool behaviour). */
    public String deleteGroup(String path, List<ConnectionProfileDto> profiles) {
        String target = normalize(path);
        StringBuilder errors = new StringBuilder();
        for (ConnectionProfileDto p : profiles) {
            if (isSameOrBelow(p.group(), target)) {
                String err = updateGroup(p, "");
                if (err != null) errors.append(err).append('\n');
            }
        }
        Set<String> groups = getEmptyGroups();
        groups.removeIf(g -> isSameOrBelow(g, target));
        saveEmptyGroups(groups);
        return errors.length() == 0 ? null : errors.toString().trim();
    }

    public String moveConnection(ConnectionProfileDto profile, String groupPath) {
        if (profile == null) return null;
        String to = normalize(groupPath);
        if (normalize(profile.group()).equalsIgnoreCase(to)) return null;
        String err = updateGroup(profile, to);
        if (err == null) {
            Set<String> groups = getEmptyGroups();
            if (groups.removeIf(g -> g.equalsIgnoreCase(to))) saveEmptyGroups(groups);
        }
        return err;
    }

    public int countConnections(String path, List<ConnectionProfileDto> profiles) {
        int n = 0;
        for (ConnectionProfileDto p : profiles) if (isSameOrBelow(p.group(), path)) n++;
        return n;
    }

    private String relocate(String from, String to, List<ConnectionProfileDto> profiles) {
        StringBuilder errors = new StringBuilder();
        for (ConnectionProfileDto p : profiles) {
            String g = normalize(p.group());
            if (isSameOrBelow(g, from)) {
                String rest = g.length() > from.length() ? g.substring(from.length()) : "";
                String err = updateGroup(p, to + rest);
                if (err != null) errors.append(err).append('\n');
            }
        }
        Set<String> groups = getEmptyGroups();
        Set<String> moved = new LinkedHashSet<>();
        for (String g : groups) {
            if (isSameOrBelow(g, from)) {
                moved.add(to + g.substring(from.length()));
            } else {
                moved.add(g);
            }
        }
        // keep an (otherwise empty) renamed group visible
        moved.add(to);
        saveEmptyGroups(moved);
        return errors.length() == 0 ? null : errors.toString().trim();
    }

    /** Rewrites only the group of a profile; password is kept (null + savePassword unchanged). */
    private String updateGroup(ConnectionProfileDto p, String group) {
        try {
            UpdateConnectionRequest req = new UpdateConnectionRequest(
                    p.id(), p.name(), group, p.environment(), p.readOnly(), p.databaseType(),
                    p.host(), p.port(), p.databaseName(), p.sqliteFilePath(), p.username(), p.authType(),
                    null, p.savePassword(), p.timeoutSeconds(), p.trustServerCertificate(),
                    p.properties() != null ? p.properties() : java.util.Map.of());
            Result<ConnectionProfileDto> res = DesktopBootstrap.getInstance().getConnectionApi().updateConnection(req);
            if (res.isSuccess()) return null;
            return res.getError().map(e -> e.message()).orElse("Không cập nhật được kết nối " + p.name());
        } catch (Exception e) {
            return "Không cập nhật được kết nối " + p.name() + ": " + e.getMessage();
        }
    }
}
