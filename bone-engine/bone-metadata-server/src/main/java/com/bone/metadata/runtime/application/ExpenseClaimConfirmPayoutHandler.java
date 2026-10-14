package com.bone.metadata.runtime.application;

import com.bone.metadata.engine.runtime.MetadataErrorCodes;
import com.bone.metadata.engine.runtime.RuntimeRecordException;
import com.bone.metadata.runtime.domain.RuntimeActionContext;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 报销「确认打款」逃逸处理器（bean 名 {@code expenseClaimConfirmPayoutHandler}，与动作定义 {@code handler_bean} 对齐）。
 *
 * <p><b>命名纪律（费用报销建模方案 §0.1）</b>：报销打款 ≠ 订单支付生命周期，bean/类名一律 {@code
 * ExpenseClaimConfirmPayoutHandler}，禁止复用/占用 blueprint 订单域 {@code PaymentApplicationService}（避免同名
 * bean 在 starter 装配/日志定位时冲突）。
 *
 * <p>模拟调用外部支付网关（半事务/补偿语义，Runtime 数据面设计 §4.8.1）：仅生成支付流水号，不真正出金。 真实实现应在此发起 HTTP/RPC 调用，并对失败做补偿/对账；幂等由
 * {@code RuntimeActionService} （按 Idempotency-Key 去重 + 执行日志 orphan 态）保障，故本方法可放心假设「被调用即代表需要执行一次打款」。
 */
@Service("expenseClaimConfirmPayoutHandler")
public class ExpenseClaimConfirmPayoutHandler implements RuntimeActionHandler {

  @Override
  public Map<String, Object> execute(RuntimeActionContext ctx) {
    // 入参契约校验（channel 必填 + enum 白名单）由平台 RuntimeActionService.validateParams 承担（§4.8.2），
    // Handler 不再各自校验——Hasura Actions inputSchema 语义：契约平台管，Handler 只写业务。
    // 但契约只在动作声明了 params_schema 时才生效：建模者漏声明时，若此处直接 toString() 会 NPE→500
    // （内部错误，调用方看不出是自己漏传 channel）。故补一道显式守卫，把「漏传必填入参」表述成可诊断的 400。
    Object channelRaw = ctx.getParams().get("channel");
    if (channelRaw == null || channelRaw.toString().isBlank()) {
      throw new RuntimeRecordException(
          MetadataErrorCodes.RUNTIME_ACTION_INVALID_PARAMS,
          "确认打款缺少必填入参 channel（银行/现金等支付渠道）；" + "若该动作有必填入参，请在动作定义上声明 params_schema 以便平台提前拦截");
    }
    String channel = channelRaw.toString();

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
