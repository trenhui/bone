package com.bone.integration.infrastructure.repository;

import com.bone.integration.domain.model.FlowVersionDO;
import com.bone.integration.domain.repository.IFlowVersionRepository;
import com.bone.integration.infrastructure.mapper.FlowVersionMapper;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

@Component
public class FlowVersionRepositoryImpl implements IFlowVersionRepository {
    @Resource
    private FlowVersionMapper flowVersionMapper;

    @Override
    public FlowVersionDO getById(Long id) {
        return flowVersionMapper.getById(id);
    }

    @Override
    public List<FlowVersionDO> query(FlowVersionDO flowVersionDO) {
        return flowVersionMapper.query(flowVersionDO);
    }

    @Override
    public List<FlowVersionDO> queryMaxVersion(FlowVersionDO flowVersionDO) {
        return flowVersionMapper.queryMaxVersion(flowVersionDO);
    }
}
