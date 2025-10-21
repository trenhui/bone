package com.bone.engine.extension.studio.repository;

import com.bone.engine.extension.studio.model.ExtPointEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 扩展点数据访问接口
 */
@Repository
public interface ExtPointRepository extends JpaRepository<ExtPointEntity, Long>, JpaSpecificationExecutor<ExtPointEntity> {

    /**
     * 根据接口名称查询扩展点
     */
    Optional<ExtPointEntity> findByInterfaceName(String interfaceName);

    /**
     * 根据领域查询扩展点列表
     */
    List<ExtPointEntity> findByDomain(String domain);

    /**
     * 根据分类查询扩展点列表
     */
    List<ExtPointEntity> findByCategory(String category);

    /**
     * 根据状态查询扩展点列表
     */
    List<ExtPointEntity> findByEnabled(boolean enabled);

    /**
     * 根据领域和分类查询扩展点列表
     */
    List<ExtPointEntity> findByDomainAndCategory(String domain, String category);

    /**
     * 模糊查询扩展点名称或描述
     */
    List<ExtPointEntity> findByNameContainingOrDescriptionContaining(String nameKeyword, String descriptionKeyword);
}