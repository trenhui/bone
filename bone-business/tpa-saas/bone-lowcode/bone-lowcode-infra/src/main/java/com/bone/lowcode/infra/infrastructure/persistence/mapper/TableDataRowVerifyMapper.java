package com.bone.lowcode.infra.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.CfgTableDataRowVerifyDO;
import org.apache.ibatis.annotations.Param;

public interface TableDataRowVerifyMapper extends BaseMapper<CfgTableDataRowVerifyDO> {

    Integer getMaxSequenceByTableId(@Param("tableId") Long tableId);
}
