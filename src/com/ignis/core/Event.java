package com.ignis.core;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Generic decoupled event publisher-subscriber class for communication between engine components.
 * Thread-safe and immune to concurrent modifications (such as unsubscribe calls during invoke dispatching).
 * 
 * @param <T> The event context type.
 */
public class Event<T> {
    private final List<Consumer<T>> listeners = new CopyOnWriteArrayList<>();

    /**
     * Subscribes a listener callback to this event.
     * 
     * @param listener The listener callback to register.
     */
    public void subscribe(Consumer<T> listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    /**
     * Unsubscribes a listener callback from this event.
     * 
     * @param listener The listener callback to unregister.
     */
    public void unsubscribe(Consumer<T> listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    /**
     * Invokes the event, notifying all registered listener callbacks with the given context.
     * 
     * @param context The event context data payload.
     */
    public void invoke(T context) {
        for (Consumer<T> listener : listeners) {
            listener.accept(context);
        }
    }
}
