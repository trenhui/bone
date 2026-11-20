package com.bone.app.purchase.boot;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

/**
 * 采购应用组件扫描：扫描 {@code com.bone.app.purchase}，排除本 boot 包避免自扫描重复注册。
 *
 * <p><b>D-06 硬隔离</b>：额外排除 {@code PurchaseOrderPayHandler}，使其不再由扫描无条件注册，改由 {@link
 * PayHandlerConditionalRegistration} 按 {@code procura.purchase-pay-handler.enabled}
 * 开关单点注册（缺省禁用）。这样开关成为该 bean 的唯一注册入口， 且不必改动存量处理器类本身（遵守"退役前不得删改存量类"）。
 */
@Configuration
@ComponentScan(
    basePackages = "com.bone.app.purchase",
    excludeFilters = {
      @ComponentScan.Filter(
          type = FilterType.REGEX,
          pattern = "com\\.bone\\.app\\.purchase\\.boot\\..*"),
      @ComponentScan.Filter(
          type = FilterType.REGEX,
          pattern = "com\\.bone\\.app\\.purchase\\.PurchaseOrderPayHandler")
    })
public class PurchaseComponentScanConfiguration {}
