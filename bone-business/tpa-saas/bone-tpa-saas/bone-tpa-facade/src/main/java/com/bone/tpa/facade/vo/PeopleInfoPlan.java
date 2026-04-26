package com.bone.tpa.facade.vo;


import lombok.Data;

@Data
public class PeopleInfoPlan{
    private String startTime;

    private String endTime;

    private String hierarchy;

    private String insuredRelation;

    private String submenuNumber;

    //分单号开始时间
    private String relationStartTime;
    //分单号结束时间
    private String relationEndTime;
    // 客户号(家属)
    private String relationCustomNo;
    // 家属计划
    private String relativeCondition;
    // 家属分单号
    private String relativePersonPsc;
}
