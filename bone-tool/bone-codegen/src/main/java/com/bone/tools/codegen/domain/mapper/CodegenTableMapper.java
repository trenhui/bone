package com.bone.tools.codegen.domain.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bone.core.model.PageResult;
import com.bone.tools.codegen.domain.entity.CodegenTableDO;
import com.bone.tools.codegen.application.dto.CodegenTablePageRequest;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface CodegenTableMapper extends BaseMapper<CodegenTableDO> {

    default CodegenTableDO selectByTableNameAndDataSourceConfigId(String tableName, Long dataSourceConfigId) {
        LambdaQueryWrapper<CodegenTableDO> wrapper = new LambdaQueryWrapper();
        wrapper.eq(CodegenTableDO::getTableName,tableName).eq(CodegenTableDO::getDataSourceConfigId,dataSourceConfigId);
        return selectOne(wrapper);
    }

    default PageResult<CodegenTableDO> selectPage(CodegenTablePageRequest pageReqVO) {
        LambdaQueryWrapper<CodegenTableDO> queryWrapper = new LambdaQueryWrapper<>();
        if (StringUtils.isNotBlank(pageReqVO.getTableName())) {
            queryWrapper.like(CodegenTableDO::getTableName, pageReqVO.getTableName());
        }
        if (StringUtils.isNotBlank(pageReqVO.getTableComment())) {
            queryWrapper.like(CodegenTableDO::getTableComment, pageReqVO.getTableComment());
        }
        if (StringUtils.isNotBlank(pageReqVO.getClassName())) {
            queryWrapper.like(CodegenTableDO::getClassName, pageReqVO.getClassName());
        }
        if(org.apache.commons.lang3.StringUtils.isNotBlank(pageReqVO.getStartTime())) {
            queryWrapper.ge(CodegenTableDO::getCreateTime, pageReqVO.getStartTime());
        }
        if(org.apache.commons.lang3.StringUtils.isNotBlank(pageReqVO.getEndTime())) {
            queryWrapper.le(CodegenTableDO::getCreateTime, pageReqVO.getEndTime());
        }

        queryWrapper.orderByDesc(CodegenTableDO::getUpdateTime);
        IPage<CodegenTableDO> page = new Page<>();
        page.setCurrent(pageReqVO.getPageNo());
        page.setSize(pageReqVO.getPageSize());
        selectPage(page, queryWrapper);
        return new PageResult<CodegenTableDO>(page.getRecords(),pageReqVO.getPageNo(),pageReqVO.getPageSize(), (int)page.getTotal());
    }

    default List<CodegenTableDO> selectListByDataSourceConfigId(Long dataSourceConfigId) {
        LambdaQueryWrapper<CodegenTableDO> wrapper = new LambdaQueryWrapper();
        wrapper.eq(CodegenTableDO::getDataSourceConfigId,dataSourceConfigId);
        return selectList(wrapper);
    }

    default List<CodegenTableDO> selectListByTemplateTypeAndMasterTableId(Integer templateType, Long masterTableId) {
        LambdaQueryWrapper<CodegenTableDO> wrapper = new LambdaQueryWrapper();
        wrapper.eq(CodegenTableDO::getTemplateType,templateType);
        wrapper.eq(CodegenTableDO::getMasterTableId,masterTableId);
        return selectList(wrapper);
    }

}
