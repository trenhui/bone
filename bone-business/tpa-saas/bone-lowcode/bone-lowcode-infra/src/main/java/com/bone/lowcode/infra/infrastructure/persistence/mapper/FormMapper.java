package com.bone.lowcode.infra.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.CfgFormDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 页面表单 Mapper 接口
 *
 * @author fhmdf
 * @since 2024-07-25
 */
@Mapper
public interface FormMapper extends BaseMapper<CfgFormDO> {}
