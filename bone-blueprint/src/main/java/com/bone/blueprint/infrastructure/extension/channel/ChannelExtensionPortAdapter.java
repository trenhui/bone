package com.bone.blueprint.infrastructure.extension.channel;

import com.bone.blueprint.application.port.out.ChannelExtensionPort;
import com.bone.blueprint.domain.extension.channel.ChannelListingResult;
import com.bone.blueprint.domain.extension.channel.ChannelOrderContext;
import com.bone.blueprint.domain.extension.channel.ChannelOrderDraft;
import com.bone.blueprint.domain.extension.channel.ChannelProductContext;
import com.bone.blueprint.domain.extension.channel.ChannelShipmentContext;
import com.bone.blueprint.domain.extension.channel.ChannelShipmentResult;
import com.bone.blueprint.domain.extension.channel.ChannelTraceResult;
import com.bone.engine.extension.support.context.BizContext;
import com.bone.engine.extension.support.context.ExtensionContextManager;
import com.bone.engine.extension.support.context.ExtensionScope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 多渠道能力适配器 —— 把领域入参装配成 {@link BizContext}，再经扩展点代理路由到具体渠道实现。
 *
 * <p><b>路由维度的选择（关键设计）</b>：渠道用<strong>自定义维度 {@code channel}</strong>， 而不是 {@code scenario} 或 {@code
 * condition}：
 *
 * <ul>
 *   <li>不用 {@code scenario}：scenario 已被「业务场景」占用（拉单/上架/发货/同步），
 *       再用来表达渠道会让二维信息挤进一维，无法表达「抖音的发货」与「淘宝的拉单」。
 *   <li>不用 {@code condition}（SpEL）：四级路由里<strong>表达式匹配（L2）优先于维度匹配（L3）</strong>， 任何带 condition
 *       的候选都会抢在精确命中之前胜出，渠道路由会被意外劫持。
 *   <li>自定义维度 {@code channel}：四个渠道的规则互斥，L3 模糊匹配天然只有一个命中， 且与业务场景维度正交，扩展性强（将来可再加 {@code
 *       channelRegion} 等维度）。
 * </ul>
 *
 * <p><b>兜底实现为何能正确工作</b>：{@code DefaultChannel*Extension} 不带任何路由维度， 扩展引擎判定其为「默认实现」，只在 L4
 * 兜底命中；四个渠道实现带 {@code channel=XXX}，在 L3 命中。 二者不会互相抢占。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChannelExtensionPortAdapter implements ChannelExtensionPort {

  /** 业务域码：所有多渠道扩展共用，便于在扩展点目录里归组。 */
  private static final String BIZ_CODE = "CHANNEL_COMMERCE";

  private final ExtensionChannelOrderExtPoint channelOrderExtPoint;
  private final ExtensionChannelProductExtPoint channelProductExtPoint;
  private final ExtensionChannelLogisticsExtPoint channelLogisticsExtPoint;

  @Override
  public ChannelOrderDraft pullOrder(ChannelOrderContext request) {
    try (ExtensionScope ignored =
        ExtensionContextManager.with(
            bizContext(request.tenantId(), request.channelCode(), "PULL_ORDER", request))) {
      return channelOrderExtPoint.pullOrder(request);
    }
  }

  @Override
  public boolean ackOrder(ChannelOrderContext request) {
    try (ExtensionScope ignored =
        ExtensionContextManager.with(
            bizContext(request.tenantId(), request.channelCode(), "ACK_ORDER", request))) {
      return channelOrderExtPoint.ackOrder(request);
    }
  }

  @Override
  public ChannelListingResult listProduct(ChannelProductContext request) {
    try (ExtensionScope ignored =
        ExtensionContextManager.with(
            bizContext(request.tenantId(), request.channelCode(), "LIST_PRODUCT", request))) {
      return channelProductExtPoint.listProduct(request);
    }
  }

  @Override
  public ChannelListingResult delistProduct(ChannelProductContext request) {
    try (ExtensionScope ignored =
        ExtensionContextManager.with(
            bizContext(request.tenantId(), request.channelCode(), "DELIST_PRODUCT", request))) {
      return channelProductExtPoint.delistProduct(request);
    }
  }

  @Override
  public ChannelListingResult syncInventory(ChannelProductContext request) {
    try (ExtensionScope ignored =
        ExtensionContextManager.with(
            bizContext(request.tenantId(), request.channelCode(), "SYNC_INVENTORY", request))) {
      return channelProductExtPoint.syncInventory(request);
    }
  }

  @Override
  public ChannelShipmentResult pushShipment(ChannelShipmentContext request) {
    try (ExtensionScope ignored =
        ExtensionContextManager.with(
            bizContext(request.tenantId(), request.channelCode(), "PUSH_SHIPMENT", request))) {
      return channelLogisticsExtPoint.pushShipment(request);
    }
  }

  @Override
  public ChannelTraceResult queryTrace(ChannelShipmentContext request) {
    try (ExtensionScope ignored =
        ExtensionContextManager.with(
            bizContext(request.tenantId(), request.channelCode(), "QUERY_TRACE", request))) {
      return channelLogisticsExtPoint.queryTrace(request);
    }
  }

  /**
   * {@inheritDoc}
   *
   * <p><b>实现说明</b>：按「渠道前缀 + 能力族」推导各渠道实现登记在 {@code @Extension(name=...)} 上的 code， 与 {@code
   * Taobao*Extension} / {@code Jd*Extension} / {@code Douyin*Extension} / {@code Pdd*Extension}
   * 四个族的命名一致；未知渠道落到 {@code DEFAULT_CHANNEL_*_EXT}。
   */
  @Override
  public String resolveImplCode(String channelCode, String useCase) {
    return channelPrefix(channelCode) + "_CHANNEL_" + capabilityFamily(useCase) + "_EXT";
  }

  private static String channelPrefix(String channelCode) {
    return switch (channelCode == null ? "" : channelCode) {
      case "TAOBAO" -> "TB";
      case "JD" -> "JD";
      case "DOUYIN" -> "DY";
      case "PDD" -> "PDD";
      default -> "DEFAULT";
    };
  }

  private static String capabilityFamily(String useCase) {
    return switch (useCase == null ? "" : useCase) {
      case "PULL_ORDER", "ACK_ORDER" -> "ORDER";
      case "LIST_PRODUCT", "DELIST_PRODUCT", "SYNC_INVENTORY" -> "PRODUCT";
      case "PUSH_SHIPMENT", "QUERY_TRACE" -> "LOGISTICS";
      default -> "ORDER";
    };
  }

  /**
   * 装配路由上下文。
   *
   * <p>{@code tenant} 传租户ID、{@code channel} 作为自定义维度；{@code env} / {@code userGroup} 保持默认 {@code
   * *}（不参与渠道路由）。
   */
  private static <T> BizContext<T> bizContext(
      Long tenantId, String channelCode, String useCase, T data) {
    return BizContext.<T>builder()
        .tenant(String.valueOf(tenantId == null ? 0L : tenantId))
        .bizCode(BIZ_CODE)
        .useCase(useCase)
        .scenario(channelCode == null ? "*" : channelCode)
        .dimension("channel", channelCode == null ? "" : channelCode)
        .data(data)
        .build();
  }
}
