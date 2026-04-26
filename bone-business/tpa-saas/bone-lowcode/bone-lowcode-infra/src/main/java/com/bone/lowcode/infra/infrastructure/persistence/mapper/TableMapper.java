package com.bone.lowcode.infra.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.CfgTableDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 表格定义 Mapper 接口
 *
 * @author fhmdf
 * @since 2024-07-25
 */
@Mapper
public interface TableMapper extends BaseMapper<CfgTableDO> {
    Integer batchSaveWithId(@Param("tableDOList") List<CfgTableDO> tableDOList);
}
