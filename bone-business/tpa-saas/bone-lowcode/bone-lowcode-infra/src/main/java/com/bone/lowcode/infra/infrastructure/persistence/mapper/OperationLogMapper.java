package com.bone.lowcode.infra.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.OperationLog;
import org.apache.ibatis.annotations.Mapper;


@Mapper
public interface OperationLogMapper extends BaseMapper<OperationLog> {
}
