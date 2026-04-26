package com.bone.lowcode.infra.application.convert;

import com.bone.lowcode.infra.application.dto.event.CreateEventTriggerDTO;
import com.bone.lowcode.infra.domain.valueobject.DeletedEnum;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.CfgEventTriggerDO;
import org.springframework.beans.BeanUtils;

import java.util.Date;

public class EventTriggerConvert {


    public static CfgEventTriggerDO createEventTriggerDTOToDO(CreateEventTriggerDTO dto) {
        CfgEventTriggerDO triggerDO = new CfgEventTriggerDO();
        BeanUtils.copyProperties(dto, triggerDO);
        triggerDO.setDeleted(DeletedEnum.UNDELETED.getCode());
        triggerDO.setCreateBy(null);
        triggerDO.setCreateTime(new Date());
        return triggerDO;
    }
}
