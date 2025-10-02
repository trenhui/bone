package com.bone.lowcode.integration.infrastructure.repository;

import com.bone.lowcode.integration.domain.model.MockDO;
import com.bone.lowcode.integration.domain.repository.IMockRepository;
import com.bone.lowcode.integration.infrastructure.mapper.MockMapper;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

@Component
public class MockRepositoryImpl implements IMockRepository {
    @Resource
    private MockMapper mockMapper;

    @Override
    public MockDO getById(Long id) {
        return mockMapper.getById(id);
    }

    @Override
    public List<MockDO> queryByList(MockDO mockDO) {
        return mockMapper.queryByList(mockDO);
    }

    @Override
    public MockDO getByMockKey(String mockKey) {
        return mockMapper.getByMockKey(mockKey);
    }
}
