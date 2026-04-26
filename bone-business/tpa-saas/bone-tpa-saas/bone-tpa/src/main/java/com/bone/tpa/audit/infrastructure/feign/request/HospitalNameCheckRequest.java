package com.bone.tpa.audit.infrastructure.feign.request;

import lombok.Data;

import java.util.List;

/**
 * @Author feihaiming
 *
 * @create 2025/9/18 17:26
 */
@Data
public class HospitalNameCheckRequest {
    private List<String> hospitalNames;
}
