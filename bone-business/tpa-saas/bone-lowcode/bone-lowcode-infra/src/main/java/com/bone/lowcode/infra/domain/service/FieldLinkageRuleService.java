package com.bone.lowcode.infra.domain.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bone.lowcode.infra.domain.valueobject.DeletedEnum;
import com.bone.lowcode.infra.domain.valueobject.StatusEnum;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.CfgFieldLinkageRuleDO;
import com.bone.lowcode.infra.infrastructure.persistence.mapper.FieldLinkageRuleMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

@Service
public class FieldLinkageRuleService {

    @Autowired
    private FieldLinkageRuleMapper fieldLinkageRuleMapper;


    public boolean save(CfgFieldLinkageRuleDO fieldLinkageRuleDO) {
        return fieldLinkageRuleMapper.insert(fieldLinkageRuleDO) > 0;
    }

    public boolean batchSave(List<CfgFieldLinkageRuleDO> fieldRuleDOList) {
        fieldLinkageRuleMapper.insert(fieldRuleDOList);
        return true;
    }

    public List<CfgFieldLinkageRuleDO> getRuleByFieldId(Long fieldId) {
        LambdaQueryWrapper<CfgFieldLinkageRuleDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CfgFieldLinkageRuleDO::getFieldId, fieldId)
                .eq(CfgFieldLinkageRuleDO::getDeleted, DeletedEnum.UNDELETED.getCode());
        List<CfgFieldLinkageRuleDO> ruleDOList = fieldLinkageRuleMapper.selectList(wrapper);
        return ruleDOList;
    }


    public List<CfgFieldLinkageRuleDO> getRuleByPageId(Long pageId) {
        LambdaQueryWrapper<CfgFieldLinkageRuleDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CfgFieldLinkageRuleDO::getPageId, pageId)
                .eq(CfgFieldLinkageRuleDO::getDeleted, DeletedEnum.UNDELETED.getCode());
        List<CfgFieldLinkageRuleDO> ruleDOList = fieldLinkageRuleMapper.selectList(wrapper);
        return ruleDOList;
    }

    public List<CfgFieldLinkageRuleDO> getByPageIds(Collection<Long> pageIds) {
        LambdaQueryWrapper<CfgFieldLinkageRuleDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(CfgFieldLinkageRuleDO::getPageId, pageIds)
                .eq(CfgFieldLinkageRuleDO::getDeleted, DeletedEnum.UNDELETED.getCode());
        return fieldLinkageRuleMapper.selectList(wrapper);
    }

    public Integer batchSaveWithId(List<CfgFieldLinkageRuleDO> fieldRuleDOList) {
        return fieldLinkageRuleMapper.batchSaveWithId(fieldRuleDOList);
    }


    public boolean updateById(CfgFieldLinkageRuleDO linkageRuleDO) {
        return fieldLinkageRuleMapper.updateById(linkageRuleDO) > 0;
    }


    public List<CfgFieldLinkageRuleDO> getEnableByPageId(Long pageId) {
        LambdaQueryWrapper<CfgFieldLinkageRuleDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CfgFieldLinkageRuleDO::getPageId, pageId)
                .eq(CfgFieldLinkageRuleDO::getStatus, StatusEnum.YES.getCode())
                .eq(CfgFieldLinkageRuleDO::getDeleted, DeletedEnum.UNDELETED.getCode());
        List<CfgFieldLinkageRuleDO> ruleDOList = fieldLinkageRuleMapper.selectList(wrapper);
        return ruleDOList;
    }

    public boolean deleteById(Long id) {
        return fieldLinkageRuleMapper.deleteById(id) > 0;
    }

    public boolean deleteByIdList(List<Long> idList) {
        int i = fieldLinkageRuleMapper.deleteByIds(idList);
        return i > 0;
    }

    //========================================以下是手写sql的方法==============================================
    public Integer getMaxSequenceByPageId(Long pageId) {
        return fieldLinkageRuleMapper.selectMaxSequenceByPageId(pageId);
    }
}
