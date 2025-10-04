package com.bone.integration.infrastructure.repository;

import com.bone.integration.domain.model.FlowDefinitionDO;
import com.bone.integration.domain.repository.IFlowDefinitionRepository;
import com.bone.integration.infrastructure.mapper.FlowDefinitionMapper;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

@Component
public class FlowDefinitionRepositoryImpl implements IFlowDefinitionRepository {
    @Resource
    private FlowDefinitionMapper flowDefinitionMapper;

    @Override
    public FlowDefinitionDO getById(Long id) {
        return flowDefinitionMapper.getById(id);
    }

    @Override
    public List<FlowDefinitionDO> query(FlowDefinitionDO flowDefinitionDO) {
        return flowDefinitionMapper.query(flowDefinitionDO);
    }
}
