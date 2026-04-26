package com.bone.lowcode.infra.infrastructure.persistence.mapper;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.CfgTableDataCrossVerifyDO;
import org.apache.ibatis.annotations.Param;

public interface TableDataCrossVerifyMapper extends BaseMapper<CfgTableDataCrossVerifyDO> {

    Integer getMaxSequenceByRelationId(@Param("relationId") Long relationId);
}
