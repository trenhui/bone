package com.bone.blueprint.domain.model.channelbuyer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 渠道买家映射聚合纯单测（R8：无容器、直接验证领域不变量）。
 *
 * <p>不变量清单：
 *
 * <ol>
 *   <li>映射键（渠道买家ID）不可为空 —— 空值会把不同买家合并成同一条记录；
 *   <li>影子态客户ID恒为 0，{@code isBound()} 为假；
 *   <li>绑定目标客户ID必须为正数（0 是「未绑定」保留值，绑到 0 等于没绑）；
 *   <li>解绑必须退回影子态并清空客户名快照，否则列表里会出现「未绑定却带客户名」的误导行；
 *   <li>{@code recordOrder()} 单调累计笔数，供运营按活跃度排优先级。
 * </ol>
 */
class ChannelBuyerTest {

  private static ChannelBuyer shadow(long id) {
    return ChannelBuyer.observe(id, 0L, "TAOBAO", "buyer-1", "昵称A");
  }

  @Test
  void observeCreatesShadowMapping() {
    ChannelBuyer buyer = shadow(1L);

    assertEquals("TAOBAO", buyer.getChannelCode());
    assertEquals("buyer-1", buyer.getChannelBuyerId());
    assertEquals(ChannelBuyer.UNBOUND_CUSTOMER_ID, buyer.getCustomerId());
    assertEquals(BindingSource.AUTO_SHADOW.name(), buyer.getBindingSource());
    assertFalse(buyer.isBound(), "影子映射不算已绑定");
    assertTrue(buyer.isShadow());
    assertEquals(0, buyer.getOrderCount());
    assertEquals(0L, buyer.getTenantId());
  }

  @Test
  void observeRejectsBlankBuyerId() {
    // 映射键为空会把「没传」与「传了空串」混成一条，不同渠道买家会被合并。
    assertThrows(
        IllegalArgumentException.class, () -> ChannelBuyer.observe(2L, 0L, "JD", "  ", null));
    assertThrows(
        IllegalArgumentException.class, () -> ChannelBuyer.observe(3L, 0L, "JD", null, null));
  }

  @Test
  void bindToMarksManualAndKeepsNameSnapshot() {
    ChannelBuyer buyer = shadow(4L);

    buyer.bindTo(2001L, "张三");

    assertTrue(buyer.isBound());
    assertEquals(2001L, buyer.getCustomerId());
    assertEquals("张三", buyer.getCustomerName());
    assertEquals(BindingSource.MANUAL.name(), buyer.getBindingSource());
  }

  @Test
  void bindToRejectsNonPositiveCustomerId() {
    ChannelBuyer buyer = shadow(5L);

    assertThrows(IllegalArgumentException.class, () -> buyer.bindTo(0L, "零"));
    assertThrows(IllegalArgumentException.class, () -> buyer.bindTo(-1L, "负"));
    assertThrows(IllegalArgumentException.class, () -> buyer.bindTo(null, "空"));
    assertFalse(buyer.isBound(), "绑定失败后必须仍是影子态，不允许半写状态");
  }

  @Test
  void rebindOverwritesPreviousCustomer() {
    ChannelBuyer buyer = shadow(6L);
    buyer.bindTo(2001L, "张三");

    // 改绑是运营日常动作（售后申诉/对账差异），必须允许覆盖，且来源仍为人工。
    buyer.bindTo(2002L, "李四");

    assertEquals(2002L, buyer.getCustomerId());
    assertEquals("李四", buyer.getCustomerName());
    assertEquals(BindingSource.MANUAL.name(), buyer.getBindingSource());
  }

  @Test
  void unbindFallsBackToShadowAndClearsCustomerName() {
    ChannelBuyer buyer = shadow(7L);
    buyer.bindTo(2001L, "张三");

    buyer.unbind();

    assertFalse(buyer.isBound());
    assertEquals(ChannelBuyer.UNBOUND_CUSTOMER_ID, buyer.getCustomerId());
    assertNull(buyer.getCustomerName(), "解绑后残留客户名会让运营误判为已绑定");
    assertEquals(BindingSource.AUTO_SHADOW.name(), buyer.getBindingSource());
  }

  @Test
  void recordOrderAccumulatesAndStampsLastOrderAt() {
    ChannelBuyer buyer = shadow(8L);

    buyer.recordOrder();
    buyer.recordOrder();
    buyer.recordOrder();

    assertEquals(3, buyer.getOrderCount());
    assertNotNull(buyer.getLastOrderAt(), "首次拉单后必须有最近时间，否则运营无法按活跃度排序");
  }

  @Test
  void refreshNickDoesNotChangeIdentity() {
    ChannelBuyer buyer = shadow(9L);
    buyer.bindTo(2001L, "张三");

    buyer.refreshNick("新昵称");

    assertEquals("新昵称", buyer.getChannelBuyerNick());
    assertEquals("buyer-1", buyer.getChannelBuyerId(), "改昵称不得影响映射键");
    assertEquals(2001L, buyer.getCustomerId());
  }
}
