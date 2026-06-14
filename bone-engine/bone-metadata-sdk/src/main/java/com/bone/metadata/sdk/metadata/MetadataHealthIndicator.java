package com.bone.metadata.sdk.metadata;

import com.bone.metadata.sdk.domain.enums.DeploymentMode;
import com.bone.metadata.sdk.metadata.api.MetadataService;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

/** 元数据服务健康检查指示器 */
public class MetadataHealthIndicator implements HealthIndicator {

  private final DelegatingMetadataService delegatingService;

  public MetadataHealthIndicator(DelegatingMetadataService delegatingService) {
    this.delegatingService = delegatingService;
  }

  @Override
  public Health health() {
    // 获取当前激活的服务和模式
    MetadataService activeService = delegatingService.getActiveDelegate();
    DeploymentMode mode = delegatingService.getCurrentMode();
    String serviceName = activeService.getClass().getSimpleName();

    try {
      // 执行健康检查
      boolean healthy = activeService.isHealthy();

      // 构建健康响应
      return healthy
          ? Health.up().withDetail("service", serviceName).withDetail("mode", mode).build()
          : Health.down()
              .withDetail("service", serviceName)
              .withDetail("mode", mode)
              .withDetail("error", "Metadata service is down")
              .build();

    } catch (Exception ex) {
      // 处理健康检查异常
      return Health.down()
          .withDetail("service", serviceName)
          .withDetail("mode", mode)
          .withDetail("error", "Health check failed: " + ex.getMessage())
          .withException(ex)
          .build();
    }
  }
}
