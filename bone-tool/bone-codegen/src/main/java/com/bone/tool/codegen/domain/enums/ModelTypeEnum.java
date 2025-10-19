package com.bone.tool.codegen.domain.enums;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 模型类型枚举
 */
public enum ModelTypeEnum {
    
    /**
     * SAAS模型
     */
    SAAS(1, "saas"),
    
    /**
     * DDD模型
     */
    DDD(2, "ddd");
    
    private Integer type;
    private String name;
    
    ModelTypeEnum(Integer type, String name) {
        this.type = type;
        this.name = name;
    }
    
    public Integer getType() {
        return type;
    }
    
    public String getName() {
        return name;
    }
    
    /**
     * 获取模板类型
     */
    public static ModelTypeEnum valueOf(Integer type) {
        return Arrays.stream(values())
                .filter(modelTypeEnum -> modelTypeEnum.getType().equals(type))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("未找到对应的模板类型"));
    }
    
    // 添加静态fromName方法，避免与Java枚举默认方法冲突
    public static ModelTypeEnum fromName(String name) {
        try {
            return ModelTypeEnum.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
    
    // 添加getJavaTemplates方法，返回空的HashMap作为占位符实现
    public Map<String, String> getJavaTemplates(String name) {
        return new HashMap<>();
    }
    
    // 添加getConfigTemplates方法，返回空的HashMap作为占位符实现
    public Map<String, String> getConfigTemplates(String name) {
        return new HashMap<>();
    }
}
