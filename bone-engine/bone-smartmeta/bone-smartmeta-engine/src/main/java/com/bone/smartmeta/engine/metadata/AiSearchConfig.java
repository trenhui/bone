package com.bone.smartmeta.engine.metadata;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * AI搜索配置模型类
 */
@Getter
@Setter
public class AiSearchConfig {
    
    // 是否启用AI搜索
    private boolean enabled = false;
    
    // 搜索权重字段列表
    private List<SearchWeightField> weightFields = new ArrayList<>();
    
    // 搜索向量维度
    private int vectorDimension = 768;
    
    // 相似度阈值
    private double similarityThreshold = 0.8;
}

/**
 * 搜索权重字段模型类
 */
@Getter
@Setter
class SearchWeightField {
    
    // 字段API名称
    private String fieldApiName;
    
    // 权重值
    private double weight = 1.0;
}