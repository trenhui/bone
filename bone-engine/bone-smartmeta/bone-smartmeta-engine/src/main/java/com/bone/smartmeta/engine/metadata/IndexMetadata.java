package com.bone.smartmeta.engine.metadata;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 索引元数据模型类
 */
@Getter
@Setter
public class IndexMetadata {
    
    // 索引名称
    private String name;
    
    // 索引字段列表
    private List<IndexField> fields = new ArrayList<>();
    
    // 是否为唯一索引
    private boolean unique = false;
    
    // 索引类型
    private String indexType;
    
    // 是否启用
    private boolean enabled = true;
}