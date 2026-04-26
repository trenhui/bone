package com.bone.tpa.sdk.adjustment.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

@Data
public class LiabilityMappingRes {

    private String id;

    /**
     * 责任uuid
     */
    private String liabilityUuid;

    /**
     * 责任名称(冗余)
     */
    private String liabilityName;

    /**
     * 发票医疗类型
     */
    private String invoiceMedicalType;

    /**
     * 发票医疗类型中文
     */
    private String invoiceMedicalTypeCN;

    /**
     * 保司险种代码
     */
    private String insuranceCompanyCoverage;

    /**
     * 保司责任代码
     */
    private String insuranceCompanyLiability;

    /**
     * 保司责任子码
     */
    private String insuranceCompanyLiabilitySub;

    /**
     * 索赔事故性质
     */
    private String claimAccident;

    /**
     * 保司个账公账
     */
    private String insuranceCompanyAccount;

    /**
     * 创建人
     */
    private String createUser;

    /**
     * 更新人
     */
    private String updateUser;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;
}
