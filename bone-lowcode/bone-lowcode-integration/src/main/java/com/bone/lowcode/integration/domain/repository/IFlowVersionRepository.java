package com.bone.lowcode.integration.domain.repository;

import com.bone.lowcode.integration.domain.model.FlowDefinitionDO;
import com.bone.lowcode.integration.domain.model.FlowVersionDO;

import java.util.List;

public interface IFlowVersionRepository {

    /**
     * 根据ID查询
     * @param id 主键
     * @return 流程数据
     */
    FlowVersionDO getById(Long id);

    List<FlowVersionDO> query(FlowVersionDO flowVersionDO);

    List<FlowVersionDO> queryMaxVersion(FlowVersionDO flowVersionDO);
}
