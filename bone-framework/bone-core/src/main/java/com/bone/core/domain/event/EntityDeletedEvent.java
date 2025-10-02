package com.bone.core.domain.event;

public class EntityDeletedEvent<T> {
    private final T entity;

    public EntityDeletedEvent(T entity) {
        this.entity = entity;
    }

    public T getEntity() {
        return entity;
    }
}