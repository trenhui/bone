package com.bone.blueprint.adapter.rpc.dto.response;

import lombok.Data;

/**
 * RPC 创建订单响应。协议由包路径声明（{@code adapter/rpc/dto/response/}），类名只表达业务语义（E-13.0）—— 与 web 侧同名 DTO 并存合法：DTO
 * 不注册为 Spring bean，同名不产生容器冲突。
 */
@Data
public class CreateOrderResp {
  private Long orderId;
  private String status;
  private boolean success;
  private String errorMsg;
}
