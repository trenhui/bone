package com.bone.masterdata.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.masterdata.adapter.web.controller.SupplierController;
import com.bone.masterdata.application.SupplierApplicationService;
import com.bone.masterdata.testsupport.MetadataSdkIntegrationTestConfiguration;
import com.bone.masterdata.testsupport.supplier.SupplierHttpTestConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * 供应商主数据切片的能力开关<b>关闭态</b>装配探针。
 *
 * <p>刻意仍导入 {@link SupplierHttpTestConfiguration}：即使测试夹具把四个出站端口都提供齐了，开关默认 false 时编排服务与 HTTP 端点
 * 都不许注册。这证明的是「开关本身就是判据」，而不是「没 bean 所以装配不上」的巧合。
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = "bone.procurement.supplier-mdm.enabled=false")
@ActiveProfiles("test")
@Import({MetadataSdkIntegrationTestConfiguration.class, SupplierHttpTestConfiguration.class})
class SupplierCapabilityGateTest {

  @Autowired private ApplicationContext context;

  @Test
  @DisplayName("开关关闭时编排服务与 Controller 均不装配")
  void disabledCapabilityShouldNotExposeBeans() {
    assertThat(context.getBeanNamesForType(SupplierApplicationService.class)).isEmpty();
    assertThat(context.getBeanNamesForType(SupplierController.class)).isEmpty();
  }
}
