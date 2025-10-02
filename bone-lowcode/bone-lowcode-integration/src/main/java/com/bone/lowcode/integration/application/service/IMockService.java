package com.bone.lowcode.integration.application.service;

import com.bone.lowcode.integration.application.dto.MockDTO;
import org.apache.ibatis.annotations.Param;

public interface IMockService {

    MockDTO getByMockKey(@Param("mockKey") String mockKey);
}
