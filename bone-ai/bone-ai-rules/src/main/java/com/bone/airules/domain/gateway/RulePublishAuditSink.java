package com.bone.airules.domain.gateway;

import com.bone.airules.domain.model.RulePublishAuditEvent;

/** 规则发布审计出站端口：实现方接 WORM 存储；仓内默认实现只做结构化日志。 */
public interface RulePublishAuditSink {

  void record(RulePublishAuditEvent event);
}
