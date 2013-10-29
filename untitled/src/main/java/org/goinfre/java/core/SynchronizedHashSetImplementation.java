package org.goinfre.java.core;

import org.goinfre.java.api.Listener;
import org.goinfre.java.api.ListenerManager;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Created with IntelliJ IDEA.
 * User: chepseskaf
 */
public class SynchronizedHashSetImplementation extends AbstractImplementation {

    private final Set<Listener> set = Collections.synchronizedSet(new HashSet<Listener>());

    public SynchronizedHashSetImplementation() {
        super("Collections.synchronizedSet");
    }

    @Override
    protected ListenerManager<Listener> create() {
        return new ListenerManager<Listener>() {
            @Override
            public boolean register(Listener listener) {
                return set.add(listener);
            }

            @Override
            public boolean unregister(Listener listener) {
                return set.remove(listener);
            }

            @Override
            public void fireNotification() {
                for (Listener listener : set) {
                    listener.fire();
                }
            }
        };
    }
}
