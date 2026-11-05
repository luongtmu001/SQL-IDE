package com.lltool.infrastructure.database.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry holding all available Database Providers.
 */
public class DatabaseProviderRegistry {

    private static final Logger log = LoggerFactory.getLogger(DatabaseProviderRegistry.class);

    private final Map<DatabaseType, DatabaseProvider> providers = new ConcurrentHashMap<>();

    public void register(DatabaseProvider provider) {
        if (provider != null) {
            providers.put(provider.getDatabaseType(), provider);
            log.info("Registered DatabaseProvider for {}", provider.getDatabaseType().getDisplayName());
        }
    }

    public Optional<DatabaseProvider> getProvider(DatabaseType type) {
        return Optional.ofNullable(providers.get(type));
    }

    public boolean supports(DatabaseType type) {
        return providers.containsKey(type);
    }
}

