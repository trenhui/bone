package com.bone.tpa.claim.application.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

/**
 * 客户报案信息
 */
@Data
public class ApplyInfo {

    @Schema(description = "申请人姓名")
    private String applyName;

    @Schema(description = "申请人身份证")
    private String applyIdentityNo;

    @Schema(description = "申请类型")
    private String applyType;

    @Schema(description = "申请时间")
    private Date applyTime;

    @Schema(description = "联系方式")
    private String applyPhone;


    @Schema(description = "出险人姓名")
    private String outInsureName;

    @Schema(description = "出险人身份证")
    private String outInsureIdentityNo;

    @Schema(description = "出险时间")
    private Date outInsureTime;

    @Schema(description = "保单号")
    private String policyNo;

    @Schema(description = "领款人姓名")
    private String collectName;

    @Schema(description = "银行卡号")
    private String accountNo;

    @Schema(description = "开户行")
    private String bankName;

    @Schema(description = "开户行地址")
    private String bankAddress;
}
