package com.bone.tpa.sdk.claim.model;

import com.bone.metadata.sdk.domain.annotation.*;
import com.bone.core.tenant.TenantAbstractEntity;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;

/**
 * ss_claim_copy_log DO
 *
 * @author 0
 */
@Table("ss_claim_copy_log")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class ClaimCopyLog extends TenantAbstractEntity<Long> {
    /**
     * 保单号
     */
    private String policyNo;
    /**
     * 原配案批次
     */
    private String oldBatchNo;
    /**
     * 原赔案id
     */
    private Long oldClaimId;
    /**
     * 原赔案号
     */
    private String oldClaimNo;
    /**
     * 原签收时间
     */
    private Date oldSignTime;

    /**
     * 新配案批次
     */
    private String newBatchNo;
    /**
     * 新赔案id
     */
    private Long newClaimId;
    /**
     * 新赔案号
     */
    private String newClaimNo;
    /**
     * 新签收时间
     */
    private Date newSignTime;

    /**
     * 操作人员
     */
    private String operator;
    /**
     * 复制时间
     */
    private Date operateTime;

    /**
     * 备注
     */
    private String remark;

    /**
     * 赔案复制请求
     */
    private String copyRequest;

    public String getPolicyNo() {
        return policyNo;
    }

    public void setPolicyNo(String policyNo) {
        this.policyNo = policyNo;
    }

    public String getOldBatchNo() {
        return oldBatchNo;
    }

    public void setOldBatchNo(String oldBatchNo) {
        this.oldBatchNo = oldBatchNo;
    }

    public Long getOldClaimId() {
        return oldClaimId;
    }

    public void setOldClaimId(Long oldClaimId) {
        this.oldClaimId = oldClaimId;
    }

    public String getOldClaimNo() {
        return oldClaimNo;
    }

    public void setOldClaimNo(String oldClaimNo) {
        this.oldClaimNo = oldClaimNo;
    }

    public Date getOldSignTime() {
        return oldSignTime;
    }

    public void setOldSignTime(Date oldSignTime) {
        this.oldSignTime = oldSignTime;
    }

    public String getNewBatchNo() {
        return newBatchNo;
    }

    public void setNewBatchNo(String newBatchNo) {
        this.newBatchNo = newBatchNo;
    }

    public Long getNewClaimId() {
        return newClaimId;
    }

    public void setNewClaimId(Long newClaimId) {
        this.newClaimId = newClaimId;
    }

    public String getNewClaimNo() {
        return newClaimNo;
    }

    public void setNewClaimNo(String newClaimNo) {
        this.newClaimNo = newClaimNo;
    }

    public Date getNewSignTime() {
        return newSignTime;
    }

    public void setNewSignTime(Date newSignTime) {
        this.newSignTime = newSignTime;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public Date getOperateTime() {
        return operateTime;
    }

    public void setOperateTime(Date operateTime) {
        this.operateTime = operateTime;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public String getCopyRequest() {
        return copyRequest;
    }

    public void setCopyRequest(String copyRequest) {
        this.copyRequest = copyRequest;
    }
}
