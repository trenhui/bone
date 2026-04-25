package com.bone.studio.generator.application.usecase;

public interface UseCaseExecutor<C, R> {
    R execute(C command);
}