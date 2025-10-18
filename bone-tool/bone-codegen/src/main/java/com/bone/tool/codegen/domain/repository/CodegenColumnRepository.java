package com.bone.tool.codegen.domain.repository;

import com.bone.tool.codegen.domain.entity.CodegenColumn;
import java.util.List;
import java.util.Optional;

public interface CodegenColumnRepository {
    
    /**
     * 根据ID删除列配置
     */
    void deleteById(Long id);
    
    /**
     * 根据ID查找列配置
     */
    Optional<CodegenColumn> findById(Long id);
    
    /**
     * 保存列配置
     */
    CodegenColumn save(CodegenColumn column);
    
    /**
     * 更新列配置
     */
    void update(CodegenColumn column);
    
    /**
     * 查询所有列配置
     */
    List<CodegenColumn> findAll();
}
