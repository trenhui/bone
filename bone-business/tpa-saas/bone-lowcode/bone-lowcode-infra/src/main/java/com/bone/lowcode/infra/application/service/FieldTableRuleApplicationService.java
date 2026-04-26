package com.bone.lowcode.infra.application.service;

import com.bone.core.exception.ServiceException;
import com.bone.lowcode.infra.application.convert.FieldConvert;
import com.bone.lowcode.infra.application.convert.TableConvert;
import com.bone.lowcode.infra.application.dto.fieldTableRule.CreateFieldTableRuleDTO;
import com.bone.lowcode.infra.application.dto.fieldTableRule.UpdateFieldTableRuleDTO;
import com.bone.lowcode.infra.application.vo.fieldTableRule.FieldTableRuleVO;
import com.bone.lowcode.infra.application.vo.simple.Field;
import com.bone.lowcode.infra.application.vo.simple.Table;
import com.bone.lowcode.infra.domain.service.*;
import com.bone.lowcode.infra.domain.valueobject.PageTypeEnum;
import com.bone.lowcode.infra.domain.valueobject.RuleTypeEnum;
import com.bone.lowcode.infra.domain.valueobject.ValueTypeEnum;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.*;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class FieldTableRuleApplicationService {

    @Autowired
    private FieldTableRuleService fieldTableRuleService;

    @Autowired
    private FieldService fieldService;

    @Autowired
    private TableService tableService;

    @Autowired
    private PageService pageService;

    @Autowired
    private ModelService modelService;


    public boolean create(CreateFieldTableRuleDTO param) {
        Long fieldId = param.getFieldId();
        CfgFieldDO fieldDO = fieldService.getDOById(fieldId);
        if (fieldDO == null) {
            throw new ServiceException(500, "目标字段不存在");
        }
        Long pageId = fieldDO.getPageId();
        CfgPageDO pageDO = pageService.getDoById(pageId);

        CfgFieldTableRule fieldTableRule = new CfgFieldTableRule();
        BeanUtils.copyProperties(param, fieldTableRule);
        fieldTableRule.setPageId(pageId);
        fieldTableRule.setCreateTime(new Date());
        if (pageDO.getType() == PageTypeEnum.TEMPLATE.getCode()) {
            fieldTableRule.setGenericOrExclusive(RuleTypeEnum.GENERIC.getCode());
        } else if (pageDO.getType() == PageTypeEnum.BIZ_IDENTITY.getCode()) {
            fieldTableRule.setGenericOrExclusive(RuleTypeEnum.EXCLUSIVE.getCode());
        }
        return fieldTableRuleService.save(fieldTableRule);
    }

    public List<FieldTableRuleVO> getRuleByFieldId(Long fieldId) {
        CfgFieldDO fieldDO = fieldService.getDOById(fieldId);
        if (fieldDO == null) {
            throw new ServiceException(500, "目标字段不存在");
        }

        List<CfgFieldTableRule> ruleList = fieldTableRuleService.getByFieldId(fieldId);
        return getFieldTableRuleVOS(ruleList);
    }

    public List<FieldTableRuleVO> getFieldTableRuleVOS(List<CfgFieldTableRule> ruleList) {
        if (CollectionUtils.isEmpty(ruleList)) {
            return List.of();
        }

        Set<Long> fieldIdSet = new HashSet<>();
        Set<Long> tableIdSet = new HashSet<>();
        for (CfgFieldTableRule rule : ruleList) {
            fieldIdSet.add(rule.getFieldId());
            if (ValueTypeEnum.DYNAMIC.getValue().equals(rule.getSourceValueType())) {
                fieldIdSet.add(Long.valueOf(rule.getSourceValue()));
            }
            tableIdSet.add(rule.getTableId());
        }
        List<CfgFieldDO> fieldDOList = fieldService.getDOListByIdList(new ArrayList<>(fieldIdSet));
        Map<Long, CfgFieldDO> fieldMap = fieldDOList.stream().collect(Collectors.toMap(CfgFieldDO::getId, i -> i));
        List<CfgTableDO> tableDOList = tableService.getByIds(tableIdSet);
        Map<Long, CfgTableDO> tableMap = tableDOList.stream().collect(Collectors.toMap(CfgTableDO::getId, i -> i));

        List<Long> modelIdList = fieldDOList.stream().map(CfgFieldDO::getModelId).toList();
        List<CfgModelDO> modelDOList = modelService.getDOListByIdList(modelIdList);
        Map<Long, CfgModelDO> modelMap = modelDOList.stream().collect(Collectors.toMap(CfgModelDO::getId, i -> i));
        Map<Long, String> modelNameMap = new HashMap<>();
        for (CfgFieldDO fieldDO : fieldDOList) {
            Long modelId = fieldDO.getModelId();
            CfgModelDO modelDO = modelMap.get(modelId);
            if (modelDO != null) {
                modelNameMap.put(fieldDO.getId(), modelDO.getName());
            }
        }

        List<FieldTableRuleVO> voList = ruleList.stream().map(rule -> {
            FieldTableRuleVO vo = new FieldTableRuleVO();
            BeanUtils.copyProperties(rule, vo);
            vo.setId(rule.getId().toString());

            Long currentFieldId = rule.getFieldId();
            CfgFieldDO currentFieldDO = fieldMap.get(currentFieldId);
            Field field = FieldConvert.fieldDOToSimpleField(currentFieldDO);
            vo.setCurrentField(field);
            if (ValueTypeEnum.DYNAMIC.getValue().equals(rule.getSourceValueType())) {
                CfgFieldDO dynamicFieldDO = fieldMap.get(Long.valueOf(rule.getSourceValue()));
                Field dynamicField = FieldConvert.fieldDOToSimpleField(dynamicFieldDO);
                vo.setSourceValue(dynamicField);
            }
            CfgTableDO tableDO = tableMap.get(rule.getTableId());
            Table table = TableConvert.tableDOToSimpleTable(tableDO);
            vo.setTargetTable(table);
            vo.setModelName(modelNameMap.get(rule.getFieldId()));
            return vo;
        }).toList();
        return voList;
    }

    public boolean update(UpdateFieldTableRuleDTO param) {
        CfgFieldTableRule fieldTableRule = new CfgFieldTableRule();
        BeanUtils.copyProperties(param, fieldTableRule);
        fieldTableRule.setUpdateTime(new Date());
        boolean flag = fieldTableRuleService.updateById(fieldTableRule);
        return flag;
    }

    public boolean deleteById(Long id) {
        return fieldTableRuleService.deleteById(id);
    }

    public List<FieldTableRuleVO> getRuleByPageCodeAndBizIdentityCode(String pageCode, String bizIdentityCode) {
        CfgPageDO pageDO = pageService.getByPageCodeAndIdentityCode(pageCode, bizIdentityCode);
        if (pageDO == null) {
            throw new ServiceException(500, "目标页面不存在,pageCode:" + pageCode + ", bizIdentityCode:" + bizIdentityCode);
        }
        List<CfgFieldTableRule> ruleList = fieldTableRuleService.getByPageId(pageDO.getId());
        return getFieldTableRuleVOS(ruleList);
    }
}
