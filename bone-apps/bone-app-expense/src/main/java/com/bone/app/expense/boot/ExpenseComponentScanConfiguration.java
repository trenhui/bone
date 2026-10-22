package com.bone.app.expense.boot;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

/** 报销应用组件扫描：扫描 {@code com.bone.app.expense}，排除本 boot 包避免自扫描重复注册。 */
@Configuration
@ComponentScan(
    basePackages = "com.bone.app.expense",
    excludeFilters =
        @ComponentScan.Filter(
            type = FilterType.REGEX,
            pattern = "com\\.bone\\.app\\.expense\\.boot\\..*"))
public class ExpenseComponentScanConfiguration {}
