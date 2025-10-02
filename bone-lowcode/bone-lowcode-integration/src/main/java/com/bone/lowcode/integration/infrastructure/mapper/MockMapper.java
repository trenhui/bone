package com.bone.lowcode.integration.infrastructure.mapper;


import com.bone.lowcode.integration.domain.model.MockDO;
import org.apache.ibatis.annotations.Param;

import java.util.List;


public interface MockMapper {

    MockDO getById(@Param("id") Long id);

    List<MockDO> queryByList(MockDO mockDO);

    MockDO getByMockKey(@Param("mockKey") String mockKey);

}
