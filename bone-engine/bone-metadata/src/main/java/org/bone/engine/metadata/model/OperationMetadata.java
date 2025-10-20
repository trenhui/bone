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
 * 操作元数据模型 - 定义实体的业务操作
 * 支持标准CRUD操作、自定义业务操作、审批操作等
 * 
 * @author Bone Engine Team
 */
@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class OperationMetadata {

    // ================ 核心属性 ================
    
    /**
     * 操作名称
     */
    private String name;
    
    /**
     * 操作标签
     */
    private String label;
    
    /**
     * 操作描述
     */
    private String description;
    
    /**
     * 操作类型
     */
    private String type;
    
    /**
     * 操作参数定义
     */
    private List<OperationParameter> parameters;
    
    /**
     * 前置条件表达式
     */
    private List<String> preconditions;
    
    /**
     * 执行步骤
     */
    private List<ExecutionStep> executionSteps;
    
    /**
     * 权限配置
     */
    private OperationPermission permission;
    
    /**
     * UI配置
     */
    private OperationUIMetadata uiMetadata;
    
    /**
     * 是否异步执行
     */
    private Boolean async;
    
    /**
     * 是否事务性操作
     */
    private Boolean transactional;
    
    /**
     * 触发事件
     */
    private List<String> triggerEvents;
    
    /**
     * 扩展属性
     */
    private Map<String, Object> extensions;
    
    // ================ 构造方法与辅助方法 ================
    
    public OperationMetadata() {
        this.type = "CUSTOM";
        this.parameters = new ArrayList<>();
        this.preconditions = new ArrayList<>();
        this.executionSteps = new ArrayList<>();
        this.permission = new OperationPermission();
        this.uiMetadata = new OperationUIMetadata();
        this.async = Boolean.FALSE;
        this.transactional = Boolean.TRUE;
        this.triggerEvents = new ArrayList<>();
        this.extensions = new HashMap<>();
    }
    
    /**
     * 添加操作参数
     */
    public OperationMetadata addParameter(OperationParameter parameter) {
        if (this.parameters == null) {
            this.parameters = new ArrayList<>();
        }
        this.parameters.add(parameter);
        return this;
    }
    
    /**
     * 添加前置条件
     */
    public OperationMetadata addPrecondition(String precondition) {
        if (this.preconditions == null) {
            this.preconditions = new ArrayList<>();
        }
        this.preconditions.add(precondition);
        return this;
    }
    
    /**
     * 添加执行步骤
     */
    public OperationMetadata addExecutionStep(ExecutionStep step) {
        if (this.executionSteps == null) {
            this.executionSteps = new ArrayList<>();
        }
        this.executionSteps.add(step);
        return this;
    }
    
    /**
     * 添加触发事件
     */
    public OperationMetadata addTriggerEvent(String event) {
        if (this.triggerEvents == null) {
            this.triggerEvents = new ArrayList<>();
        }
        this.triggerEvents.add(event);
        return this;
    }
    
    /**
     * 检查是否为查询操作
     */
    public boolean isQueryOperation() {
        return "QUERY".equals(this.type);
    }
    
    /**
     * 检查是否为保存操作
     */
    public boolean isSaveOperation() {
        return "SAVE".equals(this.type);
    }
    
    /**
     * 检查是否为删除操作
     */
    public boolean isDeleteOperation() {
        return "DELETE".equals(this.type);
    }
    
    /**
     * 检查是否为审批操作
     */
    public boolean isApprovalOperation() {
        return "APPROVAL".equals(this.type);
    }
    
    /**
     * 操作参数内部类
     */
    @Data
    @Accessors(chain = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class OperationParameter {
        
        /**
         * 参数名称
         */
        private String name;
        
        /**
         * 参数类型
         */
        private String type;
        
        /**
         * 是否必填
         */
        private Boolean required;
        
        /**
         * 默认值
         */
        private Object defaultValue;
        
        /**
         * 参数描述
         */
        private String description;
    }
    
    /**
     * 执行步骤内部类
     */
    @Data
    @Accessors(chain = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ExecutionStep {
        
        /**
         * 步骤名称
         */
        private String name;
        
        /**
         * 步骤类型
         */
        private String type;
        
        /**
         * 步骤内容/表达式
         */
        private String content;
        
        /**
         * 失败处理策略
         */
        private String onError;
        
        /**
         * 异步执行
         */
        private Boolean async;
    }
    
    /**
     * 操作权限内部类
     */
    @Data
    @Accessors(chain = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class OperationPermission {
        
        /**
         * 允许的角色列表
         */
        private List<String> allowedRoles;
        
        /**
         * 拒绝的角色列表
         */
        private List<String> deniedRoles;
        
        /**
         * 动态权限表达式
         */
        private String permissionExpression;
        
        public OperationPermission() {
            this.allowedRoles = new ArrayList<>();
            this.deniedRoles = new ArrayList<>();
        }
    }
    
    /**
     * 操作UI元数据内部类
     */
    @Data
    @Accessors(chain = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class OperationUIMetadata {
        
        /**
         * 是否在列表中显示
         */
        private Boolean showInList;
        
        /**
         * 是否在详情中显示
         */
        private Boolean showInDetail;
        
        /**
         * 操作图标
         */
        private String icon;
        
        /**
         * 操作样式
         */
        private String style;
        
        /**
         * 确认消息
         */
        private String confirmationMessage;
        
        public OperationUIMetadata() {
            this.showInList = Boolean.FALSE;
            this.showInDetail = Boolean.TRUE;
            this.icon = "default";
            this.style = "primary";
        }
    }
}