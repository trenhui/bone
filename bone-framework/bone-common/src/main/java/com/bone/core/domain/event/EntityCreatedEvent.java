package com.bone.core.domain.event;

import lombok.Getter;

@Getter
public class EntityCreatedEvent<T> {
    private final T entity;

    public EntityCreatedEvent(T entity) {
        this.entity = entity;
    }
}