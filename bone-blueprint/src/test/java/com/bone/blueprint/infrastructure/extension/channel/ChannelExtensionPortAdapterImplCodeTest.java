package com.bone.blueprint.infrastructure.extension.channel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.bone.engine.extension.api.model.definition.ExtensionDefinition;
import com.bone.engine.extension.core.register.ExtensionRegister;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

/**
 * {@link ChannelExtensionPortAdapter#resolveImplCode} 的行为锁定。
 *
 * <p>本测试的存在理由：旧实现用两个 {@code switch} 从渠道码反推implCode（{@code TAOBAO→TB}），新增渠道忘记改 switch 就落 {@code
 * DEFAULT}，而<b>渠道路由不受影响</b> —— 属静默失真，只能靠台账发现。改为读注册表后，implCode 必须是各扩展类 {@code @Extension(name=...)}
 * 的真值。
 */
class ChannelExtensionPortAdapterImplCodeTest {

  private static final String ORDER_POINT = ExtensionChannelOrderExtPoint.class.getName();
  private static final String PRODUCT_POINT = ExtensionChannelProductExtPoint.class.getName();
  private static final String LOGISTICS_POINT = ExtensionChannelFulfillmentExtPoint.class.getName();

  /**
   * 构造一条已注册扩展。
   *
   * <p>{@code isValid()} 要求 code/ extensionPoint / instance 三者皆备，故必须给 instance（真实注册时是扩展实现 bean）。
   *
   * @param channel 渠道码；传 null 表示默认实现（无 channel 维度，走 L4 兜底）
   */
  private static ExtensionDefinition def(
      String extPoint, String code, String channel, String family) {
    ExtensionDefinition.Builder builder =
        ExtensionDefinition.builder()
            .code(code)
            .extensionPoint(extPoint)
            .implementationClass("com.bone.blueprint.Fake" + family)
            .instance(new Object());
    if (channel != null) {
      builder.dimension("channel", channel);
    }
    return builder.build();
  }

  /**
   * 构造一个带完整注册表（4 渠道 × 3 族 + 3 个默认实现）的适配器。
   *
   * <p>注意：{@code def(...)} 必须在 {@code when(...)} 之外求值——在 {@code thenReturn} 内调用其它 mock 相关方法会触发
   * Mockito 的 UnfinishedStubbingException。
   */
  private static ChannelExtensionPortAdapter adapterWithRegistry() {
    List<ExtensionDefinition> orderDefs =
        List.of(
            def(ORDER_POINT, "TB_CHANNEL_ORDER_EXT", "TAOBAO", "Order"),
            def(ORDER_POINT, "JD_CHANNEL_ORDER_EXT", "JD", "Order"),
            def(ORDER_POINT, "DY_CHANNEL_ORDER_EXT", "DOUYIN", "Order"),
            def(ORDER_POINT, "PDD_CHANNEL_ORDER_EXT", "PDD", "Order"),
            def(ORDER_POINT, "DEFAULT_CHANNEL_ORDER_EXT", null, "Order"));
    List<ExtensionDefinition> productDefs =
        List.of(
            def(PRODUCT_POINT, "TB_CHANNEL_PRODUCT_EXT", "TAOBAO", "Product"),
            def(PRODUCT_POINT, "JD_CHANNEL_PRODUCT_EXT", "JD", "Product"),
            def(PRODUCT_POINT, "DY_CHANNEL_PRODUCT_EXT", "DOUYIN", "Product"),
            def(PRODUCT_POINT, "PDD_CHANNEL_PRODUCT_EXT", "PDD", "Product"),
            def(PRODUCT_POINT, "DEFAULT_CHANNEL_PRODUCT_EXT", null, "Product"));
    List<ExtensionDefinition> logisticsDefs =
        List.of(
            def(LOGISTICS_POINT, "TB_CHANNEL_LOGISTICS_EXT", "TAOBAO", "Logistics"),
            def(LOGISTICS_POINT, "JD_CHANNEL_LOGISTICS_EXT", "JD", "Logistics"),
            def(LOGISTICS_POINT, "DY_CHANNEL_LOGISTICS_EXT", "DOUYIN", "Logistics"),
            def(LOGISTICS_POINT, "PDD_CHANNEL_LOGISTICS_EXT", "PDD", "Logistics"),
            def(LOGISTICS_POINT, "DEFAULT_CHANNEL_LOGISTICS_EXT", null, "Logistics"));

    ExtensionRegister register = org.mockito.Mockito.mock(ExtensionRegister.class);
    when(register.findExtensionsByPoint(ORDER_POINT)).thenReturn(orderDefs);
    when(register.findExtensionsByPoint(PRODUCT_POINT)).thenReturn(productDefs);
    when(register.findExtensionsByPoint(LOGISTICS_POINT)).thenReturn(logisticsDefs);

    return adapterWith(register);
  }

  private static ChannelExtensionPortAdapter adapterWith(ExtensionRegister register) {
    @SuppressWarnings("unchecked")
    ObjectProvider<ExtensionRegister> provider = org.mockito.Mockito.mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(register);
    return new ChannelExtensionPortAdapter(null, null, null, provider);
  }

  private static ChannelExtensionPortAdapter adapterWithoutRegister() {
    @SuppressWarnings("unchecked")
    ObjectProvider<ExtensionRegister> provider = org.mockito.Mockito.mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(null);
    return new ChannelExtensionPortAdapter(null, null, null, provider);
  }

