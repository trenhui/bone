package com.bone.system.domain.model.console;

import java.time.Instant;
import lombok.Builder;
import lombok.Value;

/** 控制台读侧值对象：当前节点资源使用（JVM 子集；CPU/磁盘为 [Target] 占位）。 */
@Value
@Builder
public class ResourceUsage {

  long memoryUsedBytes;
  long memoryMaxBytes;
  double cpuPercent;
  double diskUsedPercent;
  Instant updatedAt;
}
