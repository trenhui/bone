package com.bone.tools.codegen.domain.mapper;

import com.bone.tools.codegen.domain.entity.CodegenColumnDO;

import java.util.List;
import java.util.Map;

public interface CodegenColumnMapper {

    // 直接定义需要的方法，避免使用Criteria API
    List<CodegenColumnDO> selectListByTableId(Long tableId);
    
    void deleteListByTableId(Long tableId);
    
    // 为RepositoryImpl提供实现支持的方法
    List<CodegenColumnDO> findByParams(Map<String, Object> params);
    
    void deleteByIds(List<Long> ids);
    
    Long save(CodegenColumnDO entity);

}
