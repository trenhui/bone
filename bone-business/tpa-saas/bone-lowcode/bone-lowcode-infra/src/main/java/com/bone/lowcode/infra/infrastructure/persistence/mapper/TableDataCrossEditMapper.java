package com.bone.lowcode.infra.infrastructure.persistence.mapper;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.CfgTableDataCrossEditDO;
import org.apache.ibatis.annotations.Param;

public interface TableDataCrossEditMapper extends BaseMapper<CfgTableDataCrossEditDO> {

    Integer getMaxSequenceByRelationId(@Param("relationId") Long relationId);

    Integer getMaxSequenceByPageId(@Param("pageId") Long pageId);
}
