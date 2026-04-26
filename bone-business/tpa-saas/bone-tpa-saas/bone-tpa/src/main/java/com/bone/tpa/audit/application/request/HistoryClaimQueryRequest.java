package com.bone.tpa.audit.application.request;

import lombok.Data;

/**
 * @Author feihaiming
 * @create 2025/9/22 16:01
 */
@Data
public class HistoryClaimQueryRequest {
    private String outIdentityNo;
    private String outUserName;
    private Long claimNo;
}
