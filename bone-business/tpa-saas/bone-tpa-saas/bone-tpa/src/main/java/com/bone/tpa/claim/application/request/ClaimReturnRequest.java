package com.bone.tpa.claim.application.request;

import lombok.Data;

@Data
public class ClaimReturnRequest {

    /**
     * 赔案号
     */
    private Long claimNumber;
    /**
     * 退回状态
     */
    private String targetStage;
    /**
     * 退回人员策略
     * 0 - 退回原处理人
     * 1 - 手工指定
     */
    private String dealerStrategy;

    /**
     * 指定的处理人的userid
     */
    private String assignDealerName;

    /**
     * 退回原因code
     */
    private String reasonCode;
    /**
     * 退回原因json
     */
    private String reasonData;

    private String remark;
}
