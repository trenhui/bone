package com.bone.lowcode.infra.domain.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bone.lowcode.infra.domain.valueobject.DeletedEnum;
import com.bone.lowcode.infra.domain.valueobject.StatusEnum;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.CfgFieldTableRule;
import com.bone.lowcode.infra.infrastructure.persistence.mapper.CfgFieldTableRuleMapper;
import org.apache.ibatis.executor.BatchResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

@Service
public class FieldTableRuleService {

    @Autowired
    private CfgFieldTableRuleMapper fieldTableRuleMapper;

    public boolean save(CfgFieldTableRule fieldTableRule) {
        return fieldTableRuleMapper.insert(fieldTableRule) > 0;
    }

    public boolean batchSave(List<CfgFieldTableRule> fieldTableRuleList) {
        List<BatchResult> resultList = fieldTableRuleMapper.insert(fieldTableRuleList);
        return true;
    }

    public boolean updateById(CfgFieldTableRule fieldTableRule) {
        return fieldTableRuleMapper.updateById(fieldTableRule) > 0;
    }

    public boolean deleteById(Long id) {
        return fieldTableRuleMapper.deleteById(id) > 0;
    }

    public boolean deleteByIdList(List<Long> idList) {
        return fieldTableRuleMapper.deleteByIds(idList) > 0;
    }

    public List<CfgFieldTableRule> getByPageId(Long pageId) {
        LambdaQueryWrapper<CfgFieldTableRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CfgFieldTableRule::getPageId, pageId)
                .eq(CfgFieldTableRule::getDeleted, DeletedEnum.UNDELETED.getCode());
        return fieldTableRuleMapper.selectList(wrapper);
    }

    public List<CfgFieldTableRule> getEnableByPageId(Long pageId) {
        LambdaQueryWrapper<CfgFieldTableRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CfgFieldTableRule::getPageId, pageId)
                .eq(CfgFieldTableRule::getStatus, StatusEnum.YES.getCode())
                .eq(CfgFieldTableRule::getDeleted, DeletedEnum.UNDELETED.getCode());
        return fieldTableRuleMapper.selectList(wrapper);
    }

    public List<CfgFieldTableRule> getByPageIds(Collection<Long> pageIds) {
        LambdaQueryWrapper<CfgFieldTableRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(CfgFieldTableRule::getPageId, pageIds)
                .eq(CfgFieldTableRule::getDeleted, DeletedEnum.UNDELETED.getCode());
        return fieldTableRuleMapper.selectList(wrapper);
    }

    public List<CfgFieldTableRule> getByFieldId(Long fieldId) {
        LambdaQueryWrapper<CfgFieldTableRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CfgFieldTableRule::getFieldId, fieldId)
                .eq(CfgFieldTableRule::getDeleted, DeletedEnum.UNDELETED.getCode());
        return fieldTableRuleMapper.selectList(wrapper);
    }
}
