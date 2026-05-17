package com.bone.engine.extension.studio.service;

/** 幂等键相同但请求体不一致（Bone-API-规范 §8）。 */
public class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException(String message) {
        super(message);
    }
}
