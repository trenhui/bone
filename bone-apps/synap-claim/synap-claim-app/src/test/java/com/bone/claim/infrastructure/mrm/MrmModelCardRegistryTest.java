package com.bone.claim.infrastructure.mrm;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.claim.infrastructure.mrm.MrmModelCardRegistry.LoadStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;

/** 配置驱动 Registry：真实 classpath 空清单必须是 NO_MODEL_CARDS；缺失配置 fail-closed。 */
class MrmModelCardRegistryTest {

  @Test
  @DisplayName("classpath 真实初态为空清单：NO_MODEL_CARDS，psi 为空")
  void emptyClasspathConfig() {
    var registry = new MrmModelCardRegistry(new ClassPathResource("mrm-model-cards.json"));

    assertThat(registry.status()).isEqualTo(LoadStatus.NO_MODEL_CARDS);
    assertThat(registry.cards()).isEmpty();
    assertThat(registry.drift().psi()).isNull();
  }

  @Test
  @DisplayName("Spring 装配：@Value 的 classpath Resource 能解析成 Bean，默认即空清单")
  void springWiring() {
    new ApplicationContextRunner()
        .withUserConfiguration(MrmModelCardRegistry.class)
        .run(
            ctx -> {
              assertThat(ctx).hasSingleBean(MrmModelCardRegistry.class);
              assertThat(ctx.getBean(MrmModelCardRegistry.class).status())
                  .isEqualTo(LoadStatus.NO_MODEL_CARDS);
            });
  }

  @Test
  @DisplayName("配置缺失时 CONFIG_UNAVAILABLE（fail-closed，不冒充空清单健康）")
  void missingConfigFailsClosed() {
    var registry = new MrmModelCardRegistry(new ClassPathResource("__not_exist_mrm__.json"));

    assertThat(registry.status()).isEqualTo(LoadStatus.CONFIG_UNAVAILABLE);
    assertThat(registry.cards()).isEmpty();
    assertThat(registry.drift().psi()).isNull();
  }
}
