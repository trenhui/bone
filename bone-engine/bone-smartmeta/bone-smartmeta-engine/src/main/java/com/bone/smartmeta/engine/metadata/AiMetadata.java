package com.bone.smartmeta.engine.metadata;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI元数据模型类
 */
@Getter
@Setter
public class AiMetadata {
    
    // 是否启用AI功能
    private boolean enabled = false;
    
    // AI描述模板
    private String descriptionTemplate;
    
    // AI生成字段配置
    private List<AiGeneratedField> generatedFields = new ArrayList<>();
    
    // AI搜索配置
    private AiSearchConfig searchConfig = new AiSearchConfig();
    
    // 自定义AI属性
    private Map<String, Object> customProperties = new HashMap<>();
    
    // 代理式AI配置
    private AgentMetadata agenticAI;
    
    // 建议配置列表
    private List<AgentMetadata> suggestions = new ArrayList<>();
    
    /**
     * 获取代理式AI配置
     */
    public AgentMetadata getAgenticAI() {
        return this.agenticAI;
    }
    
    /**
     * 获取AI建议配置
     */
    public List<AgentMetadata> getSuggestions() {
        return this.suggestions;
    }
    
    /**
     * 设置AI建议配置
     */
    public void setSuggestions(List<AgentMetadata> suggestions) {
        this.suggestions = suggestions;
    }
}