package org.goinfre.java.api;

/**
 * Created with IntelliJ IDEA.
 * User: chepseskaf
 */
public interface ListenerManager<T> {
    boolean register(T listener);

    boolean unregister(T listener);

    void fireNotification();
}
