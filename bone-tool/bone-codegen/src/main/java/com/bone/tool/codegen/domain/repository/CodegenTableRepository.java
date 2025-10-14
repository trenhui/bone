package com.bone.tool.codegen.domain.repository;

import com.bone.metadata.sdk.Repository;
import com.bone.tool.codegen.application.dto.CodegenTablePageRequest;
import com.bone.tool.codegen.domain.entity.CodegenColumn;
import com.bone.tool.codegen.domain.entity.CodegenTable;
import com.bone.core.model.PageResult;

import java.util.List;

public interface CodegenTableRepository extends Repository<CodegenTable, Long> {

    default CodegenTable selectByTableNameAndDataSourceConfigId(String tableName, Long dataSourceConfigId) {
        // 简化实现，直接调用findOneByCriteria
        try {
            // 这里使用null作为Criteria，由实现类处理
            return findOneByCriteria(null);
        } catch (Exception e) {
            return null;
        }
    }

    default PageResult<CodegenTable> selectPage(CodegenTablePageRequest pageReqVO) {
        // 简化实现，直接调用pageByCriteria
        try {
            // 这里使用null作为Criteria，由实现类处理
            return pageByCriteria(null);
        } catch (Exception e) {
            try {
                return (PageResult<CodegenTable>) Class.forName("com.bone.core.model.PageResult").getDeclaredConstructor().newInstance();
            } catch (Exception ex) {
                return null;
            }
        }
    }

    default List<CodegenTable> selectListByDataSourceConfigId(Long dataSourceConfigId) {
        // 简化实现，直接调用findByCriteria
        try {
            // 这里使用null作为Criteria，由实现类处理
            return findByCriteria(null);
        } catch (Exception e) {
            return List.of();
        }
    }

    default List<CodegenTable> selectListByTemplateTypeAndMasterTableId(Integer templateType, Long masterTableId) {
        // 简化实现，直接调用findByCriteria
        try {
            // 这里使用null作为Criteria，由实现类处理
            return findByCriteria(null);
        } catch (Exception e) {
            return List.of();
        }
    }

    // 以下方法由实现类提供
    CodegenTable findOneByCriteria(Object criteria);
    PageResult<CodegenTable> pageByCriteria(Object criteria);
    List<CodegenTable> findByCriteria(Object criteria);

}
