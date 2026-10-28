package com.bone.app.purchase.boot;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

/**
 * 采购应用自动配置（Spring Boot 3 自动装配入口）。
 *
 * <p>导入 {@link PurchaseComponentScanConfiguration}（扫描 {@code com.bone.app.purchase} 全部子包，含 {@code
 * workflow}）。 submit/approve/reject 的工作流逃逸 Handler 以 {@code @Service} 形式由 ComponentScan 自动注册，
 * 无需在此显式导入——新增 Handler 也会被自动发现。
 */
@AutoConfiguration
@Import(PurchaseComponentScanConfiguration.class)
public class PurchaseApplicationAutoConfiguration {}
