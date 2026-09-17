package com.bone.blueprint.application.query.handler;

import com.bone.blueprint.application.port.out.TenantProvider;
import com.bone.blueprint.application.query.dto.PaymentDto;
import com.bone.blueprint.application.query.dto.PaymentProjection;
import com.bone.blueprint.application.query.port.PaymentQueryPort;
import com.bone.blueprint.application.query.qry.PaymentDetailQuery;
import com.bone.blueprint.application.query.support.PaymentDetailAssembler;
import com.bone.blueprint.common.BlueprintErrorCodes;
import com.bone.core.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 支付单详情查询：读侧端口直查（只读，无事务写）。 */
@Component
@RequiredArgsConstructor
public class PaymentDetailQueryHandler {

  private final PaymentQueryPort paymentQueryPort;
  private final TenantProvider tenantProvider;

  @Transactional(readOnly = true)
  public PaymentDto handle(PaymentDetailQuery query) {
    long tenantId = tenantProvider.currentTenantId();
    PaymentProjection row =
        paymentQueryPort
            .findById(tenantId, query.paymentId())
            .orElseThrow(
                () ->
                    new BizException(
                        404, BlueprintErrorCodes.PAYMENT_NOT_FOUND + ": " + query.paymentId()));
    return PaymentDetailAssembler.from(row);
  }
}
