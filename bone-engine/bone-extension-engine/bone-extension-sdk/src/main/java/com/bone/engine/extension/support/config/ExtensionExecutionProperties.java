package com.bone.engine.extension.support.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 扩展点执行隔离（舱壁 + 超时），对齐详设 §4.4 [Target]。 */
@ConfigurationProperties(prefix = "bone.extension.execution")
public class ExtensionExecutionProperties {

    private boolean guardEnabled = true;

    /** 单次扩展调用超时（毫秒） */
    private long timeoutMs = 30_000L;

    /** 全局并发上限（舱壁） */
    private int bulkheadMaxConcurrent = 64;

    public boolean isGuardEnabled() {
        return guardEnabled;
    }

    public void setGuardEnabled(boolean guardEnabled) {
        this.guardEnabled = guardEnabled;
    }

    public long getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public int getBulkheadMaxConcurrent() {
        return bulkheadMaxConcurrent;
    }

    public void setBulkheadMaxConcurrent(int bulkheadMaxConcurrent) {
        this.bulkheadMaxConcurrent = bulkheadMaxConcurrent;
    }
}
