package com.bone.integration.application.service.impl;

import com.bone.integration.application.dto.MockDTO;
import com.bone.integration.application.service.IMockService;
import com.bone.integration.domain.repository.IMockRepository;
import com.bone.integration.infrastructure.converter.MockConverter;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
public class MockServiceImpl implements IMockService {
    @Resource
    private IMockRepository mockRepository;

    @Override
    public MockDTO getByMockKey(String mockKey) {
        return mockRepository.getByMockKey(mockKey) != null ? 
               MockConverter.INSTANCE.toDto(mockRepository.getByMockKey(mockKey)) : null;
    }
}
