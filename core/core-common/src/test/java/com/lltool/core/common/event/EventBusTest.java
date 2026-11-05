package com.lltool.core.common.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class EventBusTest {

    record TestEvent(String payload, Instant occurredAt) implements AppEvent {}
    record AnotherEvent(int number, Instant occurredAt) implements AppEvent {}

    @Test
    @DisplayName("Subscribers receive dispatched events matching their type")
    void testPublishSubscribe() {
        EventBus bus = new EventBus();
        List<String> received = new ArrayList<>();

        bus.subscribe(TestEvent.class, e -> received.add(e.payload()));

        bus.publish(new TestEvent("msg-1", Instant.now()));
        bus.publish(new AnotherEvent(100, Instant.now()));
        bus.publish(new TestEvent("msg-2", Instant.now()));

        assertEquals(List.of("msg-1", "msg-2"), received);
    }

    @Test
    @DisplayName("Error isolation: subscriber throwing an exception does not halt others")
    void testErrorIsolation() {
        EventBus bus = new EventBus();
        AtomicInteger successCounter = new AtomicInteger(0);

        // First subscriber throws exception
        bus.subscribe(TestEvent.class, e -> {
            throw new RuntimeException("Simulated failure in subscriber 1");
        });

        // Second subscriber must still run cleanly
        bus.subscribe(TestEvent.class, e -> successCounter.incrementAndGet());

        assertDoesNotThrow(() -> bus.publish(new TestEvent("payload", Instant.now())));
        assertEquals(1, successCounter.get());
    }

    @Test
    @DisplayName("AutoCloseable subscription handle removes subscriber")
    void testUnsubscribe() throws Exception {
        EventBus bus = new EventBus();
        AtomicInteger count = new AtomicInteger(0);

        AutoCloseable handle = bus.subscribe(TestEvent.class, e -> count.incrementAndGet());
        bus.publish(new TestEvent("A", Instant.now()));
        assertEquals(1, count.get());

        handle.close();
        bus.publish(new TestEvent("B", Instant.now()));
        assertEquals(1, count.get(), "Count should not increase after unsubscription");
    }
}

