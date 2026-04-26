package com.bone.lowcode.infra.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.CfgModelDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 数据模型信息 Mapper 接口
 *
 * @author fhmdf
 * @since 2024-07-25
 */
@Mapper
public interface ModelMapper extends BaseMapper<CfgModelDO> {
    Integer batchSaveWithId(@Param("modelDOList") List<CfgModelDO> modelDOList);
}
