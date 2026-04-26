package com.bone.lowcode.infra.domain.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bone.lowcode.infra.application.dto.event.ListEventDTO;
import com.bone.lowcode.infra.domain.valueobject.DeletedEnum;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.CfgEventDO;
import com.bone.lowcode.infra.infrastructure.persistence.mapper.EventMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.List;

/**
 * @Author fhmdf
 * @create 2024/7/20 17:57
 */
@Service
public class EventService {

    @Autowired
    private EventMapper eventMapper;


    public boolean add(CfgEventDO eventDO) {
        return eventMapper.insert(eventDO) > 0;
    }


    public boolean updateById(CfgEventDO eventDO) {
        return eventMapper.updateById(eventDO) > 0;
    }

    public Page<CfgEventDO> page(ListEventDTO dto) {
        LambdaQueryWrapper<CfgEventDO> wrapper = new LambdaQueryWrapper<CfgEventDO>()
                .eq(StringUtils.hasText(dto.getName()), CfgEventDO::getName, dto.getName())
                .eq(dto.getOwner() != null, CfgEventDO::getOwner, dto.getOwner())
                .eq(dto.getDevStatus() != null, CfgEventDO::getDevStatus, dto.getDevStatus())
                .eq(dto.getStatus() != null, CfgEventDO::getStatus, dto.getStatus())
                .eq(dto.getType() != null, CfgEventDO::getType, dto.getType())
                .eq(dto.getTriggerType() != null, CfgEventDO::getTriggerType, dto.getTriggerType())
                .eq(dto.getLevel() != null, CfgEventDO::getLevel, dto.getLevel())
                .eq(CfgEventDO::getDeleted, DeletedEnum.UNDELETED.getCode());

        Page<CfgEventDO> page = new Page<>(dto.getPageNum(), dto.getPageSize());
        eventMapper.selectPage(page, wrapper);
        return page;
    }

    public CfgEventDO getById(Long eventId) {
        return eventMapper.selectOne(new LambdaQueryWrapper<CfgEventDO>()
                .eq(CfgEventDO::getId, eventId)
                .eq(CfgEventDO::getDeleted, DeletedEnum.UNDELETED.getCode()));
    }

    public List<CfgEventDO> getDOListByIdList(Collection<Long> eventIds) {
        return eventMapper.selectList(new LambdaQueryWrapper<CfgEventDO>()
                .in(CfgEventDO::getId, eventIds)
                .eq(CfgEventDO::getDeleted, DeletedEnum.UNDELETED.getCode()));
    }
}
