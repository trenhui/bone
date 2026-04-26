package com.bone.lowcode.infra.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.CfgBlockDO;
import org.apache.ibatis.annotations.Mapper;


@Mapper
public interface BlockMapper extends BaseMapper<CfgBlockDO> {}
