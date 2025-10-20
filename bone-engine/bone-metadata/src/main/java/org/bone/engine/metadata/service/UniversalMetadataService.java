package org.bone.engine.metadata.service;

import org.bone.engine.metadata.model.EntityMetadata;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 通用元数据服务接口 - 实现Universal Metadata Protocol (UMP)
 * 提供元数据的创建、读取、更新、删除、版本控制等标准操作
 * 
 * @author Bone Engine Team
 */
public interface UniversalMetadataService {

    // ================ 元数据基本操作 ================

    /**
     * 创建新的实体元数据
     * @param metadata 实体元数据对象
     * @return 创建后的实体元数据（包含生成的ID和版本信息）
     */
    EntityMetadata createEntityMetadata(EntityMetadata metadata);

    /**
     * 根据API名称获取实体元数据
     * @param apiName 实体API名称
     * @return 实体元数据对象，如果不存在则返回empty
     */
    Optional<EntityMetadata> getEntityMetadataByApiName(String apiName);

    /**
     * 根据API名称和版本获取实体元数据
     * @param apiName 实体API名称
     * @param version 版本号
     * @return 实体元数据对象，如果不存在则返回empty
     */
    Optional<EntityMetadata> getEntityMetadataByApiNameAndVersion(String apiName, String version);

    /**
     * 更新实体元数据
     * @param metadata 实体元数据对象（需要包含apiName和版本信息）
     * @return 更新后的实体元数据
     */
    EntityMetadata updateEntityMetadata(EntityMetadata metadata);

    /**
     * 删除实体元数据
     * @param apiName 实体API名称
     * @param cascade 是否级联删除相关元数据（如关系、规则等）
     */
    void deleteEntityMetadata(String apiName, boolean cascade);

    // ================ 批量操作 ================

    /**
     * 批量获取实体元数据
     * @param apiNames 实体API名称列表
     * @return 实体元数据映射（API名称 -> 元数据对象）
     */
    Map<String, EntityMetadata> getEntityMetadataBatch(List<String> apiNames);

    /**
     * 批量创建实体元数据
     * @param metadataList 实体元数据列表
     * @return 批量操作结果
     */
    List<EntityMetadata> createEntityMetadataBatch(List<EntityMetadata> metadataList);

    // ================ 查询与过滤 ================

    /**
     * 根据业务域查询实体元数据
     * @param domain 业务域名称
     * @return 实体元数据列表
     */
    List<EntityMetadata> getEntityMetadataByDomain(String domain);

    /**
     * 根据实体类型查询实体元数据
     * @param entityType 实体类型
     * @return 实体元数据列表
     */
    List<EntityMetadata> getEntityMetadataByType(String entityType);

    /**
     * 搜索实体元数据（支持名称、描述等字段的模糊搜索）
     * @param keyword 搜索关键字
     * @param page 页码
     * @param size 每页大小
     * @return 实体元数据列表
     */
    List<EntityMetadata> searchEntityMetadata(String keyword, int page, int size);

    // ================ 版本管理 ================

    /**
     * 获取实体元数据的所有版本
     * @param apiName 实体API名称
     * @return 版本元数据列表
     */
    List<Map<String, Object>> getEntityMetadataVersions(String apiName);

    /**
     * 创建实体元数据的新版本
     * @param apiName 实体API名称
     * @param newVersion 新版本号
     * @param metadata 新版本的元数据（可选，如果为null则基于当前版本创建）
     * @return 新版本的实体元数据
     */
    EntityMetadata createEntityMetadataVersion(String apiName, String newVersion, EntityMetadata metadata);

    /**
     * 将实体元数据回滚到指定版本
     * @param apiName 实体API名称
     * @param targetVersion 目标版本号
     * @return 回滚后的实体元数据
     */
    EntityMetadata rollbackEntityMetadataToVersion(String apiName, String targetVersion);

    /**
     * 比较两个版本的实体元数据差异
     * @param apiName 实体API名称
     * @param version1 版本1
     * @param version2 版本2
     * @return 元数据差异对象
     */
    Map<String, Object> compareEntityMetadataVersions(String apiName, String version1, String version2);

    // ================ 验证与导入导出 ================

    /**
     * 验证实体元数据的合法性
     * @param metadata 实体元数据对象
     * @return 验证结果
     */
    Map<String, Object> validateEntityMetadata(EntityMetadata metadata);

    /**
     * 从JSON字符串导入实体元数据
     * @param jsonString 元数据JSON字符串
     * @return 导入的实体元数据对象
     */
    EntityMetadata importEntityMetadataFromJson(String jsonString);

    /**
     * 将实体元数据导出为JSON字符串
     * @param apiName 实体API名称
     * @return 元数据JSON字符串
     */
    String exportEntityMetadataToJson(String apiName);

    /**
     * 从YAML字符串导入实体元数据
     * @param yamlString 元数据YAML字符串
     * @return 导入的实体元数据对象
     */
    EntityMetadata importEntityMetadataFromYaml(String yamlString);

    /**
     * 将实体元数据导出为YAML字符串
     * @param apiName 实体API名称
     * @return 元数据YAML字符串
     */
    String exportEntityMetadataToYaml(String apiName);

    // ================ 变更管理 ================

    /**
     * 注册元数据变更监听器
     * @param listener 变更监听器
     */
    void registerMetadataChangeListener(MetadataChangeListener listener);

    /**
     * 取消注册元数据变更监听器
     * @param listener 变更监听器
     */
    void unregisterMetadataChangeListener(MetadataChangeListener listener);

    /**
     * 触发元数据变更事件（通常由内部调用）
     * @param event 变更事件对象
     */
    void fireMetadataChangeEvent(Map<String, Object> event);

    // ================ 关系管理 ================

    /**
     * 获取实体的所有关联实体
     * @param apiName 实体API名称
     * @return 关联实体列表（包含关系信息）
     */
    List<Map<String, Object>> getRelatedEntities(String apiName);

    /**
     * 分析元数据变更的影响范围
     * @param metadata 变更后的实体元数据
     * @return 影响分析结果
     */
    Map<String, Object> analyzeMetadataChangeImpact(EntityMetadata metadata);

    // ================ 缓存管理 ================

    /**
     * 刷新指定实体元数据的缓存
     * @param apiName 实体API名称
     */
    void refreshMetadataCache(String apiName);

    /**
     * 清除所有元数据缓存
     */
    void clearAllMetadataCache();

    // ================ 系统管理 ================

    /**
     * 获取元数据服务的运行时信息
     * @return 运行时信息
     */
    Map<String, Object> getRuntimeInfo();

    /**
     * 检查元数据服务的健康状态
     * @return 健康状态信息
     */
    Map<String, Object> checkHealth();
    
    // ================ 元数据变更监听器接口 ================
    
    /**
     * 元数据变更监听器接口
     */
    interface MetadataChangeListener {
        
        /**
         * 处理元数据变更事件
         * @param event 变更事件
         */
        void onMetadataChanged(Map<String, Object> event);
        
        /**
         * 获取监听器的优先级（数字越小优先级越高）
         * @return 优先级
         */
        int getPriority();
    }
}