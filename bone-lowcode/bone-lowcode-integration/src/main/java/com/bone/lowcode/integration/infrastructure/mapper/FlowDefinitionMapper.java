package com.bone.lowcode.integration.infrastructure.mapper;


import com.bone.lowcode.integration.domain.model.FlowDefinitionDO;
import org.apache.ibatis.annotations.Param;

import java.util.List;


public interface FlowDefinitionMapper {

    FlowDefinitionDO getById(@Param("id") Long id);

    List<FlowDefinitionDO> query(FlowDefinitionDO flowDefinitionDO);
}
