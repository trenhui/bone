package com.bone.engine.extension.studio.repository;

import com.bone.engine.extension.studio.model.ExtensionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 扩展实现数据访问接口
 */
@Repository
public interface ExtensionRepository extends JpaRepository<ExtensionEntity, Long>, JpaSpecificationExecutor<ExtensionEntity> {

    /**
     * 根据扩展点ID查询扩展实现列表
     */
    List<ExtensionEntity> findByExtPointId(Long extPointId);

    /**
     * 根据扩展点ID和租户代码查询扩展实现列表
     */
    List<ExtensionEntity> findByExtPointIdAndTenantCode(Long extPointId, String tenantCode);

    /**
     * 根据实现类全限定名查询扩展实现
     */
    Optional<ExtensionEntity> findByClassName(String className);

    /**
     * 根据扩展点ID和启用状态查询扩展实现列表
     */
    List<ExtensionEntity> findByExtPointIdAndEnabled(Long extPointId, boolean enabled);

    /**
     * 根据租户代码和启用状态查询扩展实现列表
     */
    List<ExtensionEntity> findByTenantCodeAndEnabled(String tenantCode, boolean enabled);

    /**
     * 根据扩展点ID和优先级排序查询扩展实现列表
     */
    List<ExtensionEntity> findByExtPointIdOrderByPriorityAsc(Long extPointId);

    /**
     * 统计扩展点的实现数量
     */
    long countByExtPointId(Long extPointId);
}