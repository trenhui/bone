package com.bone.blueprint.adapter.web.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

/**
 * 订单列表行响应（adapter 层出参，对齐《Bone-DDD》§23.1 {@code *Resp}）。
 */
@Data
@Builder
public class OrderSummaryResp {
    private Long id;
    private Long customerId;
    private BigDecimal totalAmount;
    private String status;
    private LocalDateTime createdAt;
}
