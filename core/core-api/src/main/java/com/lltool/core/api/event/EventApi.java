package com.lltool.core.api.event;

import com.lltool.core.common.event.AppEvent;
import com.lltool.core.common.event.EventHandler;
import com.lltool.core.common.event.EventPublisher;
import com.lltool.core.common.event.EventSubscriber;

import java.util.Objects;

/**
 * Public Core Contract for subscribing to and publishing platform events.
 */
public class EventApi {

    private final EventPublisher publisher;
    private final EventSubscriber subscriber;

    public EventApi(EventPublisher publisher, EventSubscriber subscriber) {
        this.publisher = Objects.requireNonNull(publisher);
        this.subscriber = Objects.requireNonNull(subscriber);
    }

    public void publish(AppEvent event) {
        publisher.publish(event);
    }

    public <E extends AppEvent> AutoCloseable subscribe(Class<E> eventType, EventHandler<E> handler) {
        return subscriber.subscribe(eventType, handler);
    }

    public <E extends AppEvent> void unsubscribe(Class<E> eventType, EventHandler<E> handler) {
        subscriber.unsubscribe(eventType, handler);
    }
}

