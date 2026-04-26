package com.bone.tpa.facade.request;

import com.bone.tpa.api.vo.ClaimDetailSyncVO;
import lombok.Data;

@Data
public class TpaHandupRequest {

    private String mockTag ;
    /**
     * 赔案号
     */
    private String claimNo;
    /**
     * 挂起节点：初审/录入/质检
     * 传入中文
     */
    private String node ;

    /**
     * 挂起类型（人工/系统）（统一传人工）
     */
    private String type = "人工";

    /**
     * 原因
     */
    private String reason;

    /**
     * 原因类型
     * 见枚举
     * TpaHandupReason.code
     *
     */
    private String reasonType;

    /**
     * 挂起人员
     *
     */
    public String hangUpUser;

    /**
     * 案件详情
     */
    private ClaimDetailSyncVO claimInfo;
}
