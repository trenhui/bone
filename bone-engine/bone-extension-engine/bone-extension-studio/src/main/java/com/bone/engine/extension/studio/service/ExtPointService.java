package com.bone.engine.extension.studio.service;

import com.bone.engine.extension.studio.model.ExtPointEntity;
import com.bone.engine.extension.studio.model.ExtensionEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 扩展点服务接口
 */
public interface ExtPointService {

    /**
     * 查询所有扩展点（分页）
     */
    Page<ExtPointEntity> findAllExtPoints(Pageable pageable);

    /**
     * 根据ID查询扩展点
     */
    Optional<ExtPointEntity> findExtPointById(Long id);

    /**
     * 根据接口名称查询扩展点
     */
    Optional<ExtPointEntity> findExtPointByInterfaceName(String interfaceName);

    /**
     * 保存扩展点
     */
    ExtPointEntity saveExtPoint(ExtPointEntity extPoint);

    /**
     * 更新扩展点
     */
    ExtPointEntity updateExtPoint(Long id, ExtPointEntity extPoint);

    /**
     * 删除扩展点
     */
    void deleteExtPoint(Long id);

    /**
     * 启用/禁用扩展点
     */
    ExtPointEntity enableExtPoint(Long id, boolean enabled);

    /**
     * 根据领域查询扩展点列表
     */
    List<ExtPointEntity> findExtPointsByDomain(String domain);

    /**
     * 根据分类查询扩展点列表
     */
    List<ExtPointEntity> findExtPointsByCategory(String category);

    /**
     * 根据领域和分类查询扩展点列表
     */
    List<ExtPointEntity> findExtPointsByDomainAndCategory(String domain, String category);

    /**
     * 搜索扩展点
     */
    Page<ExtPointEntity> searchExtPoints(String keyword, Pageable pageable);

    /**
     * 获取扩展点的所有扩展实现
     */
    List<ExtensionEntity> findExtensionsByExtPointId(Long extPointId);

    /**
     * 扫描项目中的扩展点并注册
     */
    int scanAndRegisterExtPoints();

    /**
     * 获取所有可用的领域列表
     */
    List<String> findAllDomains();

    /**
     * 获取所有可用的分类列表
     */
    List<String> findAllCategories();
    
    /**
     * 获取扩展点统计信息
     */
    Map<String, Long> getExtPointStatsByDomain();
    
    /**
     * 获取扩展点分类统计信息
     */
    Map<String, Long> getExtPointStatsByCategory();
    
    /**
     * 获取总扩展点数量
     */
    long getTotalExtPointCount();
}