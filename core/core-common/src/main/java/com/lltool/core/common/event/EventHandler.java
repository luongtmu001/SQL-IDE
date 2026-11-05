package com.lltool.core.common.event;

/**
 * Functional listener for events of type E.
 */
@FunctionalInterface
public interface EventHandler<E extends AppEvent> {

    void handle(E event);
}

