package com.bone.app.expense;

import com.bone.metadata.engine.runtime.MetadataErrorCodes;
import com.bone.metadata.engine.runtime.RuntimeRecordException;
import com.bone.metadata.runtime.application.RuntimeActionHandler;
import com.bone.metadata.runtime.domain.RuntimeActionContext;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 借款单「确认放款」逃逸处理器（bean 名 {@code expenseAdvanceConfirmPayoutHandler}，与动作定义 {@code handler_bean}
 * 对齐，0051 生产模板种子）。
 *
 * <p><b>命名纪律（0036 教训延续）</b>：借款放款 ≠ 报销打款 ≠ 订单支付——三种资金副作用各有生命周期与凭证方向
 * （借款：借「其他应收款-备用金」/贷「银行存款」），bean/类名一律 {@code ExpenseAdvanceConfirmPayoutHandler}， 禁止复用/占用 {@code
 * ExpenseReimbursementConfirmPayoutHandler} 或 blueprint 订单域 {@code PaymentApplicationService}。
 *
 * <p>与报销打款处理器同构（入参契约平台校验 + Handler 只写业务），但流水号前缀 {@code ADV-} 与报销 {@code PAY-}
 * 区分，便于对账作业按前缀分流（三方对账：paymentSerial ↔ 银行流水 ↔ ERP 凭证号）。模拟调用外部资金系统 （半事务/补偿语义同 Runtime 数据面设计
 * §4.8.1）：真实实现应在此发起 HTTP/RPC 出金，并为失败路径补补偿；幂等由 {@code
 * RuntimeActionApplicationService}（Idempotency-Key 去重 + orphan 收口）保障。
 */
@Service("expenseAdvanceConfirmPayoutHandler")
public class ExpenseAdvanceConfirmPayoutHandler implements RuntimeActionHandler {

  @Override
  public Map<String, Object> execute(RuntimeActionContext ctx) {
    // 契约只在动作声明了 params_schema 时才由平台提前拦截；漏声明时直接 toString() 会 NPE→500，
    // 故补一道显式守卫，把「漏传必填入参」表述成可诊断的 400（与报销打款处理器同口径）。
    Object channelRaw = ctx.getParams().get("channel");
    if (channelRaw == null || channelRaw.toString().isBlank()) {
      throw new RuntimeRecordException(
          MetadataErrorCodes.RUNTIME_ACTION_INVALID_PARAMS,
          "确认放款缺少必填入参 channel（银行/工资卡等放款渠道）；" + "若该动作有必填入参，请在动作定义上声明 params_schema 以便平台提前拦截");
    }
    String channel = channelRaw.toString();

    // 模拟外部资金系统出金（真实环境替换为实际出金 RPC，并为失败路径做补偿/对账）
    String paymentSerial = "ADV-" + System.nanoTime();
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("paymentSerial", paymentSerial);
    result.put("channel", channel);
    result.put("paidAt", Instant.now().toString());
    result.put("note", ctx.getParams().getOrDefault("note", ""));
    return result;
  }
}
