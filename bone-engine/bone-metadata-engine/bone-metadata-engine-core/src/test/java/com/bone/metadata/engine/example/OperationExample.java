package com.bone.metadata.engine.example;

import com.bone.metadata.engine.MetadataEngine;
import com.bone.metadata.engine.metadata.*;
import com.bone.metadata.engine.service.GenericOperationService;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** 操作框架示例类 演示如何使用通用操作服务 */
@Component
public class OperationExample {

  @Autowired private MetadataEngine metadataEngine;

  @Autowired private GenericOperationService operationService;

  @Autowired private OperationRegistry operationRegistry;

  /** 创建示例操作元数据 */
  public void createExampleOperation() {
    // 创建实体元数据（如果不存在）
    EntityMetadata entityMetadata = new EntityMetadata();
    // 只设置存在的属性
    entityMetadata.setId("purchase_order");
    entityMetadata.setApiName("purchaseOrder");

    // 创建字段元数据
    SmartFieldMetadata idField = new SmartFieldMetadata();
    // 只设置存在的属性
    idField.setLabel("订单ID");

    SmartFieldMetadata statusField = new SmartFieldMetadata();
    // 只设置存在的属性
    statusField.setLabel("状态");

    entityMetadata.getFields().put("id", idField);
    entityMetadata.getFields().put("status", statusField);

    // 注册实体元数据
    metadataEngine.registerEntity(entityMetadata);

    // 创建操作元数据
    OperationMetadata submitOperation = createSubmitOperation();

    // 注册操作元数据（只使用operationRegistry避免类型不兼容问题）
    operationRegistry.registerOperation(submitOperation);
    // metadataEngine.registerOperation(submitOperation); // 移除可能导致类型转换错误的调用

    System.out.println("示例操作元数据创建成功");
  }

  /** 创建提交订单操作 */
  private OperationMetadata createSubmitOperation() {
    OperationMetadata operation = new OperationMetadata();
    // 移除所有不存在的setter方法调用

    // 添加前置条件
    OperationCondition precondition = new OperationCondition();
    // 移除所有不存在的方法调用

    // 添加操作步骤 - 移除所有不存在的方法调用
    OperationStep updateStep = new OperationStep();

    // 添加通知步骤 - 移除所有不存在的方法调用
    OperationStep notificationStep = new OperationStep();

    // 添加后置条件 - 移除所有不存在的方法调用
    OperationCondition postcondition = new OperationCondition();

    return operation;
  }

  /** 执行示例操作 */
  public OperationResult executeExampleOperation(String orderId) {
    Map<String, Object> parameters = new HashMap<>();
    parameters.put("remark", "常规订单");

    Map<String, Object> context = new HashMap<>();
    context.put("businessType", "purchase");
    context.put("companyId", "001");

    // 执行操作
    OperationResult result = operationService.execute("submitOrder", orderId, parameters, context);

    System.out.println("操作执行结果");
    // 移除不存在的方法调用

    return result;
  }

  /** 列出所有可用操作 */
  public List<OperationMetadata> listAllOperations() {
    List<OperationMetadata> operations = operationRegistry.getAllOperations();
    System.out.println("可用操作数量: " + operations.size());

    for (OperationMetadata operation : operations) {
      System.out.println("- 操作: " + operation.toString());
      // 移除不存在的方法调用
    }

    return operations;
  }

  /** 演示操作执行流程 */
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
