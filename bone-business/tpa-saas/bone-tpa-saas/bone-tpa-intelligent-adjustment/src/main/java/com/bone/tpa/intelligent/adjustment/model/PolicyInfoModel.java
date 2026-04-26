package com.bone.tpa.intelligent.adjustment.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;

/**
 * 保单绑定信息
 */
@Data
@Schema(name = "policyInfoModel", description = "保单绑定信息")
public class PolicyInfoModel {

    @Schema(description = "绑定")
    private Boolean isBind = false;

    @Schema(description = "保单号")
    private String policyNo;
    
    @Schema(description = "计划名称")
    private String planName;

    @Schema(description = "保单开始日期")
    private String startDate;

    @Schema(description = "保单结束日期")
    private String endDate;

    @Schema(description = "关系")
    private String relation;

    @Schema(description = "关系类型")
    private Integer relationType;

    @Schema(description = "主被保险人姓名")
    private String mainPersonName;

    @Schema(description = "主被保险人身份证号")
    private String mainPersonCertId;

    @Schema(description = "主被保险人身份证类型")
    private String mainPersonCertType;

    @Schema(description = "主被联系方/式")
    private String mobile;

    @Schema(description = "分单号")
    private String slipCode;

    @Schema(description = "投保序号")
    private String serialNumber;

    @Schema(description = "投保公司名称")
    private String insureName;

    @Schema(description = "保险公司名称")
    private String insuranceName;

    @Schema(description = "是否有特殊提示")
    private Boolean isSpecialTip;

    @Schema(description = "承保时间")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date insuredTime;

    @DateTimeFormat(pattern = "yyyy-MM-dd")
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    @Schema(description = "退保时间")
    private Date cancellationTime;

    @DateTimeFormat(pattern = "yyyy-MM-dd")
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    @Schema(description = "生效时间")
    private Date effectiveTime;

    @Schema(description = "主关系")
    public String masterRelation;

    @Schema(description = "次关系")
    private String secondaryRelation;

    @Schema(description = "直付银行账号")
    private String bankAccount;

    @Schema(description = "直付开户银行")
    private String bankName;

    @Schema(description = "是否提示证件类型不一致 0-否 1-是")
    private Integer isCertType;

    @Schema(description = "出险人证件类型")
    private String certType;

    @Schema(description = "主客户号")
    private String inCustomerNo;
}
