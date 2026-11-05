package com.lltool.core.domain.connection.repository;

import com.lltool.core.domain.connection.model.ConnectionProfile;
import com.lltool.core.domain.connection.valueobject.ConnectionId;
import com.lltool.core.domain.connection.valueobject.ConnectionName;

import java.util.List;
import java.util.Optional;

public interface ConnectionRepository {

    Optional<ConnectionProfile> findById(ConnectionId id);

    Optional<ConnectionProfile> findByName(ConnectionName name);

    List<ConnectionProfile> findAll();

    void save(ConnectionProfile profile);

    void delete(ConnectionId id);

    boolean existsById(ConnectionId id);

    boolean existsByName(ConnectionName name);
}

