package com.bone.lowcode.infra.infrastructure.persistence.mapper;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.CfgTableDataRowEditDO;
import org.apache.ibatis.annotations.Param;


public interface TableDataRowEditMapper extends BaseMapper<CfgTableDataRowEditDO> {

    Integer getMaxSequenceByTableId(@Param("tableId") Long tableId);

    Integer getMaxSequenceByPageId(@Param("pageId") Long pageId);
}
