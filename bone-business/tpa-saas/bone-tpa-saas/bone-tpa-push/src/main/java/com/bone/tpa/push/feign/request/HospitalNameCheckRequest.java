package com.bone.tpa.push.feign.request;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * @Author feihaiming
 *
 * @create 2025/9/18 17:26
 */
@Data
@AllArgsConstructor
public class HospitalNameCheckRequest {
    private List<String> hospitalNames;
}
