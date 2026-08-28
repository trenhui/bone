package com.bone.blueprint.application.query.handler;

import com.bone.blueprint.application.query.dto.PaymentDto;
import com.bone.blueprint.application.query.qry.PaymentDetailQuery;
import com.bone.blueprint.application.query.support.PaymentAssemblerHelper;
import com.bone.blueprint.domain.gateway.PaymentReadPort;
import com.bone.blueprint.domain.gateway.TenantProvider;
import com.bone.blueprint.domain.payment.read.PaymentRow;
import com.bone.core.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 支付单详情查询：读侧端口直查（只读，无事务写）。 */
@Component
@RequiredArgsConstructor
public class PaymentDetailQueryHandler {

  private final PaymentReadPort paymentReadPort;
  private final TenantProvider tenantProvider;

  @Transactional(readOnly = true)
  public PaymentDto handle(PaymentDetailQuery query) {
    long tenantId = tenantProvider.currentTenantId();
    PaymentRow row =
        paymentReadPort
            .findById(tenantId, query.getPaymentId())
            .orElseThrow(() -> new NotFoundException("支付单不存在: " + query.getPaymentId()));
    return PaymentAssemblerHelper.toDto(row);
  }
}
