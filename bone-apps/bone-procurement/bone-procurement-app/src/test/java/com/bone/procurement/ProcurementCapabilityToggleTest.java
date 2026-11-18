package com.bone.procurement;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.procurement.fulfillment.adapter.web.controller.FulfillmentController;
import com.bone.procurement.transaction.adapter.web.controller.PurchaseOrderController;
import com.bone.procurement.transaction.adapter.web.controller.RequisitionController;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * 能力开关门禁：两个 Controller 必须声明 @ConditionalOnProperty(enabled, havingValue=true)， 且 application.yml 默认
 * false。三者缺一即「未配置环境暴露半成品端点」。
 */
class ProcurementCapabilityToggleTest {

  @Test
  void 交易控制器都受能力开关守护() {
    for (Class<?> c : new Class<?>[] {RequisitionController.class, PurchaseOrderController.class}) {
      ConditionalOnProperty p = c.getAnnotation(ConditionalOnProperty.class);
      assertThat(p).as(c.getSimpleName() + " 缺 @ConditionalOnProperty").isNotNull();
      assertThat(p.prefix()).isEqualTo("bone.procurement.transaction");
      assertThat(p.name()).containsExactly("enabled");
      assertThat(p.havingValue()).isEqualTo("true");
    }
  }

  @Test
  void 履约控制器受独立能力开关守护() {
    ConditionalOnProperty p =
        FulfillmentController.class.getAnnotation(ConditionalOnProperty.class);
    assertThat(p).as("FulfillmentController 缺 @ConditionalOnProperty").isNotNull();
    assertThat(p.prefix()).isEqualTo("bone.procurement.fulfillment");
    assertThat(p.name()).containsExactly("enabled");
    assertThat(p.havingValue()).isEqualTo("true");
  }

  @Test
  void 默认关闭交易与履约能力开关() throws IOException {
    Path yml = Path.of("src/main/resources/application.yml");
    String content = Files.readString(yml);
    assertThat(content).contains("enabled: ${BONE_PROCUREMENT_TRANSACTION_ENABLED:false}");
    assertThat(content).contains("enabled: ${BONE_PROCUREMENT_FULFILLMENT_ENABLED:false}");
  }
}
