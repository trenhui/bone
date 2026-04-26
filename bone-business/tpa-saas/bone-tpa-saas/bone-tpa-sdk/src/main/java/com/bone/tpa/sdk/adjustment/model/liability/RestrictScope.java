package com.bone.tpa.sdk.adjustment.model.liability;

import lombok.Data;

import java.util.List;

/**
 * 限定适用范围
 */
@Data
public class RestrictScope {
    private String restrictRange; // 范围限定
    private Boolean open; // 是否启用
    private String type; // 限定方式
    private List<String> restrictList; // 限定清单

}
