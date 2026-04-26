package com.bone.lowcode.infra.application.convert;

import com.bone.lowcode.infra.application.dto.event.CreateEventDTO;
import com.bone.lowcode.infra.application.dto.event.UpdateEventDTO;
import com.bone.lowcode.infra.application.vo.event.GetEventVO;
import com.bone.lowcode.infra.application.vo.event.ListEventVO;
import com.bone.lowcode.infra.domain.valueobject.DevStatusEnum;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.CfgEventDO;
import org.springframework.beans.BeanUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class EventConvert {


    public static CfgEventDO createEventDtoToDO(CreateEventDTO dto) {
        CfgEventDO eventDO = new CfgEventDO();
        BeanUtils.copyProperties(dto, eventDO);
        eventDO.setDevStatus(DevStatusEnum.UN_COMPLETE.getCode());
        eventDO.setCreateBy(null);
        eventDO.setCreateTime(new Date());
        return eventDO;
    }

    public static CfgEventDO updateEventDtoToDO(UpdateEventDTO dto) {
        CfgEventDO eventDO = new CfgEventDO();
        BeanUtils.copyProperties(dto, eventDO);
        return eventDO;
    }

    public static List<ListEventVO> DOListToListEventVOList(List<CfgEventDO> eventDOList) {
        List<ListEventVO> voList = new ArrayList<>();
        for (CfgEventDO eventDO : eventDOList) {
            ListEventVO vo = new ListEventVO();
            BeanUtils.copyProperties(eventDO, vo);
            vo.setId(eventDO.getId().toString());
            voList.add(vo);
        }
        return voList;
    }

    public static GetEventVO DOToGetEventVO(CfgEventDO eventDO) {
        GetEventVO vo = new GetEventVO();
        BeanUtils.copyProperties(eventDO, vo);
        return vo;
    }
}
