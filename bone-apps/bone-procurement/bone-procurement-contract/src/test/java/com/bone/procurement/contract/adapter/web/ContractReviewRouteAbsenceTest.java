package com.bone.procurement.contract.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.procurement.contract.adapter.web.controller.ContractReviewController;
import com.bone.procurement.contract.application.ConditionalOnContractAiReviewEnabled;
import com.bone.procurement.contract.application.ContractReviewApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * 关闭态路由不存在（不启完整 Spring，以条件注解单测锁定）：能力开关未显式开启时 Controller 不注册。
 *
 * <p>参照 SupplierCapabilityRouteAbsenceTest 范式。
 */
class ContractReviewRouteAbsenceTest {

  @Test
  void 控制器受能力开关条件保护() {
    ConditionalOnContractAiReviewEnabled conditional =
        ContractReviewController.class.getAnnotation(ConditionalOnContractAiReviewEnabled.class);
    assertThat(conditional).as("Controller 未挂能力开关条件注解").isNotNull();
  }

  @Test
  void 应用服务受同一能力开关条件保护() {
    assertThat(
            ContractReviewApplicationService.class.getAnnotation(
                ConditionalOnContractAiReviewEnabled.class))
        .isNotNull();
  }

  @Test
  void 条件语义为显式开启且缺省不匹配() {
    ConditionalOnProperty condition =
        ConditionalOnContractAiReviewEnabled.class.getAnnotation(ConditionalOnProperty.class);
    assertThat(condition.prefix()).isEqualTo("bone.procurement.contract-ai-review");
    assertThat(condition.name()).containsExactly("enabled");
    assertThat(condition.havingValue()).isEqualTo("true");
    assertThat(condition.matchIfMissing()).isFalse();
  }
}
