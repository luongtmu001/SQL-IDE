package com.lltool.core.common.event;

/**
 * Port for registering/unregistering event consumers.
 */
public interface EventSubscriber {

    /**
     * Subscribes a handler to a specific event class.
     * @return an AutoCloseable subscription handle to easily unregister.
     */
    <E extends AppEvent> AutoCloseable subscribe(Class<E> eventType, EventHandler<E> handler);

    <E extends AppEvent> void unsubscribe(Class<E> eventType, EventHandler<E> handler);
}

