package com.bone.lowcode.infra.application.dto.event;

import lombok.Data;

@Data
public class CreateEventTriggerDTO {

    /**
     * 显示名
     */
    private String label;

    /**
     * 样式，0：default，1：info，2：primary，3：success，4：warning，5：danger
     */
    private Byte style;

    /**
     * 事件ID
     */
    private Long eventId;

    /**
     * 展示形式，0：Button，1：Link
     */
    private Byte displayType;

    /**
     * 归属，0：form, 1:block, 2:table行, 3:table左表头, 4:table右表头
     */
    private Byte owner;

    /**
     * 上级id
     */
    private Long ownerId;
}
