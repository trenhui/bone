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
 * 供应商主数据切片的能力开关<b>开启态</b>装配探针：开关打开且四个出站端口齐备时，编排服务与 Controller 各恰好装配一份。
 *
 * <p>与 {@link SupplierCapabilityGateTest} 成对存在：两条用例合起来才能证明「开关是判据」——只有关闭态用例时，
 * 把注解删掉也会通过（配置成了死代码却无人发现）。
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = "bone.procurement.supplier-mdm.enabled=true")
@ActiveProfiles("test")
@Import({MetadataSdkIntegrationTestConfiguration.class, SupplierHttpTestConfiguration.class})
class SupplierCapabilityEnabledTest {

  @Autowired private ApplicationContext context;

  @Test
  @DisplayName("开关开启且端口齐备时编排服务与 Controller 各装配一份")
  void enabledCapabilityShouldExposeBeans() {
    assertThat(context.getBeanNamesForType(SupplierApplicationService.class)).hasSize(1);
    assertThat(context.getBeanNamesForType(SupplierController.class)).hasSize(1);
  }
}
