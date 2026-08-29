package com.bone.blueprint.adapter.web.assembler;

import com.bone.blueprint.adapter.web.dto.request.InitiatePaymentReq;
import com.bone.blueprint.adapter.web.dto.request.PaymentCallbackReq;
import com.bone.blueprint.adapter.web.dto.request.RefundPaymentReq;
import com.bone.blueprint.adapter.web.dto.response.InitiatePaymentResp;
import com.bone.blueprint.adapter.web.dto.response.PaymentDetailResp;
import com.bone.blueprint.application.command.cmd.HandlePaymentCallbackCommand;
import com.bone.blueprint.application.command.cmd.InitiatePaymentCommand;
import com.bone.blueprint.application.command.cmd.RefundPaymentCommand;
import com.bone.blueprint.application.command.result.InitiatePaymentResult;
import com.bone.blueprint.application.query.dto.PaymentDto;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PaymentAssembler {

  InitiatePaymentCommand toInitiatePaymentCommand(InitiatePaymentReq req);

  default HandlePaymentCallbackCommand toHandlePaymentCallbackCommand(PaymentCallbackReq req) {
    return new HandlePaymentCallbackCommand(
        req.getPaymentId(),
        req.getChannelTradeNo(),
        req.getPaidAmount(),
        req.getSignature(),
        req.isSuccess());
  }

  default RefundPaymentCommand toRefundPaymentCommand(Long paymentId, RefundPaymentReq req) {
    return new RefundPaymentCommand(paymentId, req.getRefundAmount());
  }

  InitiatePaymentResp toInitiatePaymentResp(InitiatePaymentResult result);

  PaymentDetailResp toPaymentDetailResp(PaymentDto dto);
}
