package com.bone.claim.infrastructure.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 启用 Spring 调度（ADR-0030 平台运维旁路）。
 *
 * <p>本模块 5 个 {@code @Scheduled} Job（{@code CloseExpiredClaimJob} / {@code InspectionSamplingJob} /
 * {@code OutboxRelayJob} / {@code ReconciliationJob} / {@code RefreshLimitHourJob}）此前因 {@code
 * ClaimApplication} 未声明调度开关而全部惰性、不会执行——尽管它们已完整实现、ADR-0030 C3 合规且各带测试。
 *
 * <p><b>渐进开启</b>：默认关闭（{@code matchIfMissing = false}），避免误开跨全租户的自动关单 / 资金对账 / 发件箱中继影响生产；
 * 上线时在部署配置中显式置 {@code bone.claim.schedule.enabled = true} 后再激活调度。
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(
    name = "bone.claim.schedule.enabled",
    havingValue = "true",
    matchIfMissing = false)
public class ScheduleConfig {}
