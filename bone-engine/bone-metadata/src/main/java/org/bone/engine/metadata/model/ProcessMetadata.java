package org.bone.engine.metadata.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 流程元数据模型 - 定义业务流程配置
 * 支持顺序流程、并行流程、状态机流程等
 * 
 * @author Bone Engine Team
 */
@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProcessMetadata {

    // ================ 核心属性 ================
    
    /**
     * 流程名称
     */
    private String name;
    
    /**
     * 流程描述
     */
    private String description;
    
    /**
     * 流程类型
     */
    private String type;
    
    /**
     * 流程节点列表
     */
    private List<ProcessNode> nodes;
    
    /**
     * 流程转换列表
     */
    private List<ProcessTransition> transitions;
    
    /**
     * 流程变量定义
     */
    private Map<String, ProcessVariable> variables;
    
    /**
     * 触发表达式
     */
    private String triggerExpression;
    
    /**
     * 触发事件列表
     */
    private List<String> triggerEvents;
    
    /**
     * 是否事务性
     */
    private Boolean transactional;
    
    /**
     * 超时设置
     */
    private Integer timeout;
    
    /**
     * 流程版本
     */
    private String version;
    
    /**
     * 初始节点ID
     */
    private String initialNodeId;
    
    /**
     * 流程UI配置
     */
    private ProcessUIMetadata uiMetadata;
    
    // ================ 构造方法与辅助方法 ================
    
    public ProcessMetadata() {
        this.type = "SEQUENTIAL";
        this.nodes = new ArrayList<>();
        this.transitions = new ArrayList<>();
        this.variables = new HashMap<>();
        this.triggerEvents = new ArrayList<>();
        this.transactional = Boolean.TRUE;
        this.timeout = 0;
        this.version = "1.0.0";
        this.uiMetadata = new ProcessUIMetadata();
    }
    
    /**
     * 添加流程节点
     */
    public ProcessMetadata addNode(ProcessNode node) {
        if (this.nodes == null) {
            this.nodes = new ArrayList<>();
        }
        this.nodes.add(node);
        return this;
    }
    
    /**
     * 添加流程转换
     */
    public ProcessMetadata addTransition(ProcessTransition transition) {
        if (this.transitions == null) {
            this.transitions = new ArrayList<>();
        }
        this.transitions.add(transition);
        return this;
    }
    
    /**
     * 添加流程变量
     */
    public ProcessMetadata addVariable(String name, ProcessVariable variable) {
        if (this.variables == null) {
            this.variables = new HashMap<>();
        }
        this.variables.put(name, variable);
        return this;
    }
    
    /**
     * 添加触发事件
     */
    public ProcessMetadata addTriggerEvent(String event) {
        if (this.triggerEvents == null) {
            this.triggerEvents = new ArrayList<>();
        }
        this.triggerEvents.add(event);
        return this;
    }
    
    /**
     * 获取指定ID的节点
     */
    public ProcessNode findNodeById(String nodeId) {
        if (this.nodes != null) {
            for (ProcessNode node : this.nodes) {
                if (nodeId.equals(node.getId())) {
                    return node;
                }
            }
        }
        return null;
    }
    
    /**
     * 检查是否为顺序流程
     */
    public boolean isSequential() {
        return "SEQUENTIAL".equals(this.type);
    }
    
    /**
     * 检查是否为并行流程
     */
    public boolean isParallel() {
        return "PARALLEL".equals(this.type);
    }
    
    /**
     * 检查是否为状态机流程
     */
    public boolean isStateMachine() {
        return "STATE_MACHINE".equals(this.type);
    }
    
    /**
     * 流程节点内部类
     */
    @Data
    @Accessors(chain = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ProcessNode {
        
        /**
         * 节点ID
         */
        private String id;
        
        /**
         * 节点名称
         */
        private String name;
        
        /**
         * 节点类型
         */
        private String type;
        
        /**
         * 节点配置
         */
        private Map<String, Object> config;
        
        /**
         * 审批人表达式
         */
        private String assigneeExpression;
        
        /**
         * 超时配置
         */
        private Integer timeout;
        
        /**
         * 节点UI配置
         */
        private NodeUIMetadata uiMetadata;
        
        public ProcessNode() {
            this.type = "TASK";
            this.config = new HashMap<>();
            this.timeout = 0;
            this.uiMetadata = new NodeUIMetadata();
        }
    }
    
    /**
     * 流程转换内部类
     */
    @Data
    @Accessors(chain = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ProcessTransition {
        
        /**
         * 转换ID
         */
        private String id;
        
        /**
         * 转换名称
         */
        private String name;
        
        /**
         * 源节点ID
         */
        private String sourceNodeId;
        
        /**
         * 目标节点ID
         */
        private String targetNodeId;
        
        /**
         * 转换条件
         */
        private String condition;
        
        /**
         * 转换类型
         */
        private String type;
        
        public ProcessTransition() {
            this.type = "NORMAL";
        }
    }
    
    /**
     * 流程变量内部类
     */
    @Data
    @Accessors(chain = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ProcessVariable {
        
        /**
         * 变量类型
         */
        private String type;
        
        /**
         * 默认值
         */
        private Object defaultValue;
        
        /**
         * 变量描述
         */
        private String description;
        
        /**
         * 是否必填
         */
        private Boolean required;
        
        public ProcessVariable() {
            this.required = Boolean.FALSE;
        }
    }
    
    /**
     * 流程UI元数据内部类
     */
    @Data
    @Accessors(chain = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ProcessUIMetadata {
        
        /**
         * 是否显示流程图
         */
        private Boolean showDiagram;
        
        /**
         * 流程图布局
         */
        private String diagramLayout;
        
        /**
         * 是否显示历史记录
         */
        private Boolean showHistory;
        
        public ProcessUIMetadata() {
            this.showDiagram = Boolean.TRUE;
            this.diagramLayout = "TOP_DOWN";
            this.showHistory = Boolean.TRUE;
        }
    }
    
    /**
     * 节点UI元数据内部类
     */
    @Data
    @Accessors(chain = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class NodeUIMetadata {
        
        /**
         * 节点颜色
         */
        private String color;
        
        /**
         * 节点形状
         */
        private String shape;
        
        /**
         * 节点图标
         */
        private String icon;
        
        public NodeUIMetadata() {
            this.color = "default";
            this.shape = "RECTANGLE";
            this.icon = "default";
        }
    }
}