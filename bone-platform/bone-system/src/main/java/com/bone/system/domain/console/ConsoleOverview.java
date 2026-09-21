package com.bone.system.domain.console;

import java.time.Instant;
import java.util.List;
import lombok.Builder;
import lombok.Value;

/** 控制台读侧值对象：概览聚合（{@code GET /api/v1/console/overview} 的 data）。 */
@Value
@Builder
public class ConsoleOverview {

  List<ServiceStatus> services;
  ResourceUsage resourceUsage;
  KeyMetrics keyMetrics;

  /** 平台级告警占位；接入 {@code sys_alert_event} 后填充（[Target]）。 */
  List<Object> alerts;

  Instant updatedAt;
}