  @Test
  @DisplayName("已注册渠道返回其 @Extension name 真值，而非拼字符串反推的缩写")
  void 已注册渠道返回注册真值() {
    ChannelExtensionPortAdapter adapter = adapterWithRegistry();
    adapter.buildImplCodeIndex();

    assertEquals("TB_CHANNEL_ORDER_EXT", adapter.resolveImplCode("TAOBAO", "PULL_ORDER"));
    assertEquals("DY_CHANNEL_PRODUCT_EXT", adapter.resolveImplCode("DOUYIN", "LIST_PRODUCT"));
    assertEquals("PDD_CHANNEL_LOGISTICS_EXT", adapter.resolveImplCode("PDD", "PUSH_SHIPMENT"));
  }

  @Test
  @DisplayName("同一渠道在不同能力族下解析到各自族的实现")
  void 同渠道跨族解析独立() {
    ChannelExtensionPortAdapter adapter = adapterWithRegistry();
    adapter.buildImplCodeIndex();

    assertEquals("TB_CHANNEL_ORDER_EXT", adapter.resolveImplCode("TAOBAO", "PULL_ORDER"));
    assertEquals("TB_CHANNEL_ORDER_EXT", adapter.resolveImplCode("TAOBAO", "ACK_ORDER"));
    assertEquals("TB_CHANNEL_PRODUCT_EXT", adapter.resolveImplCode("TAOBAO", "SYNC_INVENTORY"));
    assertEquals("TB_CHANNEL_LOGISTICS_EXT", adapter.resolveImplCode("TAOBAO", "QUERY_TRACE"));
  }

  /**
   * 核心回归：旧 switch 实现对这个用例会返回 {@code DEFAULT_CHANNEL_ORDER_EXT}（漏了 KUAISHOU），
   * 新实现必须返回注册表里的真值。这是「新增渠道不必改适配器」的可执行证据。
   */
  @Test
  @DisplayName("旧 switch 未覆盖的新渠道（如 KUAISHOU）也能解析出真实 code")
  void 新增渠道无需改适配器() {
    List<ExtensionDefinition> defs =
        List.of(
            def(ORDER_POINT, "TB_CHANNEL_ORDER_EXT", "TAOBAO", "Order"),
            // 模拟「刚加的第 5 个渠道」：只在注册表登记，适配器代码里没有它的任何分支
            def(ORDER_POINT, "KS_CHANNEL_ORDER_EXT", "KUAISHOU", "Order"),
            def(ORDER_POINT, "DEFAULT_CHANNEL_ORDER_EXT", null, "Order"));
    ExtensionRegister register = org.mockito.Mockito.mock(ExtensionRegister.class);
    when(register.findExtensionsByPoint(ORDER_POINT)).thenReturn(defs);

    ChannelExtensionPortAdapter adapter = adapterWith(register);
    adapter.buildImplCodeIndex();

    assertEquals("KS_CHANNEL_ORDER_EXT", adapter.resolveImplCode("KUAISHOU", "PULL_ORDER"));
    // 负向断言：若代码退回 switch 拼接，这里会得到 DEFAULT_...
    assertNotEquals("DEFAULT_CHANNEL_ORDER_EXT", adapter.resolveImplCode("KUAISHOU", "PULL_ORDER"));
  }

  @Test
  @DisplayName("未注册渠道落兜底 code（与扩展引擎 L4 兜底实际命中的默认实现一致）")
  void 未注册渠道落兜底() {
    ChannelExtensionPortAdapter adapter = adapterWithRegistry();
    adapter.buildImplCodeIndex();

    assertEquals(
        "DEFAULT_CHANNEL_ORDER_EXT", adapter.resolveImplCode("UNKNOWN_CHANNEL", "PULL_ORDER"));
    assertEquals("DEFAULT_CHANNEL_PRODUCT_EXT", adapter.resolveImplCode(null, "LIST_PRODUCT"));
  }

  @Test
  @DisplayName("注册表缺席时不抛异常，全部落兜底（扩展 starter 缺席的部署仍可启动）")
  void 注册表缺席时降级() {
    ChannelExtensionPortAdapter adapter = adapterWithoutRegister();
    adapter.buildImplCodeIndex();

    // 注册表缺席 ⇒ 无从查真值⇒ 一律落兜底，且不得抛异常（否则扩展 starter 缺席的部署直接启动失败）
    assertEquals("DEFAULT_CHANNEL_ORDER_EXT", adapter.resolveImplCode("TAOBAO", "PULL_ORDER"));
    assertEquals("DEFAULT_CHANNEL_LOGISTICS_EXT", adapter.resolveImplCode("TAOBAO", "QUERY_TRACE"));
  }

  @Test
  @DisplayName("未建索引（PostConstruct 未跑）也不抛异常")
  void 未建索引时降级() {
    ChannelExtensionPortAdapter adapter = adapterWithoutRegister();

    assertEquals("DEFAULT_CHANNEL_ORDER_EXT", adapter.resolveImplCode("TAOBAO", "PULL_ORDER"));
    assertTrue(adapter.resolveImplCode("TAOBAO", "PUSH_SHIPMENT").startsWith("DEFAULT_CHANNEL_"));
  }
}
