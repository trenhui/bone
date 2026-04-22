package com.bone.engine.extension.core.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 扩展点监控指标收集器
 * 集成 Prometheus 和 Grafana，提供统一的监控界面
 */
@Slf4j
public class ExtensionMetricsCollector {

    private final MeterRegistry meterRegistry;

    // 扩展调用次数计数器
    private final Counter extensionInvocationCounter;
    // 扩展调用成功计数器
    private final Counter extensionSuccessCounter;
    // 扩展调用失败计数器
    private final Counter extensionFailureCounter;
    // 扩展调用耗时计时器
    private final Timer extensionExecutionTimer;
    // 扩展点数量指标
    private final AtomicLong extensionPointCount = new AtomicLong(0);
    // 扩展实现数量指标
    private final AtomicLong extensionImplCount = new AtomicLong(0);
    // 慢调用计数器
    private final Counter slowInvocationCounter;

    public ExtensionMetricsCollector(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        // 初始化指标
        this.extensionInvocationCounter = Counter.builder("bone.extension.invocations.total")
                .description("Total number of extension invocations")
                .register(meterRegistry);

        this.extensionSuccessCounter = Counter.builder("bone.extension.invocations.success")
                .description("Number of successful extension invocations")
                .register(meterRegistry);

        this.extensionFailureCounter = Counter.builder("bone.extension.invocations.failure")
                .description("Number of failed extension invocations")
                .register(meterRegistry);

        this.extensionExecutionTimer = Timer.builder("bone.extension.execution.time")
                .description("Extension execution time")
                .register(meterRegistry);

        this.slowInvocationCounter = Counter.builder("bone.extension.invocations.slow")
                .description("Number of slow extension invocations")
                .register(meterRegistry);

        // 注册扩展点数量指标
        Gauge.builder("bone.extension.points.count", extensionPointCount, AtomicLong::get)
                .description("Number of extension points")
                .register(meterRegistry);

        // 注册扩展实现数量指标
        Gauge.builder("bone.extension.implementations.count", extensionImplCount, AtomicLong::get)
                .description("Number of extension implementations")
                .register(meterRegistry);

        log.info("ExtensionMetricsCollector initialized with Prometheus integration");
    }

    /**
     * 记录扩展调用开始
     * @return 计时器样本
     */
    public Timer.Sample startInvocation() {
        extensionInvocationCounter.increment();
        return Timer.start(meterRegistry);
    }

    /**
     * 记录扩展调用成功
     * @param sample 计时器样本
     * @param extensionPoint 扩展点名称
     * @param extensionImpl 扩展实现名称
     * @param isSlow 是否为慢调用
     */
    public void recordSuccess(Timer.Sample sample, String extensionPoint, String extensionImpl, boolean isSlow) {
        extensionSuccessCounter.increment();
        sample.stop(extensionExecutionTimer);
        if (isSlow) {
            slowInvocationCounter.increment();
        }
    }

    /**
     * 记录扩展调用失败
     * @param sample 计时器样本
     * @param extensionPoint 扩展点名称
     * @param extensionImpl 扩展实现名称
     */
    public void recordFailure(Timer.Sample sample, String extensionPoint, String extensionImpl) {
        extensionFailureCounter.increment();
        if (sample != null) {
            sample.stop(extensionExecutionTimer);
        }
    }

    /**
     * 更新扩展点数量
     * @param count 扩展点数量
     */
    public void updateExtensionPointCount(long count) {
        extensionPointCount.set(count);
    }

    /**
     * 更新扩展实现数量
     * @param count 扩展实现数量
     */
    public void updateExtensionImplCount(long count) {
        extensionImplCount.set(count);
    }


}
