package com.bone.tpa.sdk.vo;

import lombok.Data;

@Data
public class PreCheckConfigVO {
    /**
     * 影像分类规则
     * 0 有影像件分类即可(分类的影像件>=1)
     * 1 所有影像件均分类
     * 2 无需强制分类
     */
    private String categoryRule = "0";


    /**
     * 0 先自动分类再人工确认
     * 1 仅自动分类
     * 2 仅人工分类
     */
    private String categoryType="0";


    /**
     * 是否自动化
     * 0 否
     * 1 是
     */
    private String autoTag="0";

    /**
     * 是否自动分类
     * 0 否
     * 1 是
     */
    //// TODO
    private String autoCategoryImage="0";


    /**
     * 处理人分配策略
     * 0 手工分配
     * 1 随机分配
     */
    private String dealerAssignType="0";

}
