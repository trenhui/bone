package com.bone.engine.extension.studio.service;

/** If-Match / version 与当前实体不一致。 */
public class OptimisticLockException extends RuntimeException {

    public OptimisticLockException(String message) {
        super(message);
    }
}
