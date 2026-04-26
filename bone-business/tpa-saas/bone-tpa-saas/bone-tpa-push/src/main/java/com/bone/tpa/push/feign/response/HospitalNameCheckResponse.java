package com.bone.tpa.push.feign.response;

import lombok.Data;

import java.util.Map;

/**
 * @Author feihaiming
 *
 * @create 2025/9/18 17:31
 */
@Data
public class HospitalNameCheckResponse {
    private Map<String, Boolean> matchResult;
}
