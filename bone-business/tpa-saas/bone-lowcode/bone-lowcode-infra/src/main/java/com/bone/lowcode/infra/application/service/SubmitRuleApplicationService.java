package com.bone.lowcode.infra.application.service;

import com.bone.lowcode.infra.application.convert.SubmitRuleConvert;
import com.bone.lowcode.infra.application.dto.submitRule.CreateSubmitRuleDTO;
import com.bone.lowcode.infra.application.dto.submitRule.UpdateSubmitRuleDTO;
import com.bone.lowcode.infra.application.vo.submitRule.FieldOfSubmitRule;
import com.bone.lowcode.infra.application.vo.submitRule.GetSubmitRuleByPageVO;
import com.bone.lowcode.infra.domain.service.FieldService;
import com.bone.lowcode.infra.domain.service.PageService;
import com.bone.lowcode.infra.domain.service.SubmitRuleService;
import com.bone.lowcode.infra.domain.valueobject.ValueTypeEnum;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.CfgFieldDO;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.CfgPageDO;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.CfgSubmitRuleDO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SubmitRuleApplicationService {

    @Autowired
    private SubmitRuleService submitRuleService;

    @Autowired
    private PageService pageService;

    @Autowired
    private FieldService fieldService;

    public boolean create(CreateSubmitRuleDTO dto) {
        CfgSubmitRuleDO submitRuleDO = SubmitRuleConvert.createSubmitRuleDTOToDO(dto);
        String pageCode = dto.getPageCode();
        String bizIdentityCode = dto.getBizIdentityCode();
        CfgPageDO pageDO = pageService.getByPageCodeAndIdentityCode(pageCode, bizIdentityCode);
        submitRuleDO.setPageId(pageDO.getId());
        submitRuleDO.setGenericOrExclusive(pageDO.getType());

        Integer maxSequence = submitRuleService.getMaxSequenceByPageId(pageDO.getId());
        submitRuleDO.setSequence(maxSequence == null ? 1 : maxSequence + 1);
        boolean flag = submitRuleService.save(submitRuleDO);
        return flag;
    }

    public List<GetSubmitRuleByPageVO> getRuleByPage(String pageCode, String bizIdentityCode) {
        CfgPageDO pageDO = pageService.getByPageCodeAndIdentityCode(pageCode, bizIdentityCode);
        Long pageId = pageDO.getId();
        List<CfgSubmitRuleDO> submitRuleDOList = submitRuleService.getByPageId(pageId);

        List<GetSubmitRuleByPageVO> voList = new ArrayList<>();
        for (CfgSubmitRuleDO submitRuleDO : submitRuleDOList) {
            GetSubmitRuleByPageVO vo = SubmitRuleConvert.DOToGetSubmitRuleByPageVO(submitRuleDO);
            List<Long> fieldIds = submitRuleDO.getFieldIds();
            List<CfgFieldDO> fieldDOList = fieldService.getDOListByIdList(fieldIds);
            Map<Long, CfgFieldDO> fieldDOMap = fieldDOList.stream().collect(Collectors.toMap(CfgFieldDO::getId, i -> i));
            List<CfgFieldDO> fieldDOListNew = fieldIds.stream().map(fieldDOMap::get).toList();
            List<FieldOfSubmitRule> fieldList = new ArrayList<>();
            for (CfgFieldDO fieldDO : fieldDOListNew) {
                FieldOfSubmitRule field = new FieldOfSubmitRule();
                field.setId(fieldDO.getId().toString());
                field.setBizCode(fieldDO.getBizCode());
                field.setBizName(fieldDO.getBizName());
                fieldList.add(field);
            }
            vo.setFieldList(fieldList);

            if (ValueTypeEnum.DYNAMIC.getValue().equals(submitRuleDO.getValueType())) {
                Long fieldId = Long.valueOf(submitRuleDO.getValue());
                CfgFieldDO fieldDO = fieldService.getDOById(fieldId);
                FieldOfSubmitRule field = new FieldOfSubmitRule();
                field.setId(fieldDO.getId().toString());
                field.setBizCode(fieldDO.getBizCode());
                field.setBizName(fieldDO.getBizName());
                vo.setValue(field);
            } else if (ValueTypeEnum.FIXED.getValue().equals(submitRuleDO.getValueType())) {
                vo.setValue(submitRuleDO.getValue());
            }
            voList.add(vo);
        }
        return voList;
    }

    public boolean update(UpdateSubmitRuleDTO dto) {
        CfgSubmitRuleDO submitRuleDO = SubmitRuleConvert.updateSubmitRuleDTOToDO(dto);
        boolean flag = submitRuleService.updateById(submitRuleDO);
        return flag;
    }

    public boolean deleteById(Long id) {
        return submitRuleService.deleteById(id);
    }
}
