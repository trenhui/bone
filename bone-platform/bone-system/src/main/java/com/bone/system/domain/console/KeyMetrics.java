package com.bone.system.domain.console;

import java.time.Instant;
import lombok.Builder;
import lombok.Value;

/** 控制台读侧值对象：关键业务指标（按 DDL 表存在性最佳努力 COUNT）。 */
@Value
@Builder
public class KeyMetrics {

  long userCount;
  long entityCount;
  long integrationFlowCount;
  long extensionPluginCount;
  long orderCount;
  long transactionAmount;
  long jvmThreadsLive;
  long jvmThreadsDaemon;
  Instant updatedAt;
}
