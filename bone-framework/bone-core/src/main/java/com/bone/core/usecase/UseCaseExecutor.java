package com.bone.core.usecase;

public interface UseCaseExecutor<C, R> {
    R execute(C command);
}
