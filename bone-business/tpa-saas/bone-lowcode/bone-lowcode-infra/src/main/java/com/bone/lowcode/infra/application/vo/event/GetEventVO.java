package com.bone.lowcode.infra.application.vo.event;

import lombok.Data;

import java.util.Date;


@Data
public class GetEventVO {


    private Long id;

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
     * 开发状态，0：开发未完成，1：开发完成
     */
    private Byte devStatus;

    /**
     * 状态，0：不启用，1：启用
     */
    private Byte status;

    /**
     * 规则说明
     */
    private String ruleDescription;

    /**
     * 事件备注
     */
    private String remark;

    /**
     * 是否删除，0：正常，1：已删除
     */
    private Byte deleted;

    /**
     * 启用时间
     */
    private Date enableTime;

    /**
     * 创建人
     */
    private Long createBy;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 修改人
     */
    private Long updateBy;

    /**
     * 修改时间
     */
    private Date updateTime;
}
