package com.bone.file.infrastructure.observability;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * 文件服务模块健康检查。
 *
 * <p>当前为 STUB 兜底（保证 /actuator/health 聚合时本模块有 detail）。
 *
 * <p>后续真检查应覆盖：
 *
 * <ul>
 *   <li>对象存储（OSS/S3/MinIO）HEAD bucket 探测；
 *   <li>文件上传/下载关键路径轻量探测；
 *   <li>已用存储容量 / 配额。
 * </ul>
 */
@Component
public class FileModuleHealthIndicator implements HealthIndicator {

  @Override
  public Health health() {
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("module", "bone-file");
    details.put("status", "STUB");
    details.put(
        "limitation",
        "FileModuleHealthIndicator is a placeholder; real object storage / upload path checks pending");
    return Health.up().withDetails(details).build();
  }
}
