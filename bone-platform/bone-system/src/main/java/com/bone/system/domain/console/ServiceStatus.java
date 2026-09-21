package com.bone.system.domain.console;

import lombok.Builder;
import lombok.Value;

/**
 * 控制台读侧值对象：单个微服务的健康状态摘要。
 *
 * <p>状态码遵循 Actuator {@code Health.Status} 词汇（{@code UP/DOWN/OUT_OF_SERVICE/UNKNOWN}）。
 */
@Value
@Builder
public class ServiceStatus {

  String name;
  String serviceCode;
  String port;
  String status;
  long latencyMs;
}
