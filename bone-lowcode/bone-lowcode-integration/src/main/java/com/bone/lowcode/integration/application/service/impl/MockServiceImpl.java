package com.bone.lowcode.integration.application.service.impl;

import com.bone.lowcode.integration.application.dto.MockDTO;
import com.bone.lowcode.integration.application.service.IMockService;
import com.bone.lowcode.integration.domain.repository.IMockRepository;
import com.bone.lowcode.integration.infrastructure.converter.MockConverter;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
public class MockServiceImpl implements IMockService {
    @Resource
    private IMockRepository mockRepository;

    @Override
    public MockDTO getByMockKey(String mockKey) {
        return MockConverter.convert(mockRepository.getByMockKey(mockKey));
    }
}
