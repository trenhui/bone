package com.bone.tools.codegen.domain.mapper;

import com.bone.tools.codegen.domain.entity.DataSourceConfigDO;

import java.util.List;
import java.util.Map;

/**
 * 数据源配置 Repository
 *
 * @author bone-team
 */
public interface DataSourceConfigMapper {

    // 基本的CRUD方法
    DataSourceConfigDO findById(Long id);
    DataSourceConfigDO save(DataSourceConfigDO entity);
    void deleteById(Long id);
    List<DataSourceConfigDO> findAll();
    
    // 支持根据多个条件查询
    List<DataSourceConfigDO> findByParams(Map<String, Object> params);
    
    // 支持根据URL和用户名查询
    List<DataSourceConfigDO> findByUrlAndUsername(String url, String username);

}
