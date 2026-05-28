package com.bone.engine.extension.studio.observability;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/** Studio 控制面 RED 指标（详设 §11.1）。 */
@Component
public class StudioExtensionMetrics {

    private final MeterRegistry meterRegistry;

    public StudioExtensionMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordDeploy(String action, String status) {
        meterRegistry
                .counter("extension_deploy_total", "action", action, "status", status)
                .increment();
    }

    public void recordLro(String operation, String result) {
        meterRegistry
                .counter("extension_lro_operation_total", "operation", operation, "result", result)
                .increment();
    }
}
