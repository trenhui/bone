package com.bone.lowcode.infra.application.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bone.core.exception.ServiceException;
import com.bone.lowcode.infra.application.convert.EventConvert;
import com.bone.lowcode.infra.application.convert.EventTriggerConvert;
import com.bone.lowcode.infra.application.dto.event.CreateEventDTO;
import com.bone.lowcode.infra.application.dto.event.CreateEventTriggerDTO;
import com.bone.lowcode.infra.application.dto.event.ListEventDTO;
import com.bone.lowcode.infra.application.dto.event.UpdateEventDTO;
import com.bone.lowcode.infra.application.vo.PageResult;
import com.bone.lowcode.infra.application.vo.event.GetEventVO;
import com.bone.lowcode.infra.application.vo.event.ListEventVO;
import com.bone.lowcode.infra.application.vo.event.PageEventVO;
import com.bone.lowcode.infra.domain.service.*;
import com.bone.lowcode.infra.domain.valueobject.TriggerOwnerEnum;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;


@Slf4j
@Service
public class EventApplicationService {

    @Autowired
    private EventService eventService;

    @Autowired
    private EventTriggerService eventTriggerService;

    @Autowired
    private PageService pageService;
    @Autowired
    private FormService formService;
    @Autowired
    private BlockService blockService;
    @Autowired
    private TableService tableService;


    public boolean createEvent(CreateEventDTO dto) {
        CfgEventDO eventDO = EventConvert.createEventDtoToDO(dto);
        boolean flag = eventService.add(eventDO);
        return flag;
    }

    public boolean updateEvent(UpdateEventDTO dto) {
        CfgEventDO eventDO = EventConvert.updateEventDtoToDO(dto);
        boolean flag = eventService.updateById(eventDO);
        return flag;
    }

    public PageResult<ListEventVO> listEvent(ListEventDTO dto) {
        Page<CfgEventDO> page = eventService.page(dto);
        List<CfgEventDO> eventDOList = page.getRecords();
        List<ListEventVO> voList = EventConvert.DOListToListEventVOList(eventDOList);
        voList.sort((o1, o2) -> o2.getPrepare() - o1.getPrepare());
        return new PageResult<>(page.getCurrent(), page.getSize(), page.getTotal(), voList);
    }

