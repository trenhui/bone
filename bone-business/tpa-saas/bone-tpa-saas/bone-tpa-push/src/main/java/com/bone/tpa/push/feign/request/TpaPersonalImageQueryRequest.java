package com.bone.tpa.push.feign.request;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * @Author feihaiming
 * @create 2025/9/22 16:01
 */
@Data
@AllArgsConstructor
public class TpaPersonalImageQueryRequest {
    private String outIdentityNo;
    private String outUserName;
    private String insuranceName;
}
