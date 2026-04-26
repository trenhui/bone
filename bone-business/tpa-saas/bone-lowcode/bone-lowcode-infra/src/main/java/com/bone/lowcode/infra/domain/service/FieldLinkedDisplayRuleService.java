package com.bone.lowcode.infra.domain.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bone.lowcode.infra.domain.valueobject.DeletedEnum;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.FieldLinkedDisplayRule;
import com.bone.lowcode.infra.infrastructure.persistence.mapper.FieldLinkedDisplayRuleMapper;
import org.apache.ibatis.executor.BatchResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

@Service
public class FieldLinkedDisplayRuleService {

    @Autowired
    private FieldLinkedDisplayRuleMapper fieldLinkedDisplayRuleMapper;


    public boolean add(FieldLinkedDisplayRule linkedDisplayRule) {
        return fieldLinkedDisplayRuleMapper.insert(linkedDisplayRule) > 0;
    }


    public boolean batchSave(List<FieldLinkedDisplayRule> fieldLinkedDisplayRuleList) {
        List<BatchResult> results = fieldLinkedDisplayRuleMapper.insert(fieldLinkedDisplayRuleList);
        return true;
    }

    public boolean updateById(FieldLinkedDisplayRule rule) {
        return fieldLinkedDisplayRuleMapper.updateById(rule) > 0;
    }

    public boolean deleteById(Long id) {
        return fieldLinkedDisplayRuleMapper.deleteById(id) > 0;
    }

    public boolean deleteByIdList(List<Long> idList) {
        int i = fieldLinkedDisplayRuleMapper.deleteByIds(idList);
        return i > 0;
    }

    public boolean deleteByFieldIdList(List<Long> fieldIdList) {
        LambdaQueryWrapper<FieldLinkedDisplayRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(FieldLinkedDisplayRule::getSelectFieldId, fieldIdList)
                .eq(FieldLinkedDisplayRule::getDeleted, DeletedEnum.UNDELETED.getCode());
        return fieldLinkedDisplayRuleMapper.delete(wrapper) > 0;
    }

    public FieldLinkedDisplayRule getByParam(Long fieldId, Byte datasourceType, String datasourceCode) {
        LambdaQueryWrapper<FieldLinkedDisplayRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FieldLinkedDisplayRule::getSelectFieldId, fieldId)
                .eq(FieldLinkedDisplayRule::getDatasourceType, datasourceType)
                .eq(FieldLinkedDisplayRule::getDatasourceCode, datasourceCode)
                .eq(FieldLinkedDisplayRule::getDeleted, DeletedEnum.UNDELETED.getCode());
        return fieldLinkedDisplayRuleMapper.selectOne(wrapper);
    }

    public List<FieldLinkedDisplayRule> getByFieldIdList(List<Long> fieldIdList) {
        LambdaQueryWrapper<FieldLinkedDisplayRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(FieldLinkedDisplayRule::getSelectFieldId, fieldIdList)
                .eq(FieldLinkedDisplayRule::getDeleted, DeletedEnum.UNDELETED.getCode());
        return fieldLinkedDisplayRuleMapper.selectList(wrapper);
    }

    public List<FieldLinkedDisplayRule> getByPageId(Long pageId) {
        LambdaQueryWrapper<FieldLinkedDisplayRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FieldLinkedDisplayRule::getPageId, pageId)
                .eq(FieldLinkedDisplayRule::getDeleted, DeletedEnum.UNDELETED.getCode());
        return fieldLinkedDisplayRuleMapper.selectList(wrapper);
    }

    public List<FieldLinkedDisplayRule> getByPageIds(Collection<Long> pageIds) {
        LambdaQueryWrapper<FieldLinkedDisplayRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(FieldLinkedDisplayRule::getPageId, pageIds)
                .eq(FieldLinkedDisplayRule::getDeleted, DeletedEnum.UNDELETED.getCode());
        return fieldLinkedDisplayRuleMapper.selectList(wrapper);
    }
}
