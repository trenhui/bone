package com.bone.blueprint.application.query.qry;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 支付单详情查询对象。 */
@Getter
@RequiredArgsConstructor
public class PaymentDetailQuery {

  private final Long paymentId;
}
