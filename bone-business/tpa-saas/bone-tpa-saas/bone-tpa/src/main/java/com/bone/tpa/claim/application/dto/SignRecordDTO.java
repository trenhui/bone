package com.bone.tpa.claim.application.dto;

import com.bone.core.tenant.TenantAbstractEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

@Data
@Schema(name = "signRecord", description = "签收信息")
public class SignRecordDTO extends TenantAbstractEntity<SignRecordDTO, Long> {

    @Schema(description = "批次号")
    private String batchNo;
    @Schema(description = "业务身份code")
    private String bizIdentityCode;


    @Schema(description = "签收方式")
    private String signType;
    @Schema(description = "签收渠道")
    private String signChannel;
    @Schema(description = "业务类型")
    private String bizType;
    @Schema(description = "流程类型")
    private String processType;

    @Schema(description = "赔案数")
    private BigDecimal claimCount;
    @Schema(description = "是否有上传影像")
    private String imageUploadFlag;

    @Schema(description = "保险公司")
    private String insuranceCompany;
    @Schema(description = "保险分公司")
    private String insuranceSubsidiary;
    @Schema(description = "投保公司")
    private String insuringAgency;

    @Schema(description = "收单流水号")
    private String deliveryNo;
    @Schema(description = "收件时间")
    private Date deliveryTime;
    @Schema(description = "快递编号")
    private String expressNo;
    @Schema(description = "快递公司")
    private String expressCompany;
    @Schema(description = "快递到达时间")
    private Date arriveTime;
    @Schema(description = "发件地")
    private String sendFrom;
    @Schema(description = "发件人")
    private String sender;
    @Schema(description = "发件人联系方式")
    private String senderContact;

    @Schema(description = "紧急程度")
    private String emergency;
    @Schema(description = "备注")
    private String remark;
    @Schema(description = "签收时间")
    private Date signTime;
    @Schema(description = "签收机构")
    private String signInstitution;
    @Schema(description = "签收操作人员")
    private String signOperator;
    @Schema(description = "签收状态")
    private String signStatus;

}
