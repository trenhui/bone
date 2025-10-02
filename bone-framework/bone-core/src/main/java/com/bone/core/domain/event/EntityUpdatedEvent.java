package com.bone.core.domain.event;

public class EntityUpdatedEvent<T> {
    private final T entity;

    public EntityUpdatedEvent(T entity) {
        this.entity = entity;
    }

    public T getEntity() {
        return entity;
    }
}