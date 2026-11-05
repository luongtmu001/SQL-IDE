package com.lltool.core.common.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory synchronous thread-safe EventBus.
 * Dispatches events to registered subscribers while isolating handler exceptions
 * so a single failing subscriber cannot halt other subscribers or the publisher.
 */
public class EventBus implements EventPublisher, EventSubscriber {

    private static final Logger log = LoggerFactory.getLogger(EventBus.class);

    private final Map<Class<?>, List<EventHandler<?>>> subscriptions = new ConcurrentHashMap<>();

    @Override
    public <E extends AppEvent> AutoCloseable subscribe(Class<E> eventType, EventHandler<E> handler) {
        Objects.requireNonNull(eventType, "eventType cannot be null");
        Objects.requireNonNull(handler, "handler cannot be null");

        subscriptions.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>()).add(handler);
        log.debug("Subscribed handler to event {}", eventType.getSimpleName());

        return () -> unsubscribe(eventType, handler);
    }

    @Override
    public <E extends AppEvent> void unsubscribe(Class<E> eventType, EventHandler<E> handler) {
        if (eventType == null || handler == null) return;

        List<EventHandler<?>> handlers = subscriptions.get(eventType);
        if (handlers != null) {
            handlers.remove(handler);
            if (handlers.isEmpty()) {
                subscriptions.remove(eventType);
            }
            log.debug("Unsubscribed handler from event {}", eventType.getSimpleName());
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void publish(AppEvent event) {
        if (event == null) return;

        log.debug("Publishing event: {} (id={})", event.eventType(), event.eventId());

        // Dispatch to exact class and super interfaces/classes matching AppEvent
        subscriptions.forEach((eventType, handlers) -> {
            if (eventType.isInstance(event)) {
                for (EventHandler<?> rawHandler : handlers) {
                    try {
                        ((EventHandler<AppEvent>) rawHandler).handle(event);
                    } catch (Throwable t) {
                        // Strict error isolation: subscriber errors do not bubble up to publisher
                        log.error("Unhandled error in event subscriber for {}: {}",
                                event.eventType(), t.getMessage(), t);
                    }
                }
            }
        });
    }

    public void clear() {
        subscriptions.clear();
    }

    public int subscriberCount(Class<?> eventType) {
        List<EventHandler<?>> list = subscriptions.get(eventType);
        return list != null ? list.size() : 0;
    }
}

