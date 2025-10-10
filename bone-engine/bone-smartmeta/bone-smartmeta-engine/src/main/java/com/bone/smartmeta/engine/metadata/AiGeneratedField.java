package com.bone.smartmeta.engine.metadata;

import lombok.Getter;
import lombok.Setter;

/**
 * AI生成字段模型类
 */
@Getter
@Setter
public class AiGeneratedField {
    
    // 目标字段API名称
    private String targetFieldApiName;
    
    // 生成提示模板
    private String promptTemplate;
    
    // 更新策略（ON_CREATE, ON_UPDATE, MANUAL）
    private String updateStrategy = "ON_CREATE";
    
    // 是否启用
    private boolean enabled = true;
}