package com.bone.app.expense.boot;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

/** 报销应用自动配置（Spring Boot 3 自动装配入口）。组件扫描委托给 {@link ExpenseComponentScanConfiguration}。 */
@AutoConfiguration
@Import(ExpenseComponentScanConfiguration.class)
public class ExpenseApplicationAutoConfiguration {}
