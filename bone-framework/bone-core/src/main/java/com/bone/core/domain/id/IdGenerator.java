package com.bone.core.domain.id;

public interface IdGenerator {
    Object generateId(GenerationStrategy strategy, Object entity);
}