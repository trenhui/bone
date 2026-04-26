package com.bone.tpa.audit.infrastructure.feign.response;

import lombok.Data;

import java.util.HashMap;
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
