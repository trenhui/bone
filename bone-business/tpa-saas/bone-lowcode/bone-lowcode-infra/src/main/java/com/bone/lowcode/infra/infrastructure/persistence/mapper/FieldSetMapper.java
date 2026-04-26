package com.bone.lowcode.infra.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.CfgFieldsetDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface FieldSetMapper extends BaseMapper<CfgFieldsetDO> {
    Integer batchSaveWithId(@Param("fieldsetDOList") List<CfgFieldsetDO> fieldsetDOList);
}
