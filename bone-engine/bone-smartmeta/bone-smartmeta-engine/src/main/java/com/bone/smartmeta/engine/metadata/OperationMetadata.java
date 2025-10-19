package com.bone.smartmeta.engine.metadata;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.ArrayList;
import java.util.List;

/**
 * 操作元数据 - 定义业务操作的行为规范
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationMetadata {
    private String name; // 操作名称，如 "submitPurchaseOrder"
    
    private String entityName; // 目标实体，如 "PurchaseOrder"
    
    private String label; // 显示标签，如 "提交采购订单"
    
    private String description;
    
    private OperationType type; // 操作类型
    
    @Builder.Default
    private List<OperationParameter> parameters = new ArrayList<>();
    
    @Builder.Default
    private List<OperationStep> steps = new ArrayList<>();
    
    @Builder.Default
    private List<OperationCondition> preconditions = new ArrayList<>();
    
    @Builder.Default
    private List<OperationCondition> postconditions = new ArrayList<>();
    
    private String successMessage;
    private String errorMessage;
    
    @Builder.Default
    private boolean async = false;
    
    @Builder.Default
    private Integer timeout = 30; // 超时时间(秒)
    
    @Builder.Default
    private boolean transactional = true;
    
    public enum OperationType {
        CREATE,
        UPDATE,
        DELETE,
        CUSTOM,
        WORKFLOW,
        BATCH
    }
}