package com.bone.lowcode.infra.application.vo.event;

import lombok.Data;

@Data
public class PageEventVO {

    private String eventId;

    private String eventTriggerId;

    private String eventName; //事件名称

    private String label; //展示名称

    private Byte level; //对象层级

    private String location; //事件位置
}
