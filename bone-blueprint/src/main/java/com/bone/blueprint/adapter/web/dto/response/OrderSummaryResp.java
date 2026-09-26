package com.bone.blueprint.adapter.web.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import lombok.Builder;
import lombok.Data;

/**
 * 订单列表行响应（adapter 层出参，对齐《Bone-DDD》E-13.1 {@code *Resp}）。
 *
 * <p>时间字段用 {@link Instant}（带 {@code Z} 偏移）而非 {@code LocalDateTime}，理由见 {@link
 * com.bone.blueprint.adapter.web.dto.response.OrderDetailResp} 的类注释（i18n 方案 §6.4）。
 */
@Data
@Builder
public class OrderSummaryResp {
  private Long id;
  private Long customerId;
  private BigDecimal totalAmount;
  private String status;
  private Instant createdAt;
}
