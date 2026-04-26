package com.bone.lowcode.infra.application.vo.page.pageJson;

import lombok.Data;

@Data
public class EventTrigger {

    private String id;

    /**
     * 显示名
     */
    private String label;

    /**
     * 样式，0：default，1：info，2：primary，3：success，4：warning，5：danger
     */
    private Byte style;

    /**
     * 展示形式，0：Button，1：Link
     */
    private Byte displayType;

    /**
     * 归属，0：form, 1:block, 2:table行, 3:table左表头, 4:table右表头
     */
    private Byte owner;

//    =========================
    /**
     * 事件ID
     */
    private String eventId;

    /**
     * 事件名称
     */
    private String eventName;

    /**
     * 事件标识
     */
    private String eventCode;

    /**
     * 是否是预设事件,0:否,1:是
     */
    private Byte prepare;
}
