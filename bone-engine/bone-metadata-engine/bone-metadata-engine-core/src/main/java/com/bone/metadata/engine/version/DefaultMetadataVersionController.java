package com.bone.metadata.engine.version;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/** 默认元数据版本控制器实现 用于管理元数据的版本信息 */
public class DefaultMetadataVersionController {

  private final AtomicLong versionCounter = new AtomicLong(0);

  /** 生成新版本号 */
  public String generateNewVersion(
      String currentVersion, MetadataVersionController.VersionChangeType changeType) {
    // 简单实现，委托给MetadataVersionController类的静态方法
    return new MetadataVersionController().generateNewVersion(currentVersion, changeType);
  }

  /** 生成元数据ID */
  public String generateMetadataId() {
    // 使用UUID生成唯一ID
    return UUID.randomUUID().toString();
  }

  /** 验证版本号格式 */
  public boolean isValidVersion(String version) {
    if (version == null || version.isEmpty()) {
      return false;
    }
    // 验证语义化版本格式
    return version.matches("\\d+\\.\\d+\\.\\d+");
  }
}
