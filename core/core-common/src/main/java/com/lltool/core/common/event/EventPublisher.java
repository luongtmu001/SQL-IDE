package com.lltool.core.common.event;

/**
 * Port for dispatching events across modules and layers.
 */
public interface EventPublisher {

    void publish(AppEvent event);
}

