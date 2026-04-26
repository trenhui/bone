package com.bone.tpa.audit.infrastructure.feign.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * @description:
 * @author: caixuepu
 * @time: 2021/6/14
 */
@Schema(description = "个赔查询回参")
@Data
public class TpaHistoryClaimQueryResponse {

    @Schema(description = "个赔号")
    public String claimNo;
    @Schema(description = "状态(当前状态)")
    public String claimStatus;
    @Schema(description = "保险公司")
    public String insuranceName;
    @Schema(description = "投保客户")
    public String insureName;
    @Schema(description = "保单号")
    public String policyNo;
    @Schema(description = "被保险人")
    public String personName;
    @Schema(description = "被保险人证件号")
    public String personCertId;
    @Schema(description = "赔付金额")
    public BigDecimal compensationAmount;
    @Schema(description = "质检人(审核人员)")
    public String auditUser;
    @Schema(description = "质检时间(审核时间)")
    public String auditDate;
    @Schema(description = "是否拒赔")
    public String isReject;
    @Schema(description = "保司任务号")
    public String taskNo;
    @Schema(description = "赔案颜色标记")
    public Integer colorMark;
    @Schema(description = "公账赔付金额")
    public String publicAmount;
    @Schema(description = "发票号(多个,用,分隔)")
    public String invoiceNos;
}
