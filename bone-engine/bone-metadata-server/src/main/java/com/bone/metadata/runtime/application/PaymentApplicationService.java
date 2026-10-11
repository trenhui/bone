package com.bone.metadata.runtime.application;

import com.bone.metadata.engine.runtime.MetadataErrorCodes;
import com.bone.metadata.engine.runtime.RuntimeRecordException;
import com.bone.metadata.runtime.domain.RuntimeActionContext;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 报销「确认打款」逃逸处理器（bean 名 {@code paymentApplicationService}，与动作定义 {@code handler_bean} 对齐）。
 *
 * <p>模拟调用外部支付网关（半事务/补偿语义，Runtime 数据面设计 §4.8.1）： 仅生成支付流水号，不真正出金。真实实现应在此发起 HTTP/RPC 调用，
 * 并对失败做补偿/对账；幂等由 {@code RuntimeActionService}（按 Idempotency-Key 去重）保障， 故本方法可放心假设「被调用即代表需要执行一次打款」。
 */
@Service("paymentApplicationService")
public class PaymentApplicationService implements RuntimeActionHandler {

  private static final List<String> VALID_CHANNELS = List.of("PAYROLL", "BANK_TRANSFER");

  @Override
  public Map<String, Object> execute(RuntimeActionContext ctx) {
    Object channelObj = ctx.getParams().get("channel");
    if (channelObj == null || channelObj.toString().isBlank()) {
      throw new RuntimeRecordException(
          MetadataErrorCodes.RUNTIME_ACTION_INVALID_PARAMS, "打款渠道 channel 为必填");
    }
    String channel = channelObj.toString();
    if (!VALID_CHANNELS.contains(channel)) {
      throw new RuntimeRecordException(
          MetadataErrorCodes.RUNTIME_ACTION_INVALID_PARAMS, "非法打款渠道: " + channel);
    }

    // 模拟外部支付网关调用（真实环境替换为实际出金 RPC，并为失败路径补补偿）
    String paymentSerial = "PAY-" + System.nanoTime();
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("paymentSerial", paymentSerial);
    result.put("channel", channel);
    result.put("paidAt", Instant.now().toString());
    result.put("note", ctx.getParams().getOrDefault("note", ""));
    return result;
  }
}
