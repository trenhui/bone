package com.bone.tpa.push.feign.request;

import lombok.Data;

import java.util.List;

/**
 * @Author feihaiming
 * @create 2025/9/22 16:01
 */
@Data
public class ClaimCancelRequest {
    private List<String> claimNumbers;
    private String reason = "重复推送";
}
