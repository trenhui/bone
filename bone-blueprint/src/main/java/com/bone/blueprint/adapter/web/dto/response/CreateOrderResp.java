package com.bone.blueprint.adapter.web.dto.response;

import lombok.Builder;
import lombok.Data;

/** 创建订单响应（adapter 层出参，201 创建语义；强类型替代 Map）。 */
@Data
@Builder
public class CreateOrderResp {
  private Long id;
}
