package com.bone.blueprint.infrastructure.extension.channel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bone.blueprint.domain.extension.channel.ChannelOrderContext;
import com.bone.blueprint.domain.extension.channel.ChannelOrderDraft;
import com.bone.blueprint.domain.extension.channel.ChannelOrderLine;
import com.bone.blueprint.infrastructure.channel.openapi.ChannelApiResult;
import com.bone.blueprint.infrastructure.channel.openapi.ChannelOpenApiClient;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 四个渠道订单扩展的<strong>归一化行为</strong>锁定。
 *
 * <p><b>为什么必须有这组测试</b>：拉单归一化是「渠道报文→ 领域 {@link ChannelOrderDraft}」的唯一入口， 且各渠道的 JSON 路径、单位换算、outerId
 * 取值全不同（淘宝 {@code trades.order}、京东 {@code order_list.order} 且分→元、 抖音 {@code shop_order_list}、拼多多
 * {@code order_list}）。这些逻辑此前<b>零测试覆盖</b> （{@code ChannelOpenApiContractTest} 只校验接口形状，不校验归一化结果），
 * 任何重构（含抽模板方法）都可能在单测全绿的情况下改坏金额或商品映射。
 *
 * <p>本组测试锁三件事：
 *
 * <ol>
 *   <li><b>渠道无关的不变式</b>：成功必有明细、金额口径一致、明细为空时回落入参而非静默通过；
 *   <li><b>渠道各自的映射</b>：报文路径与 outerId 提取规则；
 *   <li><b>失败语义</b>：渠道拒绝⇒ 抛业务异常（不静默返回空）。
 * </ol>
 */
class ChannelOrderExtensionNormalizationTest {

  private static final Long TENANT = 1L;

  private static ChannelOpenApiClient clientReturning(Map<String, Object> data) {
    ChannelOpenApiClient client = mock(ChannelOpenApiClient.class);
    when(client.call(any())).thenReturn(ChannelApiResult.ok(data, "{}"));
    return client;
  }

  private static ChannelOpenApiClient clientRejecting() {
    ChannelOpenApiClient client = mock(ChannelOpenApiClient.class);
    when(client.call(any())).thenReturn(new ChannelApiResult(false, null, "E_BUSY", "渠道繁忙", "{}"));
    return client;
  }

  private static Map<String, Object> map(Object... kv) {
    Map<String, Object> m = new HashMap<>();
    for (int i = 0; i < kv.length; i += 2) {
      m.put(String.valueOf(kv[i]), kv[i + 1]);
    }
    return m;
  }

  @SafeVarargs
  private static <T> List<T> list(T... items) {
    return List.of(items);
  }

  private static ChannelOrderContext ctx(List<ChannelOrderLine> lines) {
    return new ChannelOrderContext(
        TENANT,
        "ANY",
        "ORDER-1",
        "买家",
        "BUYER-1",
        new BigDecimal("100.00"),
        "张三",
        "13800000000",
        "上海市浦东新区",
        lines);
  }

  // ==================== 淘宝 ====================

  /**
   * 淘宝报文：{@code trades.order[]} → {@code orders[]}。
   *
   * <p><b>淘宝特有的两层结构</b>（与其它渠道三处不同，改动时务必留意）：
   *
   * <ol>
   *   <li>顶层路径 {@code trades.order} 是两段点号路径；
   *   <li>{@code price} 在 <b>trade 层</b>（{@code ChannelJson.decimal(trade, "price")}），不在 order 层；
   *   <li>{@code title} 优先取 order 层，缺失时回落 trade 层。
   * </ol>
   */
  private static Map<String, Object> taobaoPayload() {
    Map<String, Object> order = map("outer_item_id", "TB1001", "title", "淘宝商品A", "num", 2);
    Map<String, Object> trade =
        map("title", "淘宝交易标题", "price", new BigDecimal("12.50"), "orders", list(order));
    return map("trades", map("order", list(trade)));
  }

  @Test
  @DisplayName("淘宝：trades.order.orders 明细归一化 + outer_item_id 去掉 TB 前缀还原内部商品ID")
  void 淘宝拉单归一化() {
    TaobaoOrderExtension ext = new TaobaoOrderExtension(clientReturning(taobaoPayload()));
    ChannelOrderDraft draft = ext.pullOrder(ctx(List.of()));

    assertEquals("TAOBAO", draft.channelCode());
    assertEquals(1, draft.lines().size());
    ChannelOrderDraft.ChannelDraftLine line = draft.lines().get(0);
    // 关键断言：渠道编码 TB1001 必须被还原为内部商品ID 1001
    assertEquals(1001L, line.productId());
    assertEquals("淘宝商品A", line.productName());
    assertEquals(2, line.quantity());
    assertEquals(new BigDecimal("12.50"), line.unitPrice());
  }

