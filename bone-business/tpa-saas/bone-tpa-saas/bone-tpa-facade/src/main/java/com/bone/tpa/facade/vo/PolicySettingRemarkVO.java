package com.bone.tpa.facade.vo;

import lombok.Data;

@Data
public class PolicySettingRemarkVO {

    /**
     * 保单号
     */
    private String insurancepolicyno;
    /**
     * 投保公司名称
     */
    private String insurename;
    /**
     * 保险公司名称
     */
    private String insurancename;
    /**
     * 姓名
     */
    private String name ;
    /**
     * 身份证
     */
    private String certid;
    /**
     * 特殊/特约提醒
     */
    private String remark;
    /**
     * 更新时间
     */
    private String updateTime;
    /**
     * 更0-特约 1-特殊
     */
    private Integer settingSign;
    /**
     * 保司特约
     */
    private String insurerSpecial;
    /**
     * 保司特约更新时间
     */
    private String insurerSpecialTime;
}
