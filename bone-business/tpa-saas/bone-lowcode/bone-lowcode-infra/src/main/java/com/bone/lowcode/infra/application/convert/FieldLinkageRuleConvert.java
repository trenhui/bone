package com.bone.lowcode.infra.application.convert;


import com.bone.lowcode.infra.application.dto.fieldLinkageRule.CreateFieldLinkageRuleDTO;
import com.bone.lowcode.infra.application.dto.fieldLinkageRule.UpdateFieldLinkageRuleDTO;
import com.bone.lowcode.infra.application.vo.fieldLinkageRule.FieldRuleVOByFieldId;
import com.bone.lowcode.infra.application.vo.fieldLinkageRule.FieldRuleVOByPageId;
import com.bone.lowcode.infra.application.vo.page.pageJson.FieldLinkageRule;
import com.bone.lowcode.infra.domain.valueobject.DeletedEnum;
import com.bone.lowcode.infra.domain.valueobject.StatusEnum;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.CfgFieldDO;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.CfgFieldLinkageRuleDO;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.CfgModelDO;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.Map;

@Component
public class FieldLinkageRuleConvert {

    public static CfgFieldLinkageRuleDO createFieldLinkageRuleDTOToDO(CreateFieldLinkageRuleDTO dto) {
        CfgFieldLinkageRuleDO ruleDO = new CfgFieldLinkageRuleDO();
        BeanUtils.copyProperties(dto, ruleDO);
        List<Long> targetFields = dto.getTargetFields();
        ruleDO.setTargetFieldIdList(targetFields);
        ruleDO.setStatus(StatusEnum.YES.getCode());
        ruleDO.setCreateBy(null); //
        ruleDO.setCreateTime(new Date());
        ruleDO.setDeleted(DeletedEnum.UNDELETED.getCode());
        return ruleDO;
    }

    public static FieldRuleVOByFieldId DOToGetRuleByFieldIdVO(CfgFieldLinkageRuleDO ruleDO) {
        FieldRuleVOByFieldId vo = new FieldRuleVOByFieldId();
        BeanUtils.copyProperties(ruleDO, vo);
        vo.setId(ruleDO.getId().toString());
        vo.setFieldId(ruleDO.getFieldId().toString());
        vo.setPageId(ruleDO.getPageId().toString());
        return vo;
    }

    public static FieldLinkageRule DOToFieldLinkageRule(CfgFieldLinkageRuleDO ruleDO) {
        FieldLinkageRule linkageRule = new FieldLinkageRule();
        BeanUtils.copyProperties(ruleDO, linkageRule);
        linkageRule.setId(ruleDO.getId().toString());
        linkageRule.setFieldId(ruleDO.getFieldId().toString());
        linkageRule.setPageId(ruleDO.getPageId().toString());
        return linkageRule;
    }

    public static FieldRuleVOByPageId DOToGetRuleByPageIdVO(CfgFieldLinkageRuleDO ruleDO, Map<Long, CfgFieldDO> fieldDOMap, Map<Long, CfgModelDO> modelDOMap) {
        FieldRuleVOByPageId vo = new FieldRuleVOByPageId();
        BeanUtils.copyProperties(ruleDO, vo);
        vo.setId(ruleDO.getId().toString());
        vo.setFieldId(ruleDO.getFieldId().toString());
        vo.setTargetFields(ruleDO.getTargetFieldIdList());
        vo.setPageId(ruleDO.getPageId().toString());
        CfgFieldDO fieldDO = fieldDOMap.get(ruleDO.getFieldId());
        vo.setBizName(fieldDO.getBizName());
        CfgModelDO modelDO = modelDOMap.get(fieldDO.getModelId());
        vo.setModelName(modelDO.getName());
        return vo;
    }

    public static CfgFieldLinkageRuleDO updateFieldLinkageRuleDTOToDO(UpdateFieldLinkageRuleDTO dto) {
        CfgFieldLinkageRuleDO ruleDO = new CfgFieldLinkageRuleDO();
        BeanUtils.copyProperties(dto, ruleDO);
        if (dto.getTargetFields() != null) {
            ruleDO.setTargetFieldIdList(dto.getTargetFields());
        }
        return ruleDO;
    }
}
