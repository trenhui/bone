package com.bone.engine.extension;

/**
 * 上下文复制器，用于在不同线程间复制上下文
 */
public interface ContextCopier {
    /**
     * 复制上下文并应用到当前线程
     */
    void apply();
}