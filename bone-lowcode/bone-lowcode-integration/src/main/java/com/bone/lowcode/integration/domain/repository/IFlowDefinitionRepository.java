package com.bone.lowcode.integration.domain.repository;

import com.bone.lowcode.integration.domain.model.FlowDefinitionDO;

import java.util.List;

public interface IFlowDefinitionRepository {

    /**
     * 根据ID查询
     * @param id 主键
     * @return 流程数据
     */
    FlowDefinitionDO getById(Long id);

    List<FlowDefinitionDO> query(FlowDefinitionDO flowDefinitionDO);
}
