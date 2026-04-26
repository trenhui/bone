package com.bone.lowcode.infra.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.CfgSubmitRuleDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SubmitRuleMapper extends BaseMapper<CfgSubmitRuleDO> {

    Integer batchSaveWithId(@Param("submitRuleDOList") List<CfgSubmitRuleDO> submitRuleDOList);

    Integer getMaxSequenceByPageId(@Param("pageId") Long pageId);
}