  /** 淘宝「有 trade 但无明细」报文：{@code trades.order[]} 非空而 {@code orders[]} 为空。 */
  private static Map<String, Object> emptyTaobaoPayload() {
    return map("trades", map("order", list(map("orders", list()))));
  }

  @Test
  @DisplayName("淘宝：明细为空时回落入参 lines（联调通道），仍为空才抛错")
  void 淘宝明细回落入参() {
    TaobaoOrderExtension ext = new TaobaoOrderExtension(clientReturning(emptyTaobaoPayload()));
    ChannelOrderLine fallback = new ChannelOrderLine("TB2002", "入参商品", 1, new BigDecimal("5.00"));

    ChannelOrderDraft draft = ext.pullOrder(ctx(List.of(fallback)));
    assertEquals(1, draft.lines().size());
    assertEquals(2002L, draft.lines().get(0).productId());
  }

  @Test
  @DisplayName("淘宝：渠道与入参都无明细 ⇒ 抛错，不静默返回空草稿")
  void 淘宝无明细抛错() {
    TaobaoOrderExtension ext = new TaobaoOrderExtension(clientReturning(emptyTaobaoPayload()));
    IllegalArgumentException ex =
        assertThrows(IllegalArgumentException.class, () -> ext.pullOrder(ctx(List.of())));
    assertTrue(ex.getMessage().contains("无有效明细"), ex.getMessage());
  }

  // ==================== 京东（分 → 元） ====================

  /** 京东报文：{@code order_list.order[].orderLineList.orderLine[]}，金额单位「分」。 */
  private static Map<String, Object> jdPayload() {
    Map<String, Object> line =
        map(
            "skuInfo",
            map("outerId", "JD3003", "skuName", "京东商品B", "jdPrice", new BigDecimal("800")),
            "orderLineNumId",
            3);
    return map(
        "order_list",
        map(
            "order",
            list(
                map(
                    "orderFreight", new BigDecimal("500"),
                    "orderDiscount", new BigDecimal("120"),
                    "orderLineList", map("orderLine", list(line))))));
  }

  @Test
  @DisplayName("京东：分→元换算 + outerId 优先 + 行数量读取")
  void 京东拉单归一化() {
    JdOrderExtension ext = new JdOrderExtension(clientReturning(jdPayload()));
    ChannelOrderDraft draft = ext.pullOrder(ctx(List.of()));

    assertEquals("JD", draft.channelCode());
    assertEquals(1, draft.lines().size());
    ChannelOrderDraft.ChannelDraftLine line = draft.lines().get(0);
    assertEquals(3003L, line.productId());
    assertEquals("京东商品B", line.productName());
    assertEquals(3, line.quantity());
    // 800 分 = 8.00 元：若换算被改坏，这条断言会挂
    assertEquals(
        0, new BigDecimal("8.00").compareTo(line.unitPrice()), "unitPrice=" + line.unitPrice());
    // 运费 500 分 = 5.00 元，优惠 120 分 = 1.20 元
    assertEquals(
        0,
        new BigDecimal("5.00").compareTo(draft.freightAmount()),
        "freight=" + draft.freightAmount());
    assertEquals(
        0,
        new BigDecimal("1.20").compareTo(draft.discountAmount()),
        "discount=" + draft.discountAmount());
  }

  @Test
  @DisplayName("京东：无 outerId 时用 SKU_PREFIX+skuId 兜底")
  void 京东outerId兜底() {
    Map<String, Object> payload =
        map(
            "order_list",
            map(
                "order",
                list(
                    map(
                        "orderLineList",
                        map(
                            "orderLine",
                            list(
                                map(
                                    "skuId",
                                    4004,
                                    "skuName",
                                    "京东商品C",
                                    "skuPrice",
                                    new BigDecimal("100"))))))));

    JdOrderExtension ext = new JdOrderExtension(clientReturning(payload));
    ChannelOrderDraft draft = ext.pullOrder(ctx(List.of()));
    assertEquals(4004L, draft.lines().get(0).productId());
  }

  // ==================== 抖音 ====================

  /** 抖音报文：{@code shop_order_list[].product_list[]}，金额单位「分」。 */
  private static Map<String, Object> douyinPayload() {
    Map<String, Object> product =
        map(
            "outer_sku_id",
            "DY5005",
            "product_name",
            "抖音商品D",
            "quantity",
            1,
            "order_amount",
            new BigDecimal("2000"));
    return map(
        "shop_order_list",
        list(
            map(
                "logistics_amount", new BigDecimal("300"),
                "discount_amount", new BigDecimal("100"),
                "product_list", list(product))));
  }

