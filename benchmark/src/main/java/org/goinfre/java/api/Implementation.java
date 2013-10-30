package org.goinfre.java.api;

/**
 * Created with IntelliJ IDEA.
 * User: chepseskaf
 */
public interface Implementation<T> {
    ListenerManager<T> init();

    String name();
}
