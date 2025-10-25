package com.bone.example.extension.medical;

/**
 * 理赔类型枚举
 * 定义不同类型的医疗理赔场景
 */
public enum ClaimType {
    /**
     * 门诊理赔
     */
    OUTPATIENT,
    
    /**
     * 住院理赔
     */
    INPATIENT,
    
    /**
     * 特殊门诊理赔（如慢性病门诊）
     */
    SPECIAL_OUTPATIENT,
    
    /**
     * 急诊理赔
     */
    EMERGENCY,
    
    /**
     * 意外伤害理赔
     */
    ACCIDENT
}