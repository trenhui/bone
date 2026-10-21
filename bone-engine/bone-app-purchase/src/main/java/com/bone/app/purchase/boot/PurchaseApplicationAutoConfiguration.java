package com.bone.app.purchase.boot;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

/** 采购应用自动配置（Spring Boot 3 自动装配入口）。 */
@AutoConfiguration
@Import(PurchaseComponentScanConfiguration.class)
public class PurchaseApplicationAutoConfiguration {}
