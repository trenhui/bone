package com.bone.lowcode.infra.application.service;

import com.bone.lowcode.infra.application.convert.FieldConvert;
import com.bone.lowcode.infra.application.convert.FieldLinkageRuleConvert;
import com.bone.lowcode.infra.application.dto.fieldLinkageRule.CreateFieldLinkageRuleDTO;
import com.bone.lowcode.infra.application.dto.fieldLinkageRule.UpdateFieldLinkageRuleDTO;
import com.bone.lowcode.infra.application.vo.field.FieldRuleField;
import com.bone.lowcode.infra.application.vo.fieldLinkageRule.FieldRuleVOByFieldId;
import com.bone.lowcode.infra.application.vo.fieldLinkageRule.FieldRuleVOByPageId;
import com.bone.lowcode.infra.domain.service.FieldLinkageRuleService;
import com.bone.lowcode.infra.domain.service.FieldService;
import com.bone.lowcode.infra.domain.service.ModelService;
import com.bone.lowcode.infra.domain.service.PageService;
import com.bone.lowcode.infra.domain.valueobject.PageTypeEnum;
import com.bone.lowcode.infra.domain.valueobject.RuleTypeEnum;
import com.bone.lowcode.infra.domain.valueobject.ValueTypeEnum;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.CfgFieldDO;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.CfgFieldLinkageRuleDO;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.CfgModelDO;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.CfgPageDO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class FieldLinkageRuleApplicationService {

    @Autowired
    private ModelService modelService;

    @Autowired
    private FieldService fieldService;

    @Autowired
    private FieldLinkageRuleService fieldLinkageRuleService;

    @Autowired
    private PageService pageService;


    public boolean create(CreateFieldLinkageRuleDTO dto) {
        Long fieldId = dto.getFieldId();
        CfgFieldDO fieldDO = fieldService.getDOById(fieldId);
        Long pageId = fieldDO.getPageId();

        CfgFieldLinkageRuleDO fieldLinkageRuleDO = FieldLinkageRuleConvert.createFieldLinkageRuleDTOToDO(dto);
        fieldLinkageRuleDO.setPageId(pageId);
        CfgPageDO pageDO = pageService.getDoById(pageId);
        if (pageDO.getType() == PageTypeEnum.TEMPLATE.getCode()) {
            fieldLinkageRuleDO.setGenericOrExclusive(RuleTypeEnum.GENERIC.getCode());
        } else if (pageDO.getType() == PageTypeEnum.BIZ_IDENTITY.getCode()) {
            fieldLinkageRuleDO.setGenericOrExclusive(RuleTypeEnum.EXCLUSIVE.getCode());
        }

        Integer maxSequence = fieldLinkageRuleService.getMaxSequenceByPageId(pageId);
        fieldLinkageRuleDO.setSequence(maxSequence == null ? 1 : maxSequence + 1);
        boolean flag = fieldLinkageRuleService.save(fieldLinkageRuleDO);
        return flag;
    }

    public List<FieldRuleVOByFieldId> getRuleByFieldId(Long fieldId) {
        List<CfgFieldLinkageRuleDO> ruleDOList = fieldLinkageRuleService.getRuleByFieldId(fieldId);
        List<FieldRuleVOByFieldId> re = new ArrayList<>();
        for (CfgFieldLinkageRuleDO ruleDO : ruleDOList) {
            FieldRuleVOByFieldId vo = FieldLinkageRuleConvert.DOToGetRuleByFieldIdVO(ruleDO);
            CfgFieldDO cfgFieldDO = fieldService.getDOById(ruleDO.getFieldId());
            vo.setComponentType(cfgFieldDO.getComponentType());

            String sourceValueType = ruleDO.getSourceValueType();
            String sourceValue = ruleDO.getSourceValue();
            Object newSourceValue = getValue(sourceValueType, sourceValue);
            vo.setSourceValue(newSourceValue);

            List<Long> targetFieldList = ruleDO.getTargetFieldIdList();
            List<CfgFieldDO> fieldDOList = fieldService.getDOListByIdList(targetFieldList);
            List<FieldRuleField> fieldList = new ArrayList<>();
            for (CfgFieldDO fieldDO : fieldDOList) {
                FieldRuleField field = FieldConvert.DOToFieldRuleField(fieldDO);
                fieldList.add(field);
            }
            vo.setTargetFields(fieldList);

            String targetValueType = ruleDO.getTargetValueType();
            String targetValue = ruleDO.getTargetValue();
            Object newTargetValue = getValue(targetValueType, targetValue);
            vo.setTargetValue(newTargetValue);

            re.add(vo);
        }
        return re;
    }

    public List<FieldRuleVOByPageId> getRuleByPageCodeAndBizIdentityCode(String pageCode, String bizIdentityCode) {
        CfgPageDO pageDO = pageService.getByPageCodeAndIdentityCode(pageCode, bizIdentityCode);
        List<CfgFieldLinkageRuleDO> ruleDOList = fieldLinkageRuleService.getRuleByPageId(pageDO.getId());
        if (CollectionUtils.isEmpty(ruleDOList)) return new ArrayList<>();

        List<Long> fieldIdList = ruleDOList.stream().map(CfgFieldLinkageRuleDO::getFieldId).toList();
        List<CfgFieldDO> fieldDOList = fieldService.getDOListByIdList(fieldIdList);
        Map<Long, CfgFieldDO> fieldDOMap = fieldDOList.stream().collect(Collectors.toMap(CfgFieldDO::getId, i -> i));
        List<Long> modelIdList = fieldDOList.stream().map(CfgFieldDO::getModelId).toList();
        List<CfgModelDO> modelDOList = modelService.getDOListByIdList(modelIdList);
        Map<Long, CfgModelDO> modelDOMap = modelDOList.stream().collect(Collectors.toMap(CfgModelDO::getId, i -> i));

        List<FieldRuleVOByPageId> re = new ArrayList<>();
        for (CfgFieldLinkageRuleDO ruleDO : ruleDOList) {
            FieldRuleVOByPageId vo = FieldLinkageRuleConvert.DOToGetRuleByPageIdVO(ruleDO, fieldDOMap, modelDOMap);
            CfgFieldDO cfgFieldDO = fieldService.getDOById(ruleDO.getFieldId());
            vo.setComponentType(cfgFieldDO.getComponentType());

            String sourceValueType = ruleDO.getSourceValueType();
            String sourceValue = ruleDO.getSourceValue();
            Object newSourceValue = getValue(sourceValueType, sourceValue);
            vo.setSourceValue(newSourceValue);

            List<Long> targetFieldList = ruleDO.getTargetFieldIdList();
            List<CfgFieldDO> temFieldDOList = fieldService.getDOListByIdList(targetFieldList);
            List<FieldRuleField> fieldList = new ArrayList<>();
            for (CfgFieldDO fieldDO : temFieldDOList) {
                FieldRuleField field = FieldConvert.DOToFieldRuleField(fieldDO);
                fieldList.add(field);
            }
            vo.setTargetFields(fieldList);

            String targetValueType = ruleDO.getTargetValueType();
            String targetValue = ruleDO.getTargetValue();
            Object newTargetValue = getValue(targetValueType, targetValue);
            vo.setTargetValue(newTargetValue);
            re.add(vo);
        }
        return re;
    }

    private Object getValue(String sourceValueType, String sourceValue) {
        Object newSourceValue = null;
        if (ValueTypeEnum.DYNAMIC.getValue().equals(sourceValueType)) {
            Long temFieldId = Long.valueOf(sourceValue);
            CfgFieldDO fieldDO = fieldService.getDOById(temFieldId);
            newSourceValue = FieldConvert.DOToFieldRuleField(fieldDO);
        } else if (ValueTypeEnum.FIXED.getValue().equals(sourceValueType)) {
            newSourceValue = sourceValue;
        }
        return newSourceValue;
    }

    public boolean update(UpdateFieldLinkageRuleDTO dto) {
        CfgFieldLinkageRuleDO linkageRuleDO = FieldLinkageRuleConvert.updateFieldLinkageRuleDTOToDO(dto);
        return fieldLinkageRuleService.updateById(linkageRuleDO);
    }

    public boolean deleteById(Long id) {
        return fieldLinkageRuleService.deleteById(id);
    }
}
