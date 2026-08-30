package com.bone.core.domain.extension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * 动态扩展能力测试：{@link Extensible} 的 default 方法语义 + {@link ExtensibleObject} 载体。
 *
 * <p>扩展属性是元数据驱动的动态字段底座（EAV 场景），类型安全取值与 JSON 兜底反序列化一旦出错， 会静默取到 null 或错误类型，故逐条固化。
 */
class ExtensibleTest {

  @Test
  void putAndGetProperty() {
    ExtensibleObject<Long> obj = new ExtensibleObject<>();

    obj.putExtraProperty("finance.taxRate", "0.06");

    assertThat(obj.getExtraProperty("finance.taxRate")).isEqualTo("0.06");
    assertThat(obj.getExtraProperty("finance.taxRate", String.class)).contains("0.06");
    assertThat(obj.containsExtraProperty("finance.taxRate")).isTrue();
    assertThat(obj.containsExtraProperty("missing")).isFalse();
    assertThat(obj.getExtraPropertyKeys()).containsExactly("finance.taxRate");
  }

  @Test
  void putRejectsBlankKey() {
    ExtensibleObject<Long> obj = new ExtensibleObject<>();

    assertThatThrownBy(() -> obj.putExtraProperty(null, "v"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> obj.putExtraProperty("   ", "v"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void mergeIgnoresNullOrEmpty() {
    ExtensibleObject<Long> obj = new ExtensibleObject<>();

    obj.mergeExtraProperties(null);
    obj.mergeExtraProperties(Map.of());
    assertThat(obj.getExtraPropertyKeys()).isEmpty();

    obj.mergeExtraProperties(Map.of("a", 1, "b", 2));
    assertThat(obj.getExtraPropertyKeys()).containsExactlyInAnyOrder("a", "b");
  }

  /** 简单 JDK 类型（非集合/接口）不做 JSON 兜底：类型不匹配返回 empty，不抛异常。 */
  @Test
  void typedAccessReturnsEmptyOnTypeMismatch() {
    ExtensibleObject<Long> obj = new ExtensibleObject<>();
    obj.putExtraProperty("n", "not-a-number");

    assertThat(obj.getExtraProperty("n", Integer.class)).isEmpty();
    assertThat(obj.getExtraProperty("missing", String.class)).isEmpty();
  }

  @Test
  void typedAccessFallsBackToDefaultValue() {
    ExtensibleObject<Long> obj = new ExtensibleObject<>();

    assertThat(obj.getExtraProperty("missing", String.class, "fallback")).isEqualTo("fallback");

    obj.putExtraProperty("k", "v");
    assertThat(obj.getExtraProperty("k", String.class, "fallback")).isEqualTo("v");
  }

  /** 枚举转换支持「按名称」与「按序数」两种来源。 */
  @Test
  void enumConversionByNameAndOrdinal() {
    ExtensibleObject<Long> obj = new ExtensibleObject<>();
    obj.putExtraProperty("byName", "GREEN");
    obj.putExtraProperty("byOrdinal", 1);

    assertThat(obj.getExtraProperty("byName", Color.class)).contains(Color.GREEN);
    assertThat(obj.getExtraProperty("byOrdinal", Color.class)).contains(Color.GREEN);
  }

  /** 无法解析的枚举值返回 empty（越界序数 / 未知名称），不抛异常。 */
  @Test
  void enumConversionReturnsEmptyWhenUnresolvable() {
    ExtensibleObject<Long> obj = new ExtensibleObject<>();
    obj.putExtraProperty("bad", "NO_SUCH_COLOR");
    obj.putExtraProperty("outOfRange", 99);

    assertThat(obj.getExtraProperty("bad", Color.class)).isEmpty();
    assertThat(obj.getExtraProperty("outOfRange", Color.class)).isEmpty();
  }

  /** 值已是目标枚举实例时直接返回，避免重复转换。 */
  @Test
  void enumValueAlreadyTypedIsReturnedAsIs() {
    ExtensibleObject<Long> obj = new ExtensibleObject<>();
    obj.putExtraProperty("c", Color.RED);

    assertThat(obj.getExtraProperty("c", Color.class)).contains(Color.RED);
  }

  @Test
  void jsonCollectionIsDeserialized() {
    ExtensibleObject<Long> obj = new ExtensibleObject<>();
    obj.putExtraProperty("list", "[1,2,3]");

    Optional<List> parsed = obj.getExtraProperty("list", List.class);

    assertThat(parsed).isPresent();
    assertThat(parsed.get()).hasSize(3);
  }

  /** JSON 反序列化失败必须降级为 empty，不能把异常抛给业务调用方。 */
  @Test
  void jsonParsingFailureFallsBackToEmpty() {
    ExtensibleObject<Long> obj = new ExtensibleObject<>();
    obj.putExtraProperty("broken", "{not-json}");

    assertThat(obj.getExtraProperty("broken", Map.class)).isEmpty();
  }

  @Test
  void propertyKeysViewIsUnmodifiable() {
    ExtensibleObject<Long> obj = new ExtensibleObject<>();
    obj.putExtraProperty("k", "v");

    assertThatThrownBy(() -> obj.getExtraPropertyKeys().add("x"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  @SuppressWarnings("unchecked")
  void extensibleObjectCarriesBizIdentityAndTenant() {
    ExtensibleObject<Long> obj = new ExtensibleObject<>();
    obj.setBizIdentityCode("order.online");
    obj.setTenantId(1001L);

    assertThat(obj.getBizIdentityCode()).isEqualTo("order.online");
    assertThat(obj.getTenantId()).isEqualTo(1001L);
    // 扩展属性容器默认可写
    assertThat((Map<String, Object>) obj.getExtraProperties()).isEmpty();
  }

  enum Color {
    RED,
    GREEN,
    BLUE
  }
}
