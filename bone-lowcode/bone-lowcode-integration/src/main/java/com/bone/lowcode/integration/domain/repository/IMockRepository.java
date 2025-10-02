package com.bone.lowcode.integration.domain.repository;

import com.bone.lowcode.integration.domain.model.MockDO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface IMockRepository {

    MockDO getById(@Param("id") Long id);

    List<MockDO> queryByList(MockDO mockDO);

    MockDO getByMockKey(@Param("mockKey") String mockKey);
}