    public GetEventVO getEvent(Long eventId) {
        CfgEventDO eventDO = eventService.getById(eventId);
        GetEventVO vo = EventConvert.DOToGetEventVO(eventDO);
        return vo;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean createEventTrigger(CreateEventTriggerDTO dto) {
        Byte owner = dto.getOwner();
        Long ownerId = dto.getOwnerId();
        Long pageId = null;
        if (owner == TriggerOwnerEnum.FORM.getCode()) {
            CfgFormDO formDO = formService.getDOById(ownerId);
            pageId = formDO.getPageId();
        } else if (owner == TriggerOwnerEnum.BLOCK.getCode()) {
            CfgBlockDO blockDO = blockService.getDOById(ownerId);
            pageId = blockDO.getPageId();
        } else if (owner == TriggerOwnerEnum.TABLE_ROW.getCode() ||
                owner == TriggerOwnerEnum.TABLE_LEFT.getCode() ||
                owner == TriggerOwnerEnum.TABLE_RIGHT.getCode()) {
            CfgTableDO tableDO = tableService.getDOById(ownerId);
            pageId = tableDO.getPageId();
        }

        CfgEventTriggerDO triggerDO = EventTriggerConvert.createEventTriggerDTOToDO(dto);
        triggerDO.setPageId(pageId);
        boolean flag = eventTriggerService.add(triggerDO);
        return flag;
    }

    public boolean deleteEventTriggerById(Long eventTriggerId) {
        return eventTriggerService.deleteById(eventTriggerId);
    }

    public List<PageEventVO> getByPage(String pageCode, String bizIdentityCode, Byte type) {
        CfgPageDO pageDO = pageService.getByPageCodeAndIdentityCode(pageCode, bizIdentityCode);
        if (pageDO == null) {
            throw new ServiceException(500, "目标页面不存在,pageCode:" + pageCode + ", bizIdentityCode:" + bizIdentityCode);
        }

        List<CfgEventTriggerDO> triggerDOList = eventTriggerService.getDOListByPageId(pageDO.getId());
        if (CollectionUtils.isEmpty(triggerDOList)) {
            return new ArrayList<>();
        }

        List<Long> eventIdList = triggerDOList.stream().map(CfgEventTriggerDO::getEventId).filter(Objects::nonNull).toList();
        if (CollectionUtils.isEmpty(eventIdList)) {
            return new ArrayList<>();
        }

        List<CfgEventDO> eventDOList = eventService.getDOListByIdList(eventIdList);
        if (CollectionUtils.isEmpty(eventDOList)) {
            return new ArrayList<>();
        }
        Map<Long, CfgEventDO> eventMap = eventDOList.stream().collect(Collectors.toMap(CfgEventDO::getId, i -> i));

        Set<Long> formIdSet = new HashSet<>();
        Set<Long> blockIdSet = new HashSet<>();
        Set<Long> tableIdSet = new HashSet<>();
        for (CfgEventTriggerDO triggerDO : triggerDOList) {
            Byte owner = triggerDO.getOwner();
            Long ownerId = triggerDO.getOwnerId();
            if (owner == null || ownerId == null) {
                continue;
            }

            if (owner == TriggerOwnerEnum.FORM.getCode()) {
                formIdSet.add(ownerId);
            } else if (owner == TriggerOwnerEnum.BLOCK.getCode()) {
                blockIdSet.add(ownerId);
            } else if (owner == TriggerOwnerEnum.TABLE_ROW.getCode() ||
                    owner == TriggerOwnerEnum.TABLE_LEFT.getCode() ||
                    owner == TriggerOwnerEnum.TABLE_RIGHT.getCode()) {
                tableIdSet.add(ownerId);
            }
        }

        Map<Long, CfgFormDO> formMap = new HashMap<>();
        if (!CollectionUtils.isEmpty(formIdSet)) {
            List<CfgFormDO> formDOList = formService.getByIds(formIdSet);
            formMap = formDOList.stream().collect(Collectors.toMap(CfgFormDO::getId, i -> i));
        }

        Map<Long, CfgBlockDO> blockMap = new HashMap<>();
        if (!CollectionUtils.isEmpty(blockIdSet)) {
            List<CfgBlockDO> blockDOList = blockService.getByIds(blockIdSet);
            blockMap = blockDOList.stream().collect(Collectors.toMap(CfgBlockDO::getId, i -> i));
        }

        Map<Long, CfgTableDO> tableMap = new HashMap<>();
        if (!CollectionUtils.isEmpty(tableIdSet)) {
            List<CfgTableDO> tableDOList = tableService.getByIds(tableIdSet);
            tableMap = tableDOList.stream().collect(Collectors.toMap(CfgTableDO::getId, i -> i));
        }

        List<PageEventVO> res = new ArrayList<>();
        for (CfgEventTriggerDO triggerDO : triggerDOList) {
            Long eventId = triggerDO.getEventId();
            if (eventId == null) {
                continue;
            }
            CfgEventDO eventDO = eventMap.get(eventId);
            if (eventDO == null) {
                continue;
            }
            if (type != null && !type.equals(eventDO.getType())) {
                continue;
            }

            PageEventVO pageEventVO = new PageEventVO();
            pageEventVO.setEventId(eventDO.getId().toString());
            pageEventVO.setEventTriggerId(triggerDO.getId().toString());
            pageEventVO.setEventName(eventDO.getName());
            pageEventVO.setLabel(triggerDO.getLabel());
            pageEventVO.setLevel(eventDO.getLevel());
            res.add(pageEventVO);

            Byte owner = triggerDO.getOwner();
            Long ownerId = triggerDO.getOwnerId();
            if (owner == null || ownerId == null) {
                continue;
            }
            if (owner == TriggerOwnerEnum.FORM.getCode()) {
                CfgFormDO formDO = formMap.get(ownerId);
                if (formDO != null) {
                    pageEventVO.setLocation(TriggerOwnerEnum.FORM.getDesc() + ":" + formDO.getName());
                }
            } else if (owner == TriggerOwnerEnum.BLOCK.getCode()) {
                CfgBlockDO blockDO = blockMap.get(ownerId);
                if (blockDO != null) {
                    pageEventVO.setLocation(TriggerOwnerEnum.BLOCK.getDesc() + ":" + blockDO.getName());
                }
            } else if (owner == TriggerOwnerEnum.TABLE_ROW.getCode() || owner == TriggerOwnerEnum.TABLE_LEFT.getCode() ||
                    owner == TriggerOwnerEnum.TABLE_RIGHT.getCode()) {
                CfgTableDO tableDO = tableMap.get(ownerId);
                if (tableDO != null) {
                    String desc = TriggerOwnerEnum.getDescByCode(owner);
                    if (StringUtils.hasText(desc)) {
                        pageEventVO.setLocation(desc + ":" + tableDO.getName());
                    }
                }
            }
        }
        return res;
    }
}
