package com.bone.lowcode.infra.application.vo.event;

import lombok.Data;

import java.util.Date;

@Data
public class ListEventVO {

    private String id;

    /**
     * 事件名称
     */
    private String name;

    /**
     * 展示名称
     */
    private String showName;

    /**
     * 事件标识
     */
    private String code;

    /**
     * 归属，0：form, 1:block, 2:table行, 3:table左表头, 4:table右表头
     */
    private Byte owner;

    /**
     * 事件类型，1：流程事件，0：业务事件
     */
    private Byte type;

    /**
     * 对象层级，0：系统级，1：页面级，2：流程级，3：赔案级，4：人员级，5：保单级，6：发票级，7：费用项目级，8：费用项目明细级
     */
    private Byte level;

    /**
     * 触发机制，0：手动触发，1：定时触发，2：规则触发
     */
    private Byte triggerType;

    /**
     * 是否是预设事件,0:否,1:是
     */
    private Byte prepare;

    /**
     * 开发状态，0：开发未完成，1：开发完成
     */
    private Byte devStatus;

    /**
     * 状态，0：不启用，1：启用
     */
    private Byte status;

    /**
     * 启用时间
     */
    private Date enableTime;

    /**
     * 创建时间
     */
    private Date createTime;
}
