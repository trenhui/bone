package com.bone.aiintegration.domain.gateway;

import com.bone.aiintegration.domain.model.ExternalCall;
import com.bone.aiintegration.domain.model.ExternalCallOutcome;

/** 外部系统出站端口；消费方可 {@code @Bean} 覆盖为接自有 HTTP / 消息网关。 */
public interface ExternalSystemPort {
  ExternalCallOutcome call(ExternalCall call);
}
