package com.bone.smartmeta.engine.example;

import com.bone.smartmeta.engine.MetadataEngine;
import com.bone.smartmeta.engine.metadata.*;
import com.bone.smartmeta.engine.service.GenericOperationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 操作框架示例类
 * 演示如何使用通用操作服务
 */
@Component
public class OperationExample {
    
    @Autowired
    private MetadataEngine metadataEngine;
    
    @Autowired
    private GenericOperationService operationService;
    
    @Autowired
    private OperationRegistry operationRegistry;
    
    /**
     * 创建示例操作元数据
     */
    public void createExampleOperation() {
        // 创建实体元数据（如果不存在）
        EntityMetadata entityMetadata = new EntityMetadata();
        entityMetadata.setId("purchase_order");
        entityMetadata.setName("PurchaseOrder");
        entityMetadata.setApiName("purchaseOrder");
        entityMetadata.setTitle("采购订单");
        
        // 创建字段元数据
        FieldMetadata idField = new FieldMetadata();
        idField.setName("id");
        idField.setLabel("订单ID");
        idField.setDataType("String");
        
        FieldMetadata statusField = new FieldMetadata();
        statusField.setName("status");
        statusField.setLabel("状态");
        statusField.setDataType("String");
        
        entityMetadata.getFields().put("id", idField);
        entityMetadata.getFields().put("status", statusField);
        
        // 注册实体元数据
        metadataEngine.registerEntity(entityMetadata);
        
        // 创建操作元数据
        OperationMetadata submitOperation = createSubmitOperation();
        
        // 注册操作元数据
        operationRegistry.registerOperation(submitOperation);
        metadataEngine.registerOperation(submitOperation);
        
        System.out.println("示例操作元数据创建成功: " + submitOperation.getName());
    }
    
    /**
     * 创建提交订单操作
     */
    private OperationMetadata createSubmitOperation() {
        OperationMetadata operation = new OperationMetadata();
        operation.setName("submitOrder");
        operation.setEntityName("PurchaseOrder");
        operation.setTitle("提交订单");
        operation.setDescription("将采购订单提交审核");
        operation.setOperationType("BUSINESS_ACTION");
        operation.setSuccessMessage("订单提交成功");
        operation.setErrorMessage("订单提交失败");
        
        // 添加前置条件
        OperationCondition precondition = new OperationCondition();
        precondition.setExpression("targetEntity.status == 'DRAFT'");
        precondition.setErrorMessage("只有草稿状态的订单才能提交");
        precondition.setConditionType(ConditionType.PRECONDITION);
        operation.getPreconditions().add(precondition);
        
        // 添加操作步骤
        OperationStep updateStep = new OperationStep();
        updateStep.setName("updateStatus");
        updateStep.setDescription("更新订单状态");
        updateStep.setType(StepType.DATA_UPDATE);
        updateStep.setTargetEntity("PurchaseOrder");
        updateStep.setOrder(1);
        updateStep.setRequired(true);
        
        // 设置步骤参数
        Map<String, Object> updateParams = new HashMap<>();
        updateParams.put("entityId", "${entityId}");
        updateParams.put("status", "SUBMITTED");
        updateParams.put("submittedAt", "${now}");
        updateParams.put("submittedBy", "${operator}");
        updateStep.setParameters(updateParams);
        
        operation.getSteps().add(updateStep);
        
        // 添加通知步骤
        OperationStep notificationStep = new OperationStep();
        notificationStep.setName("sendNotification");
        notificationStep.setDescription("发送通知");
        notificationStep.setType(StepType.NOTIFICATION);
        notificationStep.setOrder(2);
        notificationStep.setRequired(false);
        
        Map<String, Object> notificationParams = new HashMap<>();
        notificationParams.put("type", "EMAIL");
        notificationParams.put("message", "订单 ${entityId} 已提交，请审核");
        notificationStep.setParameters(notificationParams);
        
        operation.getSteps().add(notificationStep);
        
        // 添加后置条件
        OperationCondition postcondition = new OperationCondition();
        postcondition.setExpression("targetEntity.status == 'SUBMITTED'");
        postcondition.setErrorMessage("订单状态更新失败");
        postcondition.setConditionType(ConditionType.POSTCONDITION);
        operation.getPostconditions().add(postcondition);
        
        return operation;
    }
    
    /**
     * 执行示例操作
     */
    public OperationResult executeExampleOperation(String orderId) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("remark", "常规订单");
        
        Map<String, Object> context = new HashMap<>();
        context.put("businessType", "purchase");
        context.put("companyId", "001");
        
        // 执行操作
        OperationResult result = operationService.execute("submitOrder", orderId, parameters, context);
        
        System.out.println("操作执行结果: " + result.isSuccess());
        if (result.isSuccess()) {
            System.out.println("成功消息: " + result.getMessage());
        } else {
            System.out.println("错误消息: " + result.getMessage());
        }
        
        return result;
    }
    
    /**
     * 列出所有可用操作
     */
    public List<OperationMetadata> listAllOperations() {
        List<OperationMetadata> operations = operationRegistry.getAllOperations();
        System.out.println("可用操作数量: " + operations.size());
        
        for (OperationMetadata operation : operations) {
            System.out.println("- " + operation.getEntityName() + "." + 
                operation.getName() + ": " + operation.getDescription());
        }
        
        return operations;
    }
    
    /**
     * 演示操作执行流程
     */
    public void demonstrateOperationFlow() {
        // 1. 创建示例操作
        createExampleOperation();
        
        // 2. 列出所有操作
        listAllOperations();
        
        // 3. 执行操作（这里只是示例，实际执行需要有对应的订单数据）
        String orderId = "PO123456";
        System.out.println("\n准备执行操作 submitOrder 针对订单: " + orderId);
        
        // 注意：实际执行前，需要先在DynamicDataService中创建对应的订单数据
        // 这里只是演示执行流程
        try {
            OperationResult result = executeExampleOperation(orderId);
            System.out.println("操作执行完成，结果: " + result);
        } catch (Exception e) {
            System.out.println("操作执行出错: " + e.getMessage());
        }
    }
}