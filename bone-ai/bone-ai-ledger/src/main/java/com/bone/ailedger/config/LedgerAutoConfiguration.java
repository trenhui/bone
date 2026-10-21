package com.bone.ailedger.config;

import com.bone.ailedger.domain.gateway.LedgerAuditPort;
import com.bone.ailedger.domain.gateway.LimitLedger;
import com.bone.ailedger.infrastructure.ledger.InMemoryLimitLedger;
import com.bone.ailedger.infrastructure.ledger.LoggingLedgerAuditAdapter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * bone-ai-ledger 自动配置（Boot 3 {@code .imports} 机制）。
 *
 * <p>默认提供进程内 {@link InMemoryLimitLedger} + 日志型 {@link LedgerAuditPort}（碰钱内核默认必须留痕）； 多副本需全局一致时以
 * {@code @Bean LimitLedger} 覆盖为共享存储实现（Redis 等，L3），端口不变、调用方零改动。
 */
@AutoConfiguration
public class LedgerAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public LimitLedger limitLedger(LedgerAuditPort auditPort) {
    return new InMemoryLimitLedger(auditPort);
  }

  /** 额度变异审计默认实现（结构化日志；接入方以 {@code @Bean} 覆盖为落库 / 数仓 / MQ）。 */
  @Bean
  @ConditionalOnMissingBean
  public LedgerAuditPort ledgerAuditPort() {
    return new LoggingLedgerAuditAdapter();
  }
}
