package com.bone.masterdata.infrastructure.observability;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * 主数据模块健康检查占位实现。
 *
 * <p>已知限制（技术债，待后续扩展）：
 *
 * <ul>
 *   <li>当前仅返回静态 UP，未真正校验：主数据实体数、质量检查任务积压、导入导出批处理队列深度、元数据 SDK 目录服务可达性；
 *   <li>未探测扩展字段（EAV）表规模与索引膨胀；
 *   <li>后续建议拆分为 {@code masterdata-entity / masterdata-quality / masterdata-import} 三个子指标 聚合到统一分组。
 * </ul>
 */
@Component
public class MasterDataModuleHealthIndicator implements HealthIndicator {

  @Override
  public Health health() {
    return Health.up()
        .withDetail("module", "bone-masterdata")
        .withDetail("status", "STUB")
        .withDetail(
            "limitation",
            "MasterDataModuleHealthIndicator is a placeholder; real entity/quality/import checks pending")
        .build();
  }
}
