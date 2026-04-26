package com.bone.lowcode.infra.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.CfgReleasedPageDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 发布页面 Mapper 接口
 *
 * @author fhmdf
 * @since 2024-07-25
 */
@Mapper
public interface ReleasedPageMapper extends BaseMapper<CfgReleasedPageDO> {}