  @Test
  @DisplayName("抖音：shop_order_list.product_list 归一化 + 分→元")
  void 抖音拉单归一化() {
    DouyinOrderExtension ext = new DouyinOrderExtension(clientReturning(douyinPayload()));
    ChannelOrderDraft draft = ext.pullOrder(ctx(List.of()));

    assertEquals("DOUYIN", draft.channelCode());
    ChannelOrderDraft.ChannelDraftLine line = draft.lines().get(0);
    assertEquals(5005L, line.productId());
    assertEquals("抖音商品D", line.productName());
    assertEquals(1, line.quantity());
    assertEquals(
        0, new BigDecimal("20.00").compareTo(line.unitPrice()), "unitPrice=" + line.unitPrice());
    assertEquals(
        0,
        new BigDecimal("3.00").compareTo(draft.freightAmount()),
        "freight=" + draft.freightAmount());
    assertEquals(
        0,
        new BigDecimal("1.00").compareTo(draft.discountAmount()),
        "discount=" + draft.discountAmount());
  }

  // ==================== 拼多多 ====================

  /** 拼多多报文：{@code order_list[].goods_list[]}，金额单位「分」。 */
  private static Map<String, Object> pddPayload() {
    Map<String, Object> goods =
        map(
            "outer_product_id",
            "PDD6006",
            "goods_name",
            "拼多多商品E",
            "goods_quantity",
            4,
            "goods_price",
            new BigDecimal("500"));
    return map(
        "order_list",
        list(
            map(
                "logistics_fee", new BigDecimal("200"),
                "discount_amount", new BigDecimal("50"),
                "goods_list", list(goods))));
  }

  @Test
  @DisplayName("拼多多：order_list.goods_list 归一化 + 分→元")
  void 拼多多拉单归一化() {
    PddOrderExtension ext = new PddOrderExtension(clientReturning(pddPayload()));
    ChannelOrderDraft draft = ext.pullOrder(ctx(List.of()));

    assertEquals("PDD", draft.channelCode());
    ChannelOrderDraft.ChannelDraftLine line = draft.lines().get(0);
    assertEquals(6006L, line.productId());
    assertEquals("拼多多商品E", line.productName());
    assertEquals(4, line.quantity());
    assertEquals(
        0, new BigDecimal("5.00").compareTo(line.unitPrice()), "unitPrice=" + line.unitPrice());
    assertEquals(
        0,
        new BigDecimal("2.00").compareTo(draft.freightAmount()),
        "freight=" + draft.freightAmount());
  }

  // ==================== 四渠道共通不变式 ====================

  @Test
  @DisplayName("四渠道：渠道返回失败 ⇒ 抛业务异常（不静默返回空草稿）")
  void 四渠道渠道拒绝均抛异常() {
    List<Runnable> calls =
        List.of(
            () -> new TaobaoOrderExtension(clientRejecting()).pullOrder(ctx(List.of())),
            () -> new JdOrderExtension(clientRejecting()).pullOrder(ctx(List.of())),
            () -> new DouyinOrderExtension(clientRejecting()).pullOrder(ctx(List.of())),
            () -> new PddOrderExtension(clientRejecting()).pullOrder(ctx(List.of())));

    for (Runnable call : calls) {
      RuntimeException ex = assertThrows(RuntimeException.class, call::run);
      // 必须是 CHANNEL_OPENAPI_REJECTED 这类业务异常，而不是 NPE 之类
      assertTrue(
          ex.getClass().getName().startsWith("com.bone"), "应抛业务异常，实际: " + ex.getClass().getName());
    }
  }

  @Test
  @DisplayName("四渠道：归一化结果必带渠道码与收货信息（骨架行为一致）")
  void 四渠道骨架行为一致() {
    record Case(String channel, ChannelOrderDraft draft) {}
    List<Case> cases =
        List.of(
            new Case(
                "TAOBAO",
                new TaobaoOrderExtension(clientReturning(taobaoPayload()))
                    .pullOrder(ctx(List.of()))),
            new Case(
                "JD", new JdOrderExtension(clientReturning(jdPayload())).pullOrder(ctx(List.of()))),
            new Case(
                "DOUYIN",
                new DouyinOrderExtension(clientReturning(douyinPayload()))
                    .pullOrder(ctx(List.of()))),
            new Case(
                "PDD",
                new PddOrderExtension(clientReturning(pddPayload())).pullOrder(ctx(List.of()))));

    for (Case c : cases) {
      ChannelOrderDraft d = c.draft();
      assertEquals(c.channel(), d.channelCode());
      assertEquals("ORDER-1", d.channelOrderNo());
      assertNotNull(d.lines());
      assertTrue(!d.lines().isEmpty(), c.channel() + " 明细不应为空");
      assertEquals("张三", d.receiverName());
      assertEquals("13800000000", d.receiverPhone());
      for (ChannelOrderDraft.ChannelDraftLine line : d.lines()) {
        assertNotNull(line.productId(), c.channel() + " 商品ID 不应为空");
        assertTrue(line.productId() > 0, c.channel() + " 商品ID 应为正数: " + line.productId());
      }
    }
  }
}
