package com.bone.ailedger.domain.gateway;

import com.bone.ailedger.domain.model.LedgerAuditRecord;

/**
 * 额度变异审计端口（碰钱内核必须留痕：合规 / 对账 / 纠纷溯源）。
 *
 * <p>每一次余额变动（含被拒绝的操作）都由台账emit一条 {@link LedgerAuditRecord}。 默认实现 {@code
 * LoggingLedgerAuditAdapter}（记日志）；接入方以 {@code @Bean} 覆盖为落库 / 数仓 / 消息队列， 与 pk-tpa-saas
 * 「理算结果落库」的合规口径对齐。
 *
 * <p>与 {@link com.bone.ainotification.domain.gateway.NotificationAckSink} 等审计端口同一模式： 平台定义留痕契约与默认实现，
 * 持久化目标归接入方。
 */
public interface LedgerAuditPort {

  /** 记录一笔额度变异。 */
  void record(LedgerAuditRecord record);
}
