package com.bone.tpa.intelligent.adjustment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

@Data
@Schema(name = "policy", description = "保单信息")
public class PolicyDTO  extends  DtoBase{

    @Schema(description = "普康保单号")
    private String policyNo;

    @Schema(description = "父保单号")
    private String parentPolicyNo;

    @Schema(description = "投保公司")
    private String insureName;

    @Schema(description = "投保公司Guid")
    private String insureId;

    @Schema(description = "保险公司")
    private String insuranceName;

    @Schema(description = "保险公司Guid")
    private String carrierId;

    @Schema(description = "保司保单号")
    private String externalPolicyNo;

    @Schema(description = "责任配置状态")
    private String configStatus;

    @Schema(description = "状态")
    private Integer status;

    @Schema(description = "保单生效日")
    private Date effdate;

    @Schema(description = "保单失效日")
    private Date expdate;

    @Schema(description = "保单类型")
    private Integer policyType;

    @Schema(description = "购买方式")
    private Integer policyBuyType;

    @Schema(description = "其他公司")
    private String otherCompany;

    @Schema(description = "其他公司Guid")
    private String otherCompanyId;

    @Schema(description = "项目类型")
    public Integer itemType;

    @Schema(description = "保单属性")
    private Integer policyAttribute;

    @Schema(description = "保单启用状态")
    private Integer policyFlag;

    @Schema(description = "理算顺序")
    private Integer adjustmentOrder;
}
