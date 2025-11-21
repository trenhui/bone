package com.bone.engine.extension.support.repository;

import com.bone.engine.extension.api.model.definition.ExtensionDefinition;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * 扩展点仓库接口 - 基于业界最佳实践设计
 *
 * 设计原则：
 * 1. 单一职责：专注于扩展定义的存储和检索
 * 2. 接口隔离：提供细粒度的操作方法
 * 3. 明确命名：方法名清晰表达意图
 * 4. 空安全：使用@NonNull/@Nullable注解
 * 5. 泛型支持：类型安全的操作
 *
 * @author Bone Engine Team
 * @since 2.0.0-GA
 */
public interface ExtensionRepository {

    // ==================== 核心存储操作 ====================

    /**
     * 保存扩展定义到指定扩展点
     *
     * @param extensionPoint 扩展点全限定名
     * @param definition 扩展定义
     * @return 如果已存在相同扩展代码的定义，返回旧定义，否则返回null
     */
    @Nullable
    ExtensionDefinition save(@NonNull String extensionPoint, @NonNull ExtensionDefinition definition);

    /**
     * 根据扩展点和扩展代码删除扩展定义
     *
     * @param extensionPoint 扩展点全限定名
     * @param extensionCode 扩展代码
     * @return 被删除的扩展定义，如果不存在返回null
     */
    @Nullable
    ExtensionDefinition delete(@NonNull String extensionPoint, @NonNull String extensionCode);

    /**
     * 根据扩展代码删除扩展定义（全局搜索）
     *
     * @param extensionCode 扩展代码
     * @return 被删除的扩展定义，如果不存在返回null
     */
    @Nullable
    ExtensionDefinition deleteByCode(@NonNull String extensionCode);

    // ==================== 精确查询操作 ====================

    /**
     * 根据扩展点和扩展代码查询扩展定义
     *
     * @param extensionPoint 扩展点全限定名
     * @param extensionCode 扩展代码
     * @return 扩展定义，不存在返回null
     */
    @Nullable
    ExtensionDefinition findByPointAndCode(@NonNull String extensionPoint, @NonNull String extensionCode);

    /**
     * 根据扩展代码查询扩展定义（全局搜索）
     *
     * @param extensionCode 扩展代码
     * @return 扩展定义，不存在返回null
     */
    @Nullable
    ExtensionDefinition findByCode(@NonNull String extensionCode);

    /**
     * 查询扩展点下的所有扩展定义
     *
     * @param extensionPoint 扩展点全限定名
     * @return 扩展定义集合（不可修改），不会返回null
     */
    @NonNull
    Collection<ExtensionDefinition> findAllByPoint(@NonNull String extensionPoint);

    /**
     * 查询所有扩展定义
     *
     * @return 所有扩展定义的集合（不可修改）
     */
    @NonNull
    Collection<ExtensionDefinition> findAll();

    // ==================== 条件查询操作 ====================

    /**
     * 根据条件查询扩展定义
     *
     * @param predicate 查询条件
     * @return 满足条件的扩展定义集合
     */
    @NonNull
    Collection<ExtensionDefinition> findByCondition(@NonNull Predicate<ExtensionDefinition> predicate);

    /**
     * 查询启用的扩展定义
     *
     * @param extensionPoint 扩展点全限定名
     * @return 启用的扩展定义集合
     */
    @NonNull
    Collection<ExtensionDefinition> findEnabledByPoint(@NonNull String extensionPoint);

    // ==================== 存在性检查 ====================

    /**
     * 检查扩展定义是否存在
     *
     * @param extensionPoint 扩展点全限定名
     * @param extensionCode 扩展代码
     * @return 是否存在
     */
    boolean exists(@NonNull String extensionPoint, @NonNull String extensionCode);

    /**
     * 检查扩展点是否存在任何扩展定义
     *
     * @param extensionPoint 扩展点全限定名
     * @return 是否存在扩展定义
     */
    boolean existsByPoint(@NonNull String extensionPoint);

    // ==================== 批量操作 ====================

    /**
     * 批量保存扩展定义
     *
     * @param definitions 扩展定义映射（扩展点 -> 扩展定义列表）
     * @return 保存成功的数量
     */
    int saveAll(@NonNull Map<String, Collection<ExtensionDefinition>> definitions);

    /**
     * 清空指定扩展点的所有扩展定义
     *
     * @param extensionPoint 扩展点全限定名
     * @return 删除的数量
     */
    int clearByPoint(@NonNull String extensionPoint);

    // ==================== 统计信息 ====================

    /**
     * 获取扩展点数量
     *
     * @return 扩展点总数
     */
    int countPoints();

    /**
     * 获取扩展定义总数
     *
     * @return 扩展定义总数
     */
    int countExtensions();

    /**
     * 获取指定扩展点的扩展定义数量
     *
     * @param extensionPoint 扩展点全限定名
     * @return 扩展定义数量
     */
    int countByPoint(@NonNull String extensionPoint);

    /**
     * 获取所有扩展点名称
     *
     * @return 扩展点名称集合
     */
    @NonNull
    Set<String> getAllExtensionPoints();

    // ==================== 仓库管理 ====================

    /**
     * 清空整个仓库
     */
    void clear();

    /**
     * 获取仓库统计信息
     *
     * @return 统计信息
     */
    @NonNull
    RepositoryStatistics getStatistics();

    /**
     * 仓库名称
     */
    @NonNull
    String getName();

    /**
     * 仓库实现类型
     */
    @NonNull
    String getType();

    /**
     * 统计信息类
     */
    class RepositoryStatistics {
        private final int extensionPointCount;
        private final int extensionCount;
        private final long lastModifiedTime;
        private final String repositoryName;

        public RepositoryStatistics(int extensionPointCount, int extensionCount,
                                    long lastModifiedTime, String repositoryName) {
            this.extensionPointCount = extensionPointCount;
            this.extensionCount = extensionCount;
            this.lastModifiedTime = lastModifiedTime;
            this.repositoryName = repositoryName;
        }

        // Getters
        public int getExtensionPointCount() { return extensionPointCount; }
        public int getExtensionCount() { return extensionCount; }
        public long getLastModifiedTime() { return lastModifiedTime; }
        public String getRepositoryName() { return repositoryName; }
    }
}