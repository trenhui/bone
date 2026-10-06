package com.bone.blueprint.infrastructure.extension.channel;

import com.bone.blueprint.domain.extension.channel.ChannelOrderContext;
import com.bone.blueprint.domain.extension.channel.ChannelOrderDraft;
import com.bone.blueprint.domain.extension.channel.ChannelOrderLine;
import com.bone.blueprint.infrastructure.channel.openapi.ChannelApiResult;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;

/**
 * 渠道订单扩展的公共骨架（模板方法）。
 *
 * <p><b>抽这层的理由</b>：四家渠道（淘宝/京东/抖音/拼多多）的 {@code pullOrder} 在剥掉渠道差异后骨架 100% 同型—— 「调渠道接口 → 失败即抛业务异常 →
 * 逐单逐行归一化 → 空明细回落入参 → 仍为空则抛错 → 装配草稿 → 记日志」。 此前这段骨架在四个类里各写一遍（规范化重复度 0.55~0.72），任何口径变更（例如「空明细不该静默通过」）
 * 都要在四处同步，而漏改一处不会编译失败、只会在生产上表现为「某渠道行为不一致」。
 *
 * <p><b>刻意留在子类的部分（不可再抽象）</b>：请求参数拼装、响应 JSON 路径、金额单位（分/元）、外部商品ID 规则。 这些差异若强行统一成配置表，会得到一个「用字符串描述 JSON
 * 路径」的迷你语言——可读性比重复更差。 故此处只收「100% 相同」的部分，差异保留在子类里保持人眼可见。
 */
abstract class AbstractChannelOrderExtension {

  /** 各子类自带的 logger（继承体系不共享字段，故由子类显式传入）。 */
  protected final Logger log;

  protected AbstractChannelOrderExtension(Logger log) {
    this.log = log;
  }

  /** 渠道码，对应 {@code @Extension(tags = "channel=XXX")} 的 XXX。 */
  protected abstract String channelCode();

  /** 日志与异常里的渠道展示名。 */
  protected abstract String displayName();

  /** 订单来源（{@code WEB} / {@code MINI} …），进 {@code ChannelOrderDraft.channelSource}。 */
  protected abstract String channelSource();

  /** 渠道 SKU 编码前缀（淘宝 TB / 京东 JD / 抖音 DY / 拼多多 PDD）。 */
  protected abstract String skuPrefix();

  /** 调渠道开放接口（请求参数各渠道不同）。 */
  protected abstract ChannelApiResult buildCall(ChannelOrderContext request);

  /**
   * 渠道拒绝时的异常。
   *
   * <p><b>刻意交给子类构造</b>：四家渠道的拒绝都用模块统一错误码 {@code CHANNEL_OPENAPI_REJECTED}（错误码是四处真源， 不能因抽基类而降级成
   * {@code IllegalStateException}——那会让前端拿不到码、也无法按码做治理）。
   */
  protected abstract RuntimeException rejectionOf(ChannelApiResult result);

  /**
   * 从响应体解析归一化结果。
   *
   * @param data 渠道响应体，可能为 {@code null}（MOCK 通道无业务体）
   * @return 明细 + 运费 + 优惠；明细为空列表表示「渠道没给明细」，由骨架决定回落入参
   */
  protected abstract NormalizedOrder extractOrder(Map<String, Object> data);

  /**
   * 拉单并归一化（骨架固定，渠道差异全部委托给抽象方法）。
   *
   * <p>可见性为包级：只应由同包的 {@code XxxOrderExtension.pullOrder} 调用，避免绕过渠道协议直接调骨架。
   */
  final ChannelOrderDraft pullOrderInternal(ChannelOrderContext request) {
    ChannelApiResult result = buildCall(request);
    if (!result.success()) {
      throw rejectionOf(result);
    }

    NormalizedOrder order = extractOrder(result.data());
    List<ChannelOrderLine> lines = order.lines();
    // 空明细不能静默通过：静默会让「渠道没拉到」伪装成「渠道没订单」，排查时无从下手。
    if (lines.isEmpty()) {
      lines = request.lines();
    }
    if (lines.isEmpty()) {
      throw new IllegalArgumentException(displayName() + "渠道订单无有效明细: " + request.channelOrderNo());
    }

    ChannelOrderDraft draft =
        new ChannelOrderDraft(
            channelCode(),
            request.channelOrderNo(),
            channelSource(),
            toDraftLines(lines),
            order.freightAmount(),
            order.discountAmount(),
            request.receiverName(),
            request.receiverPhone(),
            request.receiverAddress());
    log.info(
        "[{}] 拉单归一化完成 | orderNo={} | lines={} | amount={}",
        displayName(),
        request.channelOrderNo(),
        draft.lines().size(),
        draft.totalAmount());
    return draft;
  }

  /**
   * 外部 SKU 编码 → 内部商品 ID（四家共用：渠道给「前缀 + 数字」的可逆编码，剥前缀还原内部 ID）。
   *
   * <p>解析失败必须抛错而非返回 0：静默变0 会让「匹配不到商品」的订单以错误的商品 ID 落库。
   */
  final Long toProductId(String outerSkuId) {
    String prefix = skuPrefix();
    if (outerSkuId == null || outerSkuId.isBlank()) {
      throw new IllegalArgumentException(displayName() + "渠道 SKU 编码为空");
    }
    String trimmed = outerSkuId.trim();
    String body =
        trimmed.toUpperCase().startsWith(prefix) ? trimmed.substring(prefix.length()) : trimmed;
    try {
      return Long.parseLong(body);
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException(
          displayName() + "渠道 SKU 编码无法解析为内部商品ID: " + outerSkuId + "（期望格式 " + prefix + "<商品ID>）",
          ex);
    }
  }

  private List<ChannelOrderDraft.ChannelDraftLine> toDraftLines(List<ChannelOrderLine> lines) {
    return lines.stream()
        .map(
            line ->
                new ChannelOrderDraft.ChannelDraftLine(
                    toProductId(line.outerSkuId()),
                    line.title(),
                    line.quantity(),
                    line.unitPrice()))
        .toList();
  }

  /** 归一化中间结果：明细 + 金额（金额随渠道单位不同，故与明细一起回传）。 */
  record NormalizedOrder(
      List<ChannelOrderLine> lines, BigDecimal freightAmount, BigDecimal discountAmount) {

    static NormalizedOrder of(List<ChannelOrderLine> lines) {
      return new NormalizedOrder(lines, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    static NormalizedOrder of(
        List<ChannelOrderLine> lines, BigDecimal freight, BigDecimal discount) {
      return new NormalizedOrder(lines, freight, discount);
    }
  }
}
