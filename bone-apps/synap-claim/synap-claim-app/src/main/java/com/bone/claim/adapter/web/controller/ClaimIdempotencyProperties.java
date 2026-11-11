package com.bone.claim.adapter.web.controller;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 理赔模块幂等配置（{@code bone.claim.idempotency.*}）。
 *
 * <p><b>P0-3（2026-11-11 落地）</b>：快照 TTL 从写死的 24h 改为配置化，默认 <b>30 天</b>—— 24h
 * 覆盖不了理赔争议窗口（以天/月计）：客户「已成功但响应丢失」隔天重试时， 快照已过期，资金写端点（pay/refund）会二次出金、报案会二次建案。30 天与诊断报告 §14.1 P0-3
 * 的验收判据 「TTL 按争议窗口配置化且默认 ≥30 天」对齐。
 *
 * <p>{@code enabled} 开关由 {@code IdempotencyStoreAdapter} 的 {@code @ConditionalOnProperty} 消费： 置
 * {@code false} 时快照存储 Bean 不装配，{@code IdempotencyService} 因缺构造依赖而装配失败——<b>应用拒绝启动</b>（fail-closed）。
 * 资金写路径不允许「无幂等也能跑」的降级形态，本开关只用于紧急回滚到上一镜像，不是常态运行形态。
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "bone.claim.idempotency")
public class ClaimIdempotencyProperties {

  /** 快照保留时长；默认 30 天（≥ 理赔争议窗口）。 */
  private Duration ttl = Duration.ofDays(30);
}
