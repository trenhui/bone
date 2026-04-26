package com.bone.tpa.intelligent.adjustment.dto;


import lombok.Data;

@Data
public class LiabilitySharingRelationDTO extends DtoBase{

    /**
     * 内部保单号
     */
    private String policyNo;

    /**
     * 计划ID
     */
    private Long planId;


    /**
     * 责任UUID
     */
    private String liabilityUuid;


    /**
     * 责任共保代码
     */
    private String shareCode;


    /**
     * 计划版本
     */
    private String version;
}
