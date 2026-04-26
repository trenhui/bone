package com.bone.lowcode.infra.application.dto.table;

import lombok.Data;

@Data
public class TableEventTriggerDTO {

    /**
     * 事件ID
     */
    private Long eventId;

    /**
     * 归属，0：form, 1:block, 2:table行, 3:table左表头, 4:table右表头
     */
    private Byte owner;
}
