package com.bone.lowcode.infra.domain.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bone.lowcode.infra.domain.valueobject.DeletedEnum;
import com.bone.lowcode.infra.domain.valueobject.StatusEnum;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.CfgSubmitRuleDO;
import com.bone.lowcode.infra.infrastructure.persistence.mapper.SubmitRuleMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

@Service
public class SubmitRuleService {

    @Autowired
    private SubmitRuleMapper submitRuleMapper;


    public boolean save(CfgSubmitRuleDO submitRuleDO) {
        return submitRuleMapper.insert(submitRuleDO) > 0;
    }

    public boolean batchSave(List<CfgSubmitRuleDO> submitRuleDOList) {
        submitRuleMapper.insert(submitRuleDOList);
        return true;
    }

    public List<CfgSubmitRuleDO> getByPageId(Long pageId) {
        LambdaQueryWrapper<CfgSubmitRuleDO> wrapper = new LambdaQueryWrapper<CfgSubmitRuleDO>()
                .eq(CfgSubmitRuleDO::getPageId, pageId)
                .eq(CfgSubmitRuleDO::getDeleted, DeletedEnum.UNDELETED.getCode())
                .orderByAsc(CfgSubmitRuleDO::getCreateTime);
        return submitRuleMapper.selectList(wrapper);
    }

    public List<CfgSubmitRuleDO> getByPageIds(Collection<Long> pageIds) {
        LambdaQueryWrapper<CfgSubmitRuleDO> wrapper = new LambdaQueryWrapper<CfgSubmitRuleDO>()
                .in(CfgSubmitRuleDO::getPageId, pageIds)
                .eq(CfgSubmitRuleDO::getDeleted, DeletedEnum.UNDELETED.getCode());
        return submitRuleMapper.selectList(wrapper);
    }

    public boolean updateById(CfgSubmitRuleDO submitRuleDO) {
        return submitRuleMapper.updateById(submitRuleDO) > 0;
    }

    public List<CfgSubmitRuleDO> getEnableByPageId(Long pageId) {
        LambdaQueryWrapper<CfgSubmitRuleDO> wrapper = new LambdaQueryWrapper<CfgSubmitRuleDO>()
                .eq(CfgSubmitRuleDO::getPageId, pageId)
                .eq(CfgSubmitRuleDO::getStatus, StatusEnum.YES.getCode())
                .eq(CfgSubmitRuleDO::getDeleted, DeletedEnum.UNDELETED.getCode())
                .orderByAsc(CfgSubmitRuleDO::getCreateTime);
        return submitRuleMapper.selectList(wrapper);
    }

    public Integer batchSaveWithId(List<CfgSubmitRuleDO> submitRuleDOList) {
        return submitRuleMapper.batchSaveWithId(submitRuleDOList);
    }

    public boolean deleteById(Long id) {
        return submitRuleMapper.deleteById(id) > 0;
    }

    public boolean deleteByIdList(List<Long> idList) {
        int i = submitRuleMapper.deleteByIds(idList);
        return i > 0;
    }

    //========================================以下是手写sql的方法==============================================
    public Integer getMaxSequenceByPageId(Long pageId) {
        return submitRuleMapper.getMaxSequenceByPageId(pageId);
    }
}
