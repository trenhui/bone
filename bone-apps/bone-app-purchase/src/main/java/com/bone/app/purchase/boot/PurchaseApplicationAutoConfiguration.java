package com.bone.app.purchase.boot;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * 采购应用自动配置（Spring Boot 3 自动装配入口）。
 *
 * <p>导入 {@link PurchaseComponentScanConfiguration}（扫描 {@code com.bone.app.purchase}
 * 的业务子包；付款处理器已在其中被排除）。 submit/approve/reject 的工作流逃逸 Handler 以 {@code @Service} 形式由 ComponentScan
 * 自动注册。
 *
 * <p><b>D-06 硬隔离装配</b>：启用 {@link PaymentIsolationProperties}；{@link
 * PayHandlerConditionalRegistration} 按开关单点注册付款处理器； 始终注册 {@link LegacyPaymentIsolationReporter}
 * 输出启动期隔离状态日志。
 */
@AutoConfiguration
@Import({PurchaseComponentScanConfiguration.class, PayHandlerConditionalRegistration.class})
@EnableConfigurationProperties(PaymentIsolationProperties.class)
public class PurchaseApplicationAutoConfiguration {

  /** 始终注册：无论开关状态，启动期都输出隔离状态日志（可观察证据）。 */
  @Bean
  public LegacyPaymentIsolationReporter legacyPaymentIsolationReporter(
      PaymentIsolationProperties properties) {
    return new LegacyPaymentIsolationReporter(properties);
  }
}
