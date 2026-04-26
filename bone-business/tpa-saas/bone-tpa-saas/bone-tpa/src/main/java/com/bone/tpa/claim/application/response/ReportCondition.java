package com.bone.tpa.claim.application.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 报案信息体
 */
@Data
public class ReportCondition {

    @Schema(description = "赔案号")
    private String claimNo;

    @Schema(description = "保单号")
    private String policyNo;

    /**
     * 出险人信息
     */
    @Schema(description = "出险人姓名")
    private String outInsureName;
    @Schema(description = "出险人证件类型")
    private String outInsureIdentityType;
    @Schema(description = "出险人证件号")
    private String outInsureIdentityNo;
    @Schema(description = "出险人身份")
    private String outInsureIdentity;
    @Schema(description = "出险人性别")
    private String outInsureGender;
    @Schema(description = "出险人年龄")
    private String outInsureAge;
    @Schema(description = "出险人出生日期")
    private String outInsureBirthDay;


    /**
     * 报案人信息
     */
    @Schema(description = "报案人姓名")
    private String reportName;
    @Schema(description = "报案人电话")
    private String reportPhone;


    /**
     * 联系人信息
     */
    @Schema(description = "联系人姓名")
    private String contactName;
    @Schema(description = "联系人电话")
    private String contactPhone;


    /**
     * 出险信息
     */
    @Schema(description = "出险地点")
    private String outInsureAddress;
    @Schema(description = "出险经过")
    private String outInsureDetail;
    @Schema(description = "出险时间")
    private String outInsureTime;

    /**
     * 其他信息
     */
    @Schema(description = "报案人与出险人关系")
    private String relationType;

    @Schema(description = "疾病原因")
    private String diseaseReason;

    //责任类型， 编码：P-普通责任；C-公共责任
    @Schema(description = "责任类型")
    private String responsibilityType;

    /**
     * 下面的分类
     */
    private List<KindCode> kindCodeTypes;
    @Schema(description = "条款代码")
    private String kindCode;

    private List<KindCode> itemCodeTypes;
    @Schema(description = "一级责任代码")
    private String itemCode;

    private List<KindCode> secondaryItemCodeTypes;
    @Schema(description = "二级责任代码")
    private String secondaryItemCode;

    /**
     * 估损金额
     */
    private Map<String, BigDecimal> payoutAmountMap;
    @Schema(description = "估损金额")
    private String payoutAmountType;
}
