package com.bone.lowcode.infra.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.SysBizIdentityDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 业务虚拟对象表 Mapper 接口
 *
 * @author fhmdf
 * @since 2024-07-31
 */
@Mapper
public interface SysBizIdentityMapper extends BaseMapper<SysBizIdentityDO> {}
