package com.bone.engine.extension.support.repository;

import com.bone.engine.extension.api.model.definition.ExtensionDefinition;
import com.bone.engine.extension.api.model.definition.ExtensionPointDefinition;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * 扩展仓库接口 - Bone扩展框架最终版
 *
 * 设计原则：
 * 1. 领域驱动：方法名直接反映扩展注册、注销、路由等业务场景。
 * 2. 无歧义：每个方法名都有明确唯一的含义。
 * 3. 性能考虑：区分高频查询（如getEnabledExtensions）和管理查询。
 * 4. 监控友好：提供完整的统计信息。
 *
 * @author Bone Engine Team
 * @since 2.0.0-GA
 */
public interface ExtensionRepository {

    // ==================== 扩展生命周期管理 ====================

    /**
     * 注册扩展实现到指定扩展点
     *
     * @param extensionPoint 扩展点全限定名
     * @param extension 扩展定义
     * @return 如果扩展代码已存在，返回已注册的定义，否则返回null
     */
    @Nullable
    ExtensionDefinition register(@NonNull String extensionPoint, @NonNull Object extension);

    /**
     * 注册扩展实现到指定扩展点
     *
     * @param extensionPoint 扩展点全限定名
     * @param extension 扩展定义
     * @return 如果扩展代码已存在，返回已注册的定义，否则返回null
     */
    @Nullable
    ExtensionDefinition register(@NonNull String extensionPoint, @NonNull ExtensionDefinition extension);

    /**
     * 注销指定扩展点的扩展实现
     *
     * @param extensionPoint 扩展点全限定名
     * @param extensionCode 扩展代码
     * @return 被注销的扩展定义，不存在时返回null
     */
    @Nullable
    ExtensionDefinition unregister(@NonNull String extensionPoint, @NonNull String extensionCode);

    /**
     * 根据扩展代码全局注销扩展实现
     *
     * @param extensionCode 扩展代码
     * @return 被注销的扩展定义，不存在时返回null
     */
    @Nullable
    ExtensionDefinition unregisterByCode(@NonNull String extensionCode);

    // ==================== 路由核心查询 ====================

    /**
     * 获取扩展点下所有启用的扩展实现（路由器高频调用）
     *
     * @param extensionPoint 扩展点全限定名
     * @return 启用的扩展定义集合，不会返回null
     */
    @NonNull
    Collection<ExtensionDefinition> getEnabledExtensions(@NonNull String extensionPoint);


    /**
     * 获取扩展点下所有启用的扩展实现（路由器高频调用）
     *
     * @param extPointClazz 扩展点类
     * @return 启用的扩展定义集合，不会返回null
     */
    @NonNull
    Collection<Object> getEnabledExtensionObjects(@NonNull Class<?> extPointClazz);

    // ==================== 管理与调试查询 ====================

    /**
     * 获取扩展点下所有扩展实现（包含禁用扩展）
     *
     * @param extensionPoint 扩展点全限定名
     * @return 所有扩展定义集合，不会返回null
     */
    @NonNull
    Collection<ExtensionDefinition> getAllExtensions(@NonNull String extensionPoint);

    /**
     * 获取扩展点下所有扩展实现（包含禁用扩展）
     *
     * @return 所有扩展定义集合，不会返回null
     */
    @NonNull
    Collection<ExtensionPointDefinition> getAllExtensionPointDefinitions();



    /**
     * 精确查找扩展实现
     *
     * @param extensionPoint 扩展点全限定名
     * @param extensionCode 扩展代码
     * @return 扩展定义，不存在时返回null
     */
    @Nullable
    ExtensionDefinition getExtension(@NonNull String extensionPoint, @NonNull String extensionCode);

    /**
     * 全局按扩展代码查找扩展实现
     *
     * @param extensionCode 扩展代码
     * @return 扩展定义，不存在时返回null
     */
    @Nullable
    ExtensionDefinition getExtensionByCode(@NonNull String extensionCode);

    // ==================== 存在性检查 ====================

    /**
     * 检查扩展实现是否已注册
     *
     * @param extensionPoint 扩展点全限定名
     * @param extensionCode 扩展代码
     * @return 是否已注册
     */
    boolean isRegistered(@NonNull String extensionPoint, @NonNull String extensionCode);

    /**
     * 检查扩展点是否包含任何扩展实现
     *
     * @param extensionPoint 扩展点全限定名
     * @return 是否包含扩展
     */
    boolean hasExtensions(@NonNull String extensionPoint);

    // ==================== 批量操作 ====================

    /**
     * 批量注册扩展实现
     *
     * @param extensionsByPoint 扩展映射：key=扩展点，value=该扩展点下的扩展集合
     * @return 成功注册的数量
     */
    int registerAll(@NonNull Map<String, Collection<ExtensionDefinition>> extensionsByPoint);

    /**
     * 清空扩展点下的所有扩展实现
     *
     * @param extensionPoint 扩展点全限定名
     * @return 被清理的扩展数量
     */
    int clearExtensions(@NonNull String extensionPoint);

    /**
     * 清空整个仓库
     */
    void clearAll();

    // ==================== 高级查询 ====================

    /**
     * 按条件搜索扩展（管理后台高级筛选）
     *
     * @param condition 筛选条件
     * @return 满足条件的扩展定义集合
     */
    @NonNull
    Collection<ExtensionDefinition> findExtensions(@NonNull Predicate<ExtensionDefinition> condition);

    // ==================== 统计信息 ====================

    /**
     * 统计已注册的扩展点数量
     *
     * @return 扩展点总数
     */
    int countExtensionPoints();

    /**
     * 统计所有扩展实现的总数
     *
     * @return 扩展实现总数
     */
    int countExtensions();

    /**
     * 统计指定扩展点的扩展实现数量
     *
     * @param extensionPoint 扩展点全限定名
     * @return 扩展实现数量
     */
    int countExtensionsInPoint(@NonNull String extensionPoint);

    /**
     * 获取所有已注册的扩展点名称
     *
     * @return 扩展点名称集合
     */
    @NonNull
    Set<String> getExtensionPointNames();

    /**
     * 获取仓库统计信息
     *
     * @return 仓库统计信息
     */
    @NonNull
    RepositoryStats getStats();

    /**
     * 获取仓库名称
     *
     * @return 仓库名称
     */
    @NonNull
    String getName();

    /**
     * 获取仓库类型
     *
     * @return 仓库类型
     */
    @NonNull
    String getType();

    // ==================== 统计信息类 ====================

    class RepositoryStats {
        private final int extensionPointCount;
        private final int extensionCount;
        private final long lastModifiedTime;
        private final String repositoryName;

        public RepositoryStats(int extensionPointCount, int extensionCount,
                               long lastModifiedTime, String repositoryName) {
            this.extensionPointCount = extensionPointCount;
            this.extensionCount = extensionCount;
            this.lastModifiedTime = lastModifiedTime;
            this.repositoryName = repositoryName;
        }

        public int getExtensionPointCount() { return extensionPointCount; }
        public int getExtensionCount() { return extensionCount; }
        public long getLastModifiedTime() { return lastModifiedTime; }
        public String getRepositoryName() { return repositoryName; }
    }
}