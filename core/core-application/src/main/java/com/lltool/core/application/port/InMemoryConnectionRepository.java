package com.lltool.core.application.port;

import com.lltool.core.domain.connection.model.ConnectionProfile;
import com.lltool.core.domain.connection.repository.ConnectionRepository;
import com.lltool.core.domain.connection.valueobject.ConnectionId;
import com.lltool.core.domain.connection.valueobject.ConnectionName;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryConnectionRepository implements ConnectionRepository {

    private final Map<ConnectionId, ConnectionProfile> storage = new ConcurrentHashMap<>();

    @Override
    public Optional<ConnectionProfile> findById(ConnectionId id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(storage.get(id));
    }

    @Override
    public Optional<ConnectionProfile> findByName(ConnectionName name) {
        if (name == null) return Optional.empty();
        return storage.values().stream()
                .filter(c -> c.getName().equals(name))
                .findFirst();
    }

    @Override
    public List<ConnectionProfile> findAll() {
        return new ArrayList<>(storage.values());
    }

    @Override
    public void save(ConnectionProfile profile) {
        Objects.requireNonNull(profile, "ConnectionProfile cannot be null");
        storage.put(profile.getId(), profile);
    }

    @Override
    public void delete(ConnectionId id) {
        if (id != null) {
            storage.remove(id);
        }
    }

    @Override
    public boolean existsById(ConnectionId id) {
        return id != null && storage.containsKey(id);
    }

    @Override
    public boolean existsByName(ConnectionName name) {
        return findByName(name).isPresent();
    }

    public void clear() {
        storage.clear();
    }
}

