package com.bone.blueprint.adapter.web.assembler;

import com.bone.blueprint.adapter.web.dto.request.CreateOrderReq;
import com.bone.blueprint.adapter.web.dto.response.OrderDetailResp;
import com.bone.blueprint.adapter.web.dto.response.OrderSummaryResp;
import com.bone.blueprint.application.command.CancelOrderCommand;
import com.bone.blueprint.application.command.CreateOrderCommand;
import com.bone.blueprint.application.command.DeliverOrderCommand;
import com.bone.blueprint.application.command.ShipOrderCommand;
import com.bone.blueprint.application.query.dto.OrderDto;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface OrderAssembler {

  /**
   * 读模型（{@code LocalDateTime}，按 UTC 归一）→ 对外契约（{@code Instant}，序列化带 {@code Z}）。
   *
   * <p>MapStruct 不会凭空猜时区，必须显式给一条转换方法；这里固定 {@link ZoneOffset#UTC}—— 与 {@code
   * OrderHeadProjection.from()} 的归一口径一致，避免"读模型按 UTC 存、出口按系统时区转"的错位。
   */
  default Instant toInstant(LocalDateTime value) {
    return value == null ? null : value.toInstant(ZoneOffset.UTC);
  }

  CreateOrderCommand toCreateOrderCommand(CreateOrderReq request);

  OrderSummaryResp toOrderSummaryResp(OrderDto orderDto);

  default CancelOrderCommand toCancelOrderCommand(Long orderId) {
    // HTTP 入口：租户由请求上下文提供，命令不携带（定时任务入口才显式传 tenantId）
    return new CancelOrderCommand(orderId, null);
  }

  default ShipOrderCommand toShipOrderCommand(Long orderId) {
    return new ShipOrderCommand(orderId);
  }

  default DeliverOrderCommand toDeliverOrderCommand(Long orderId) {
    return new DeliverOrderCommand(orderId);
  }

  OrderDetailResp toOrderDetailResp(OrderDto orderDto);
}
