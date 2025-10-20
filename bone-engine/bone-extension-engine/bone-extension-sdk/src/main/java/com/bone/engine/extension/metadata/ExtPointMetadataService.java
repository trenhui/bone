package com.bone.engine.extension.metadata;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 扩展点元数据服务接口
 * <p>
 * 提供扩展点元数据的收集、存储和查询功能，支持可视化平台展示
 * </p>
 * 
 * @since 1.0.0
 */
public interface ExtPointMetadataService {
    
    /**
     * 获取所有扩展点元数据
     * 
     * @return 所有扩展点元数据列表
     */
    List<ExtPointMetadata> getAllExtPointMetadata();
    
    /**
     * 根据接口名获取扩展点元数据
     * 
     * @param interfaceName 扩展点接口全限定名
     * @return 扩展点元数据，若不存在则返回空
     */
    Optional<ExtPointMetadata> getExtPointMetadata(String interfaceName);
    
    /**
     * 根据条件查询扩展点元数据
     * 
     * @param criteria 查询条件，支持按分类、标签、租户等过滤
     * @return 符合条件的扩展点元数据列表
     */
    List<ExtPointMetadata> findExtPointMetadata(Map<String, String> criteria);
    
    /**
     * 获取扩展点实现的详细元数据
     * 
     * @param interfaceName 扩展点接口名
     * @param implClassName 实现类名
     * @return 扩展实现元数据，若不存在则返回空
     */
    Optional<ExtensionImplMetadata> getExtensionImplMetadata(String interfaceName, String implClassName);
    
    /**
     * 刷新元数据缓存
     * <p>
     * 重新从系统中收集所有扩展点和扩展实现的元数据
     * </p>
     */
    void refreshMetadata();
    
    /**
     * 更新扩展实现的路由配置元数据
     * <p>
     * 用于可视化配置平台动态调整路由规则
     * </p>
     * 
     * @param interfaceName 扩展点接口名
     * @param implClassName 实现类名
     * @param routingConfig 新的路由配置
     * @return 是否更新成功
     */
    boolean updateExtensionRoutingConfig(String interfaceName, String implClassName, Map<String, String> routingConfig);
    
    /**
     * 获取扩展点使用统计信息
     * 
     * @return 扩展点调用统计信息
     */
    Map<String, ExtensionUsageStats> getExtPointUsageStats();
    
    /**
     * 导出扩展点元数据为JSON格式
     * 
     * @return JSON格式的元数据
     */
    String exportMetadataAsJson();
    
    /**
     * 导入扩展点元数据配置
     * <p>
     * 用于从可视化平台导入路由规则配置
     * </p>
     * 
     * @param metadataJson JSON格式的元数据配置
     * @return 导入结果
     */
    ImportResult importMetadataFromJson(String metadataJson);
    
    /**
     * 扩展点使用统计信息
     */
    interface ExtensionUsageStats {
        String getInterfaceName();
        long getTotalInvokeCount();
        Map<String, Long> getImplInvokeCounts();
        Map<String, Double> getImplAvgInvokeTimes();
        String getMostUsedImplementation();
    }
    
    /**
     * 导入结果
     */
    interface ImportResult {
        boolean isSuccess();
        int getUpdatedCount();
        int getFailedCount();
        List<String> getErrorMessages();
    }
}