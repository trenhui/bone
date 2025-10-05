package com.bone.core.event;

public interface EntityEventHandler<T> {
    void handleEvent(T entity);
}
