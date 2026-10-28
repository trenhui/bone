package com.bone.app.purchase.boot;

import com.bone.app.purchase.workflow.PurchaseWorkflowDecisionHandler;
import com.bone.app.purchase.workflow.PurchaseWorkflowSubmitHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

/**
 * 采购应用自动配置（Spring Boot 3 自动装配入口）。
 *
 * <p>除 {@link PurchaseComponentScanConfiguration} 扫描采购应用组件外，显式导入工作流逃逸 Handler， 保证
 * submit/approve/reject 的 handler_bean 在 bone-boot 组合根中一定注册（不依赖 ComponentScan 递归路径）。
 */
@AutoConfiguration
@Import({
  PurchaseComponentScanConfiguration.class,
  PurchaseWorkflowSubmitHandler.class,
  PurchaseWorkflowDecisionHandler.class
})
public class PurchaseApplicationAutoConfiguration {}
