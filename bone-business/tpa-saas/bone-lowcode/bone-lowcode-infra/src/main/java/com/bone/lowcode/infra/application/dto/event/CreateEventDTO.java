package com.bone.lowcode.infra.application.dto.event;

import lombok.Data;

@Data
public class CreateEventDTO {

    /**
     * 事件名称
     */
    private String name;

    /**
     * 事件标识
     */
    private String code;

    /**
     * 归属，0：form, 1:block, 2:table行, 3:table左表头, 4:table右表头
     */
    private Byte owner;

    /**
     * 事件类型，0：流程事件，1：业务事件
     */
    private Byte type;

    /**
     * 对象层级，0：系统级，1：页面级，2：流程级，3：赔案级，4：保单级，5：发票级，6：费用项目级，7：费用项目明细级
     */
    private Byte level;

    /**
     * 触发机制，0：手动触发，1：定时触发，2：规则触发
     */
    private Byte triggerType;

    /**
     * 规则说明
     */
    private String ruleDescription;

    /**
     * 事件备注
     */
    private String remark;
}
