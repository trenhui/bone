package com.bone.blueprint.adapter.web.assembler;

import com.bone.blueprint.adapter.web.dto.request.InitiatePaymentReq;
import com.bone.blueprint.adapter.web.dto.request.PaymentCallbackReq;
import com.bone.blueprint.adapter.web.dto.request.RefundPaymentReq;
import com.bone.blueprint.adapter.web.dto.response.InitiatePaymentResp;
import com.bone.blueprint.adapter.web.dto.response.PaymentDetailResp;
import com.bone.blueprint.application.command.cmd.InitiatePaymentCommand;
import com.bone.blueprint.application.command.cmd.InitiatePaymentResult;
import com.bone.blueprint.application.command.cmd.ProcessPaymentCallbackCommand;
import com.bone.blueprint.application.command.cmd.RefundPaymentCommand;
import com.bone.blueprint.application.query.dto.PaymentDto;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PaymentAssembler {

  InitiatePaymentCommand toInitiatePaymentCommand(InitiatePaymentReq req);

  default ProcessPaymentCallbackCommand toProcessPaymentCallbackCommand(PaymentCallbackReq req) {
    // signature 带进应用层：验签已收口到 Handler（E-4.2 分层约束，adapter 禁止直引技术端口）
    return new ProcessPaymentCallbackCommand(
        req.getPaymentId(),
        req.getChannelTradeNo(),
        req.getPaidAmount(),
        req.isSuccess(),
        req.getSignature());
  }

  default RefundPaymentCommand toRefundPaymentCommand(Long paymentId, RefundPaymentReq req) {
    return new RefundPaymentCommand(paymentId, req.getRefundAmount());
  }

  InitiatePaymentResp toInitiatePaymentResp(InitiatePaymentResult result);

  PaymentDetailResp toPaymentDetailResp(PaymentDto dto);
}
