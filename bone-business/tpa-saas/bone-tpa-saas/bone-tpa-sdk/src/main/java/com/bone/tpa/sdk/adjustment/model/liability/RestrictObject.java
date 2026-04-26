package com.bone.tpa.sdk.adjustment.model.liability;

import lombok.Data;

import java.util.List;

/**
 * 适用对象
 */
@Data
public class RestrictObject {
    private String gender; // 性别
    private RangeObject age; // 年龄范围
    private List<String> occupation; // 职业
    private String other; // 其他
}
