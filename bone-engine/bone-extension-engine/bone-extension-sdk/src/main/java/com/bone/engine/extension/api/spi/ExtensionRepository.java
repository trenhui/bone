package com.bone.engine.extension.api.spi;

import com.bone.engine.extension.api.model.definition.ExtensionDefinition;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

/**
 * 扩展仓库接口 - 可读性优化版
 *
 * <p>设计原则： 1. 📖 **清晰明了**：方法名一看就懂 2. 🎯 **职责明确**：每个方法只做一件事 3. 🔍 **直观查询**：查询方法名明确表达意图 4. ⚡
 * **性能区分**：明确区分高频和低频操作
 */
public interface ExtensionRepository {

  // ==================== 生命周期管理 ====================

  /**
   * 注册扩展实现
   *
   * @return 如果已存在相同code的扩展，返回之前的扩展，否则返回null
   */
  @Nullable
  ExtensionDefinition registerExtension(
      @NonNull String extensionPoint, @NonNull ExtensionDefinition extension);

  /**
   * 注销扩展实现
   *
   * @return 被注销的扩展，如果不存在返回null
   */
  @Nullable
  ExtensionDefinition unregisterExtension(
      @NonNull String extensionPoint, @NonNull String extensionCode);

  // ==================== 核心查询方法 ====================

  /** [高频-路由器使用] 获取启用的扩展实现列表 注意：这个方法会被频繁调用，实现应该进行性能优化（缓存等） */
  @NonNull
  Collection<ExtensionDefinition> getEnabledExtensions(@NonNull String extensionPoint);

  /** [低频-管理使用] 获取扩展点的所有扩展实现 */
  @NonNull
  Collection<ExtensionDefinition> getAllExtensions(@NonNull String extensionPoint);

  /**
   * 根据扩展编码获取扩展实现
   *
   * @return Optional包装的扩展定义，避免null
   */
  @NonNull
  Optional<ExtensionDefinition> getExtensionByCode(
      @NonNull String extensionPoint, @NonNull String extensionCode);

  // ==================== 批量操作 ====================

  /**
   * 批量注册扩展实现
   *
   * @return 成功注册的数量
   */
  int batchRegisterExtensions(
      @NonNull Map<String, Collection<ExtensionDefinition>> extensionsByPoint);

  /**
   * 清空指定扩展点的所有扩展实现
   *
   * @return 清理的扩展数量
   */
  int clearExtensionPoint(@NonNull String extensionPoint);

  /** 清空所有扩展点（谨慎使用，通常用于测试环境） */
  void clearAllExtensions();

  // ==================== 元数据查询 ====================

  /** 获取所有已注册的扩展点名称 */
  @NonNull
  Set<String> getAllExtensionPointNames();

  /** 检查扩展点是否包含任何扩展实现 */
  boolean hasExtensions(@NonNull String extensionPoint);

  /** 获取仓库的详细统计信息 */
  @NonNull
  ExtensionRepositoryStats getRepositoryStats();

  // ==================== 统计信息类 ====================

  /** 扩展仓库统计信息 */
  class ExtensionRepositoryStats {
    private final String repositoryName;
    private final int totalExtensionPoints;
    private final int totalExtensions;
    private final int enabledExtensions;
    private final long lastUpdatedTimestamp;

    public ExtensionRepositoryStats(
        String repositoryName,
        int totalExtensionPoints,
        int totalExtensions,
        int enabledExtensions,
        long lastUpdatedTimestamp) {
      this.repositoryName = repositoryName;
      this.totalExtensionPoints = totalExtensionPoints;
      this.totalExtensions = totalExtensions;
      this.enabledExtensions = enabledExtensions;
      this.lastUpdatedTimestamp = lastUpdatedTimestamp;
    }

    public String getRepositoryName() {
      return repositoryName;
    }

    public int getTotalExtensionPoints() {
      return totalExtensionPoints;
    }

    public int getTotalExtensions() {
      return totalExtensions;
    }

    public int getEnabledExtensions() {
      return enabledExtensions;
    }

    public long getLastUpdatedTimestamp() {
      return lastUpdatedTimestamp;
    }

    /** 计算启用率（0.0 - 1.0） */
    public double getEnabledRate() {
      return totalExtensions > 0 ? (double) enabledExtensions / totalExtensions : 0.0;
    }

    /** 计算平均每个扩展点的扩展数 */
    public double getAverageExtensionsPerPoint() {
      return totalExtensionPoints > 0 ? (double) totalExtensions / totalExtensionPoints : 0.0;
    }

    @Override
    public String toString() {
      return String.format(
          "ExtensionRepositoryStats{name='%s', points=%d, total=%d, enabled=%d, rate=%.2f%%, avg=%.1f}",
          repositoryName,
          totalExtensionPoints,
          totalExtensions,
          enabledExtensions,
          getEnabledRate() * 100,
          getAverageExtensionsPerPoint());
    }
  }
}
