package com.bone.airules.infrastructure.audit;

import com.bone.airules.domain.gateway.RulePublishAuditSink;
import com.bone.airules.domain.model.RulePublishAuditEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 结构化日志审计 sink（仓内默认）：发布判定无论放行/拒绝都落一行审计日志。
 *
 * <p>长期 WORM 落盘由接入方注入另一个 {@link RulePublishAuditSink} 实现；本类不宣称满足 WORM， 只是让"判定有痕"在默认装配下也成立。默认不被装配。
 */
public class LoggingRulePublishAuditSink implements RulePublishAuditSink {

  private static final Logger log = LoggerFactory.getLogger(LoggingRulePublishAuditSink.class);

  @Override
  public void record(RulePublishAuditEvent event) {
    log.info(
        "RULE_PUBLISH_AUDIT code={} version={} operator={} admitted={} decision={} at={}",
        event.ruleCode(),
        event.versionNo(),
        event.operator(),
        event.admitted(),
        event.decisionCode(),
        event.occurredAt());
  }
}
