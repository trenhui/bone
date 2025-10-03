package com.bone.tools.codegen.domain.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bone.tools.codegen.domain.entity.CodegenColumnDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface CodegenColumnMapper extends BaseMapper<CodegenColumnDO> {

    default List<CodegenColumnDO> selectListByTableId(Long tableId) {
        return selectList(new LambdaQueryWrapper<CodegenColumnDO>()
                .eq(CodegenColumnDO::getTableId, tableId)
                .orderByAsc(CodegenColumnDO::getId));
    }

    default void deleteListByTableId(Long tableId) {
        delete(new LambdaQueryWrapper<CodegenColumnDO>()
                .eq(CodegenColumnDO::getTableId, tableId));
    }

}
