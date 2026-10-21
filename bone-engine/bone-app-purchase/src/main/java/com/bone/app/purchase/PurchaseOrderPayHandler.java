package com.bone.app.purchase;

import com.bone.metadata.engine.runtime.MetadataErrorCodes;
import com.bone.metadata.engine.runtime.RuntimeRecordException;
import com.bone.metadata.runtime.application.RuntimeActionHandler;
import com.bone.metadata.runtime.domain.RuntimeActionContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 采购订单「付款」逃逸处理器（bean 名 {@code purchaseOrderPayHandler}，与动作定义 {@code handler_bean} 对齐）。
 *
 * <p><b>为什么单独一个 bean 而不是复用报销打款</b>：采购付款与报销打款是两条不同的资金链路——
 * 付款对象是<b>供应商</b>（对公、需三单匹配与应付核销），报销打款对象是<b>员工</b>（对私、核销借款）。 复用同一 bean
 * 会让两条链路的失败语义、对账口径与审计线索互相污染（报销命名纪律亦禁止复用 blueprint 订单域语义）。
 *
 * <p><b>职责边界（与 {@code ExpenseReimbursementConfirmPayoutHandler} 同构）</b>：
 *
 * <ol>
 *   <li><b>入参契约由平台管</b>：{@code channel}/{@code payAmount} 的必填与取值域由动作定义 {@code params_schema}
 *       声明、{@code RuntimeActionApplicationService#validateParams} 统一校验（Hasura Actions inputSchema
 *       语义）；
 *   <li><b>此处只补一道守卫</b>：建模者漏声明 {@code params_schema} 时，直接读空值会 NPE→500（内部错误，
 *       调用方看不出是自己漏传），故显式把「漏传必填入参」表述成可诊断的 400；
 *   <li><b>业务语义</b>：模拟调用外部付款网关，生成付款流水号与会计凭证号，<b>不真正出金</b>。
 * </ol>
 *
 * <p><b>三单匹配在哪</b>：订单/收货/发票三者相符是<b>声明式</b>能力（协议 {@code submitRules} 的 {@code SUB-MATCH-01/02}，以
 * {@code when: status eq RECEIVED} 门控），由 {@link UiSchemaRuleEnforcer} 在动作前强制——
 * 处理器<b>不重复校验</b>，避免「配置改了、代码没改」的双真源漂移。
 *
 * <p><b>幂等</b>：由 {@code RuntimeActionApplicationService} 按 {@code Idempotency-Key} 去重 + 执行日志孤儿态保障，
 * 故本方法可放心假设「被调用即代表需要执行一次付款」。
 */
@Service("purchaseOrderPayHandler")
public class PurchaseOrderPayHandler implements RuntimeActionHandler {

  @Override
  public Map<String, Object> execute(RuntimeActionContext ctx) {
    Map<String, Object> params = ctx.getParams() == null ? Map.of() : ctx.getParams();

    Object channelRaw = params.get("channel");
    if (channelRaw == null || channelRaw.toString().isBlank()) {
      throw new RuntimeRecordException(
          MetadataErrorCodes.RUNTIME_ACTION_INVALID_PARAMS,
          "采购付款缺少必填入参 channel（BANK=银行转账 / ACCEPTANCE=承兑汇票 / WECHAT=微信）；"
              + "若该动作有必填入参，请在动作定义上声明 params_schema 以便平台提前拦截");
    }

    Object amountRaw = params.get("payAmount");
    BigDecimal payAmount;
    try {
      payAmount = amountRaw == null ? null : new BigDecimal(amountRaw.toString());
    } catch (NumberFormatException e) {
      throw new RuntimeRecordException(
          MetadataErrorCodes.RUNTIME_ACTION_INVALID_PARAMS, "采购付款入参 payAmount 不是合法金额：" + amountRaw);
    }
    if (payAmount == null || payAmount.compareTo(BigDecimal.ZERO) <= 0) {
      throw new RuntimeRecordException(
          MetadataErrorCodes.RUNTIME_ACTION_INVALID_PARAMS,
          "采购付款缺少必填入参 payAmount（须大于 0）；" + "若该动作有必填入参，请在动作定义上声明 params_schema 以便平台提前拦截");
    }

    // 模拟外部付款网关调用（真实环境替换为实际出金 RPC，并为失败路径补补偿与对账）
    String paymentSerial = "POPAY-" + System.nanoTime();
    String voucherNo = "VCH" + Instant.now().toString().replaceAll("[^0-9]", "").substring(0, 14);

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("paymentSerial", paymentSerial);
    result.put("voucherNo", voucherNo);
    result.put("channel", channelRaw.toString());
    result.put("payAmount", payAmount);
    result.put("paidAt", Instant.now().toString());
    result.put("note", params.getOrDefault("note", ""));
    return result;
  }
}
