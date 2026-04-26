package com.bone.tpa.sdk.vo;

import lombok.Data;

public class ClaimFlowConfigVO {
    private String bizIdentityCode;
    private FlowConfigVO flowConfigVO;
    private PreCheckConfigVO preCheckConfigVO;
    private InputConfigVO inputConfigVO;
    private QualityConfigVO qualityConfigVO;
    private ApproveConfigVO approveConfigVO;
    private ApproveCheckConfigVO approveCheckConfigVO;
    private Integer status;

    public String getBizIdentityCode() {
        return bizIdentityCode;
    }

    public void setBizIdentityCode(String bizIdentityCode) {
        this.bizIdentityCode = bizIdentityCode;
    }

    public FlowConfigVO getFlowConfigVO() {
        return flowConfigVO;
    }

    public void setFlowConfigVO(FlowConfigVO flowConfigVO) {
        this.flowConfigVO = flowConfigVO;
    }

    public PreCheckConfigVO getPreCheckConfigVO() {
        return preCheckConfigVO;
    }

    public void setPreCheckConfigVO(PreCheckConfigVO preCheckConfigVO) {
        this.preCheckConfigVO = preCheckConfigVO;
    }

    public InputConfigVO getInputConfigVO() {
        return inputConfigVO;
    }

    public void setInputConfigVO(InputConfigVO inputConfigVO) {
        this.inputConfigVO = inputConfigVO;
    }

    public QualityConfigVO getQualityConfigVO() {
        return qualityConfigVO;
    }

    public void setQualityConfigVO(QualityConfigVO qualityConfigVO) {
        this.qualityConfigVO = qualityConfigVO;
    }

    public ApproveConfigVO getApproveConfigVO() {
        return approveConfigVO;
    }

    public void setApproveConfigVO(ApproveConfigVO approveConfigVO) {
        this.approveConfigVO = approveConfigVO;
    }

    public ApproveCheckConfigVO getApproveCheckConfigVO() {
        return approveCheckConfigVO;
    }

    public void setApproveCheckConfigVO(ApproveCheckConfigVO approveCheckConfigVO) {
        this.approveCheckConfigVO = approveCheckConfigVO;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }
}
