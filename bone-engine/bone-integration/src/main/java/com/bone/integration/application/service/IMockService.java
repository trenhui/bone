package com.bone.integration.application.service;

import com.bone.integration.application.dto.MockDTO;
import org.apache.ibatis.annotations.Param;

public interface IMockService {

    MockDTO getByMockKey(@Param("mockKey") String mockKey);
}
