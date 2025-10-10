package com.bone.smartmeta.engine.metadata;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * AI代理元数据模型类
 */
@Getter
@Setter
public class AgentMetadata implements Iterable<AgentMetadata> {
    
    // 代理名称
    private String name;
    
    // 代理描述
    private String description;
    
    // 代理角色
    private String role;
    
    // 系统提示
    private String systemPrompt;
    
    // 模型名称
    private String modelName;
    
    // 温度参数
    private double temperature = 0.7;
    
    // 最大令牌数
    private int maxTokens = 1000;
    
    // 是否启用
    private boolean enabled = true;
    
    // 触发器
    private String trigger;
    
    // 动作
    private String action;
    
    // 代理类型
    private String type;
    
    // 子代理列表
    private List<AgentMetadata> children = new ArrayList<>();
    
    /**
     * 获取触发器
     */
    public String getTrigger() {
        return this.trigger;
    }
    
    /**
     * 获取动作
     */
    public String getAction() {
        return this.action;
    }
    
    /**
     * 获取代理类型
     */
    public String getType() {
        return this.type;
    }
    
    /**
     * 添加子代理
     */
    public void add(AgentMetadata agent) {
        this.children.add(agent);
    }
    
    /**
     * 获取子代理列表
     */
    public List<AgentMetadata> getChildren() {
        return this.children;
    }
    
    /**
     * 实现Iterable接口，支持for-each循环遍历子代理
     */
    @Override
    public Iterator<AgentMetadata> iterator() {
        return this.children.iterator();
    }
}