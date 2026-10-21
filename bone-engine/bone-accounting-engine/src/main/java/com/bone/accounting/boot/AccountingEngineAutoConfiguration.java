package com.bone.accounting.boot;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

/**
 * 会计引擎自动配置（Spring Boot 3 自动装配入口）。
 *
 * <p>按官方最佳实践，自动配置类本身<strong>不启用组件扫描</strong>；组件注册职责委托给独立的 {@link
 * AccountingComponentScanConfiguration}（扫描 {@code com.bone.accounting}，且排除本 boot 包以防自扫描重复注册）。
 *
 * <p>本模块是<strong>可复用的横向能力</strong>：{@code voucherGenerateHandler} 按 (来源实体, 触发动作) 从 {@code
 * fin_voucher_template} 取规则生成凭证，与具体业务无关。报销、采购等应用模块只需在动作定义里 声明 {@code
 * handler_bean=voucherGenerateHandler} 即可复用，差异全在配置数据（模板/科目映射）。
 */
@AutoConfiguration
@Import(AccountingComponentScanConfiguration.class)
public class AccountingEngineAutoConfiguration {}
