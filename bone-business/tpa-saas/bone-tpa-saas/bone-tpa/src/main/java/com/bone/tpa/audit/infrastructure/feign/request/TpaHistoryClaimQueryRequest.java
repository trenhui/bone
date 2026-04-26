package com.bone.tpa.audit.infrastructure.feign.request;

import lombok.Data;

/**
 * @Author feihaiming
 * @create 2025/9/22 16:01
 */
@Data
public class TpaHistoryClaimQueryRequest {
    private String outIdentityNo;
    private String outUserName;
    private String insuranceName;
}
