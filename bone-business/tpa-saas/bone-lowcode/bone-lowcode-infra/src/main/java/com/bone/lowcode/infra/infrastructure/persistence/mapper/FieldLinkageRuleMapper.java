package com.bone.lowcode.infra.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.CfgFieldLinkageRuleDO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface FieldLinkageRuleMapper extends BaseMapper<CfgFieldLinkageRuleDO> {

    Integer batchSaveWithId(@Param("fieldRuleDOList") List<CfgFieldLinkageRuleDO> fieldRuleDOList);

    Integer selectMaxSequenceByPageId(@Param("pageId") Long pageId);
}
