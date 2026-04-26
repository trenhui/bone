package com.bone.tpa.push.dto;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * @Author feihaiming
 * @create 2025/10/16 14:57
 */
@Data
public class ClaimImageDTO {
    private String claimCode;
    private String name;
    private String type;
    private String address;
    private Integer status;
    private Map<String, String> extraFields = new HashMap<>();
}
