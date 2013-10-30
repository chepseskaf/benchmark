package org.goinfre.java.core;

import org.goinfre.java.api.Implementation;
import org.goinfre.java.api.Listener;
import org.goinfre.java.api.ListenerManager;

/**
 * Created with IntelliJ IDEA.
 * User: chepseskaf
 */
abstract class AbstractImplementation implements Implementation<Listener> {
    private final String name;

    protected AbstractImplementation(String name) {
        this.name = name;
    }

    @Override
    final public ListenerManager<Listener> init() {
        return create();
    }

    protected abstract ListenerManager<Listener> create();

    @Override
    final public String name() {
        return name;
    }
}
