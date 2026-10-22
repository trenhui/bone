package com.bone.app.purchase.boot;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

/** 采购应用组件扫描：扫描 {@code com.bone.app.purchase}，排除本 boot 包避免自扫描重复注册。 */
@Configuration
@ComponentScan(
    basePackages = "com.bone.app.purchase",
    excludeFilters =
        @ComponentScan.Filter(
            type = FilterType.REGEX,
            pattern = "com\\.bone\\.app\\.purchase\\.boot\\..*"))
public class PurchaseComponentScanConfiguration {}
