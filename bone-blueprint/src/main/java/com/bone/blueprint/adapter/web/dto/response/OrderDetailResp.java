package com.bone.blueprint.adapter.web.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * 订单详情响应。
 *
 * <p><b>时间字段为何是 {@link Instant} 而不是 LocalDateTime</b>（i18n 方案 §6.4）： {@code LocalDateTime} 序列化后是
 * {@code 2026-09-25T12:00:00}——<b>不带时区偏移</b>， 前端只能猜它是 UTC 还是服务端本地时区，猜错就是静默的 ±8 小时偏差，且用户不可察觉。 {@code
 * Instant} 序列化为 {@code 2026-09-25T12:00:00Z}，语义自解释，前端按浏览器时区渲染即可。
 */
@Data
@Builder
public class OrderDetailResp {
  private Long id;
  private Long customerId;
  private BigDecimal totalAmount;
  private String status;
  private Instant createdAt;
  private List<OrderItemResp> items;

  @Data
  @Builder
  public static class OrderItemResp {
    private Long id;
    private Long productId;
    private String productName;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal subtotal;
  }
}
