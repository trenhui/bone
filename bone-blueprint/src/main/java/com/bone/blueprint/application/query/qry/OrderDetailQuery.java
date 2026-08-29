package com.bone.blueprint.application.query.qry;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 订单详情查询对象。
 *
 * <p>不可变（构造器注入），与 {@link PaymentDetailQuery} 保持一致风格；避免 setter 带来的可变状态与调用方 构造方式不统一。
 */
@Getter
@RequiredArgsConstructor
public class OrderDetailQuery {

  private final Long orderId;
}
