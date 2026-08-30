package com.bone.core.capability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;

/**
 * {@link HandlerRegistry} 测试：能力注册、注解属性透传、重名去重与快照不可变。
 *
 * <p>该注册表是 Flow / AI 编排发现 Handler 的入口，注册信息（幂等/重试/超时）直接影响编排决策， 属性一旦丢失会导致编排行为错误，故固化。
 */
class HandlerRegistryTest {

  @Test
  void initRegistersAllAnnotatedBeansWithFullAttributes() {
    try (GenericApplicationContext ctx = new GenericApplicationContext()) {
      ctx.registerBean("alphaHandler", AlphaHandler.class);
      ctx.registerBean("betaHandler", BetaHandler.class);
      ctx.refresh();

      HandlerRegistry registry = new HandlerRegistry(ctx);
      registry.init();

      assertThat(registry.getAllCapabilities()).hasSize(2);

      Optional<HandlerRegistry.CapabilityRegistration> alpha =
          registry.findCapability("AlphaCapability");
      assertThat(alpha).isPresent();
      assertThat(alpha.get().getName()).isEqualTo("AlphaCapability");
      assertThat(alpha.get().getDescription()).isEqualTo("示例能力 A");
      assertThat(alpha.get().getInputSchema()).isEqualTo("{\"orderId\": \"long\"}");
      assertThat(alpha.get().getOutputSchema()).isEqualTo("{\"result\": \"string\"}");
      // @Capability 显式属性必须原样透传（编排侧据此做幂等 / 重试 / 超时决策）
      assertThat(alpha.get().isIdempotent()).isTrue();
      assertThat(alpha.get().isRetryable()).isFalse();
      assertThat(alpha.get().getCost()).isEqualTo(3);
      assertThat(alpha.get().getTimeout()).isEqualTo(45);
      assertThat(alpha.get().getDeclaringClass()).contains("AlphaHandler");
    }
  }

  @Test
  void defaultAnnotationValuesAreApplied() {
    try (GenericApplicationContext ctx = new GenericApplicationContext()) {
      ctx.registerBean("betaHandler", BetaHandler.class);
      ctx.refresh();

      HandlerRegistry registry = new HandlerRegistry(ctx);
      registry.init();

      HandlerRegistry.CapabilityRegistration beta =
          registry.findCapability("BetaCapability").orElseThrow();

      // 注解默认值：idempotent=false, cost=1, retryable=true, timeout=30
      assertThat(beta.isIdempotent()).isFalse();
      assertThat(beta.isRetryable()).isTrue();
      assertThat(beta.getCost()).isEqualTo(1);
      assertThat(beta.getTimeout()).isEqualTo(30);
    }
  }

  @Test
  void missingCapabilityReturnsEmptyAndNull() {
    try (GenericApplicationContext ctx = new GenericApplicationContext()) {
      ctx.refresh();
      HandlerRegistry registry = new HandlerRegistry(ctx);
      registry.init();

      assertThat(registry.findCapability("Nope")).isEmpty();
      // 兼容旧调用：返回 null 而非 Optional（新代码应用 findCapability）
      assertThat(registry.getCapability("Nope")).isNull();
    }
  }

  /** 重名注册必须保留首个、忽略后续（避免编排侧路由到错误实现）。 */
  @Test
  void duplicateCapabilityNameKeepsFirstRegistration() {
    try (GenericApplicationContext ctx = new GenericApplicationContext()) {
      ctx.registerBean("first", AlphaHandler.class);
      ctx.registerBean("second", DuplicateNameHandler.class);
      ctx.refresh();

      HandlerRegistry registry = new HandlerRegistry(ctx);
      registry.init();

      assertThat(registry.getAllCapabilities()).hasSize(1);
      assertThat(registry.findCapability("AlphaCapability").orElseThrow().getDeclaringClass())
          .contains("AlphaHandler");
    }
  }

  @Test
  void snapshotIsUnmodifiable() {
    try (GenericApplicationContext ctx = new GenericApplicationContext()) {
      ctx.registerBean("alphaHandler", AlphaHandler.class);
      ctx.refresh();
      HandlerRegistry registry = new HandlerRegistry(ctx);
      registry.init();

      assertThatThrownBy(() -> registry.snapshot().clear())
          .isInstanceOf(UnsupportedOperationException.class);
    }
  }

  /**
   * @Value 生成的 equals/hashCode 为全字段值比较——用于注册信息快照比对。
   */
  @Test
  void registrationEqualityAndToString() {
    HandlerRegistry.CapabilityRegistration a = registration("X");
    HandlerRegistry.CapabilityRegistration b = registration("X");
    HandlerRegistry.CapabilityRegistration c = registration("Y");

    assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    assertThat(a).isNotEqualTo(c);
    assertThat(a.toString()).contains("X");
  }

  private static HandlerRegistry.CapabilityRegistration registration(String name) {
    return HandlerRegistry.CapabilityRegistration.builder()
        .name(name)
        .description("d")
        .inputSchema("{}")
        .outputSchema("{}")
        .idempotent(false)
        .cost(1)
        .retryable(true)
        .timeout(30)
        .declaringClass("SomeHandler")
        .build();
  }

  @Capability(
      name = "AlphaCapability",
      description = "示例能力 A",
      inputSchema = "{\"orderId\": \"long\"}",
      outputSchema = "{\"result\": \"string\"}",
      idempotent = true,
      cost = 3,
      retryable = false,
      timeout = 45)
  public static class AlphaHandler {}

  @Capability(
      name = "BetaCapability",
      description = "示例能力 B",
      inputSchema = "{}",
      outputSchema = "{}")
  public static class BetaHandler {}

  @Capability(
      name = "AlphaCapability",
      description = "与 Alpha 重名",
      inputSchema = "{}",
      outputSchema = "{}")
  public static class DuplicateNameHandler {}
}
