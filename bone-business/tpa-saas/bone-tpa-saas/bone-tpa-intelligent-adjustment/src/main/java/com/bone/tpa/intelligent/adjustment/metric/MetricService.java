package com.bone.tpa.intelligent.adjustment.metric;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

/**
 * 监控指标服务，用于记录关键业务指标
 */
@Service
public class MetricService {
    private static final String QUOTA_FREEZE_SUCCESS = "quota.freeze.success";
    private static final String QUOTA_EXCEEDED = "quota.exceeded";
    private static final String ADJUDICATION_DURATION = "adjudication.duration"; // 新增：理算时长
    private static final String DEFAULT_DESCRIPTION = "Track quota-related operations";

    private final Counter quotaFreezeSuccessCounter;
    private final Counter quotaExceededCounter;
    private final Timer adjudicationDurationTimer; // 新增：理算时长计时器

    public MetricService(MeterRegistry meterRegistry) {
        // 初始化额度冻结成功计数器
        quotaFreezeSuccessCounter = Counter.builder(QUOTA_FREEZE_SUCCESS)
                .description(DEFAULT_DESCRIPTION)
                .tags("operation", "freeze", "status", "success")
                .register(meterRegistry);

        // 初始化额度超限计数器
        quotaExceededCounter = Counter.builder(QUOTA_EXCEEDED)
                .description(DEFAULT_DESCRIPTION)
                .tags("operation", "freeze", "status", "exceeded")
                .register(meterRegistry);

        // 初始化理算时长计时器
        adjudicationDurationTimer = Timer.builder(ADJUDICATION_DURATION)
                .description("Time taken for adjudication process")
                .tags("operation", "adjudication")
                .register(meterRegistry);
    }

    /**
     * 记录额度冻结成功事件
     * @param tags 可选标签（例如：应用名称、产品线等）
     */
    public void recordQuotaFreezeSuccess(String... tags) {
        quotaFreezeSuccessCounter.increment();
        // 如果需动态标签，可扩展为：
        // Counter.builder(QUOTA_FREEZE_SUCCESS).tags(tags).register(meterRegistry).increment();
    }

    /**
     * 记录额度超限事件
     * @param tags 可选标签（例如：错误类型、产品线等）
     */
    public void recordQuotaExceeded(String... tags) {
        quotaExceededCounter.increment();
        // 动态标签示例：
        // Counter.builder(QUOTA_EXCEEDED).tags(tags).register(meterRegistry).increment();
    }

    /**
     * 记录理算处理时长
     * @param durationMillis 理算耗时（毫秒）
     */
    public void recordAdjudicationDuration(long durationMillis) {
        adjudicationDurationTimer.record(durationMillis, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    /**
     * 记录理算处理时长（自动开始和结束计时）
     * @return Timer.Sample，用于在代码执行后结束计时
     */
    public Timer.Sample startAdjudicationTimer() {
        return Timer.start();  // 开始计时
    }
}
