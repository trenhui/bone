package com.bone.ainotification.domain.service;

import com.bone.ainotification.domain.gateway.AlertSuppressionGuard;
import com.bone.ainotification.domain.model.AlertEvent;
import java.time.Instant;

/** 默认抑制闸门（不抑制）：未启用风暴抑制时的安全默认，全部告警放行。 */
public final class NoopAlertSuppressionGuard implements AlertSuppressionGuard {

  public static final NoopAlertSuppressionGuard INSTANCE = new NoopAlertSuppressionGuard();

  private NoopAlertSuppressionGuard() {}

  @Override
  public boolean shouldSuppress(AlertEvent event, Instant now) {
    return false;
  }
}
