package com.bone.engine.extension.studio.service;

import com.bone.engine.extension.studio.model.ExtensionEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

/**
 * 扩展实现服务接口
 */
public interface ExtensionService {

    /**
     * 查询所有扩展实现（分页）
     */
    Page<ExtensionEntity> findAllExtensions(Pageable pageable);

    /**
     * 根据ID查询扩展实现
     */
    Optional<ExtensionEntity> findExtensionById(Long id);

    /**
     * 根据扩展点ID查询扩展实现列表
     */
    List<ExtensionEntity> findExtensionsByExtPointId(Long extPointId);

    /**
     * 根据扩展点ID和租户代码查询扩展实现列表
     */
    List<ExtensionEntity> findExtensionsByExtPointIdAndTenantCode(Long extPointId, String tenantCode);

    /**
     * 保存扩展实现
     */
    ExtensionEntity saveExtension(ExtensionEntity extension);

    /**
     * 更新扩展实现
     */
    ExtensionEntity updateExtension(Long id, ExtensionEntity extension);

    /**
     * 删除扩展实现
     */
    void deleteExtension(Long id);

    /**
     * 启用/禁用扩展实现
     */
    ExtensionEntity enableExtension(Long id, boolean enabled);

    /**
     * 更新扩展实现的优先级
     */
    ExtensionEntity updateExtensionPriority(Long id, int priority);

    /**
     * 根据租户代码查询扩展实现列表
     */
    List<ExtensionEntity> findExtensionsByTenantCode(String tenantCode);

    /**
     * 搜索扩展实现
     */
    Page<ExtensionEntity> searchExtensions(String keyword, Pageable pageable);

    /**
     * 扫描项目中的扩展实现并注册
     */
    int scanAndRegisterExtensions();

    /**
     * 验证扩展实现的有效性
     */
    boolean validateExtension(ExtensionEntity extension);

    /**
     * 获取扩展实现的调用统计信息
     */
    String getExtensionStatistics(Long id);

    /**
     * 重置扩展实现的调用统计信息
     */
    void resetExtensionStatistics(Long id);
}