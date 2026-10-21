package com.bone.accounting.boot;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

/**
 * 会计引擎组件扫描配置：扫描 {@code com.bone.accounting}（业务 Handler/Hook）， 并<strong>排除本 boot
 * 包</strong>，避免自动配置类被重复扫描注册。
 */
@Configuration
@ComponentScan(
    basePackages = "com.bone.accounting",
    excludeFilters =
        @ComponentScan.Filter(
            type = FilterType.REGEX,
            pattern = "com\\.bone\\.accounting\\.boot\\..*"))
public class AccountingComponentScanConfiguration {}
