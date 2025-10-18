package com.bone.tool.codegen.domain.repository;

import com.bone.tool.codegen.domain.entity.Datasource;

import java.util.List;
import java.util.Optional;

/**
 * 数据源配置 Repository
 *
 * @author bone-team
 */
public interface DataSourceConfigRepository {
    
    /**
     * 根据ID查找数据源
     */
    Optional<Datasource> findById(Long id);
    
    /**
     * 保存数据源配置
     */
    Long save(Datasource datasource);
    
    /**
     * 更新数据源配置
     */
    void update(Datasource datasource);
    
    /**
     * 删除数据源配置
     */
    void deleteById(Long id);
    
    /**
     * 查询所有数据源配置
     */
    List<Datasource> findAll();
    
    /**
     * 根据条件查询数据源配置
     */
    List<Datasource> findByCondition(Object condition);
}
