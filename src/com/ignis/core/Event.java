package com.ignis.core;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Classe genérica de Evento desacoplado para comunicação entre sistemas e componentes.
 * Seguro contra modificações concorrentes durante o disparo.
 * 
 * @param <T> Tipo de dado do contexto do evento.
 */
public class Event<T> {
    private final List<Consumer<T>> listeners = new CopyOnWriteArrayList<>();

    /**
     * Inscreve um ouvinte no evento.
     * 
     * @param listener Ouvinte a ser adicionado.
     */
    public void subscribe(Consumer<T> listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    /**
     * Desinscreve um ouvinte do evento.
     * 
     * @param listener Ouvinte a ser removido.
     */
    public void unsubscribe(Consumer<T> listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    /**
     * Dispara o evento, notificando todos os ouvintes registrados.
     * 
     * @param context Contexto ou dado da notificação.
     */
    public void invoke(T context) {
        for (Consumer<T> listener : listeners) {
            listener.accept(context);
        }
    }
}
