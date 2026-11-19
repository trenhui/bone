package com.bone.procurement.governance;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.procurement.governance.application.AiGovernanceProperties;
import com.bone.procurement.governance.application.ConditionalOnAiGovernanceEnabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/** 能力开关门禁：默认关闭（fail-closed），装配条件必须是显式 enabled=true。 */
class AiGovernanceToggleTest {

  @Test
  void 能力开关默认关闭() {
    AiGovernanceProperties properties = new AiGovernanceProperties();
    assertThat(properties.isEnabled()).as("治理能力开关必须默认关闭（fail-closed）").isFalse();
  }

  @Test
  void 组合注解的装配条件为显式开启() {
    ConditionalOnProperty condition =
        ConditionalOnAiGovernanceEnabled.class.getAnnotation(ConditionalOnProperty.class);
    assertThat(condition).as("缺少 @ConditionalOnProperty").isNotNull();
    assertThat(condition.prefix()).isEqualTo("bone.procurement.ai-governance");
    assertThat(condition.name()).containsExactly("enabled");
    assertThat(condition.havingValue()).isEqualTo("true");
    assertThat(condition.matchIfMissing()).as("属性缺失时不得装配").isFalse();
  }

  @Test
  void 配置属性前缀与开关键一致() {
    assertThat(AiGovernanceProperties.PREFIX).isEqualTo("bone.procurement.ai-governance");
    assertThat(AiGovernanceProperties.ENABLED_KEY)
        .isEqualTo("bone.procurement.ai-governance.enabled");
  }
}
