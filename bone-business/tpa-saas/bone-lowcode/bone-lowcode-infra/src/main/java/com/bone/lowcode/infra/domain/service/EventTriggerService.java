package com.bone.lowcode.infra.domain.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bone.lowcode.infra.domain.valueobject.DeletedEnum;
import com.bone.lowcode.infra.domain.valueobject.TriggerOwnerEnum;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.CfgEventTriggerDO;
import com.bone.lowcode.infra.infrastructure.persistence.mapper.EventTriggerMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

@Service
public class EventTriggerService {

    @Autowired
    private EventTriggerMapper eventTriggerMapper;


    public boolean add(CfgEventTriggerDO triggerDO) {
        return eventTriggerMapper.insert(triggerDO) > 0;
    }

    public List<CfgEventTriggerDO> getDOListByPageId(Long pageId) {
        LambdaQueryWrapper<CfgEventTriggerDO> wrapper = new LambdaQueryWrapper<CfgEventTriggerDO>()
                .eq(CfgEventTriggerDO::getPageId, pageId)
                .eq(CfgEventTriggerDO::getDeleted, DeletedEnum.UNDELETED.getCode());
        List<CfgEventTriggerDO> eventTriggerDOList = eventTriggerMapper.selectList(wrapper);
        return eventTriggerDOList;
    }

    public List<CfgEventTriggerDO> getByPageIds(Collection<Long> pageIds) {
        LambdaQueryWrapper<CfgEventTriggerDO> wrapper = new LambdaQueryWrapper<CfgEventTriggerDO>()
                .in(CfgEventTriggerDO::getPageId, pageIds)
                .eq(CfgEventTriggerDO::getDeleted, DeletedEnum.UNDELETED.getCode());
        return eventTriggerMapper.selectList(wrapper);
    }

    public void batchSave(List<CfgEventTriggerDO> eventTriggerList) {
        eventTriggerMapper.insert(eventTriggerList);
    }

    public boolean deleteById(Long eventTriggerId) {
        return eventTriggerMapper.deleteById(eventTriggerId) > 0;
    }

    public boolean deleteByIdList(List<Long> idList) {
        int i = eventTriggerMapper.deleteByIds(idList);
        return i > 0;
    }

    public List<CfgEventTriggerDO> getDOListByTableId(Long tableId) {
        List<CfgEventTriggerDO> triggerDOList = eventTriggerMapper.selectList(new LambdaQueryWrapper<CfgEventTriggerDO>()
                .eq(CfgEventTriggerDO::getOwnerId, tableId)
                .in(CfgEventTriggerDO::getOwner, TriggerOwnerEnum.TABLE_ROW.getCode(),
                        TriggerOwnerEnum.TABLE_LEFT.getCode(),
                        TriggerOwnerEnum.TABLE_RIGHT.getCode())
                .eq(CfgEventTriggerDO::getDeleted, DeletedEnum.UNDELETED.getCode()));
        return triggerDOList;
    }
}
