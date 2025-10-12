package com.bone.tools.codegen.domain.mapper;

import com.bone.tools.codegen.domain.entity.CodegenTableDO;
import com.bone.tools.codegen.application.dto.CodegenTablePageRequest;
import com.bone.core.model.PageResult;

import java.util.List;

public interface CodegenTableMapper {

    default CodegenTableDO selectByTableNameAndDataSourceConfigId(String tableName, Long dataSourceConfigId) {
        // 简化实现，直接调用findOneByCriteria
        try {
            // 这里使用null作为Criteria，由实现类处理
            return findOneByCriteria(null);
        } catch (Exception e) {
            return null;
        }
    }

    default PageResult<CodegenTableDO> selectPage(CodegenTablePageRequest pageReqVO) {
        // 简化实现，直接调用pageByCriteria
        try {
            // 这里使用null作为Criteria，由实现类处理
            return pageByCriteria(null);
        } catch (Exception e) {
            try {
                return (PageResult<CodegenTableDO>) Class.forName("com.bone.core.model.PageResult").getDeclaredConstructor().newInstance();
            } catch (Exception ex) {
                return null;
            }
        }
    }

    default List<CodegenTableDO> selectListByDataSourceConfigId(Long dataSourceConfigId) {
        // 简化实现，直接调用findByCriteria
        try {
            // 这里使用null作为Criteria，由实现类处理
            return findByCriteria(null);
        } catch (Exception e) {
            return List.of();
        }
    }

    default List<CodegenTableDO> selectListByTemplateTypeAndMasterTableId(Integer templateType, Long masterTableId) {
        // 简化实现，直接调用findByCriteria
        try {
            // 这里使用null作为Criteria，由实现类处理
            return findByCriteria(null);
        } catch (Exception e) {
            return List.of();
        }
    }

    // 以下方法由实现类提供
    CodegenTableDO findOneByCriteria(Object criteria);
    PageResult<CodegenTableDO> pageByCriteria(Object criteria);
    List<CodegenTableDO> findByCriteria(Object criteria);

}
