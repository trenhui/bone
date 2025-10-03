package com.bone.integration.infrastructure.mapper;


import com.bone.integration.domain.model.FlowVersionDO;
import org.apache.ibatis.annotations.Param;

import java.util.List;


public interface FlowVersionMapper {

    FlowVersionDO getById(@Param("id") Long id);

    List<FlowVersionDO> query(FlowVersionDO flowVersionDO);

    List<FlowVersionDO> queryMaxVersion(FlowVersionDO flowVersionDO);

}
