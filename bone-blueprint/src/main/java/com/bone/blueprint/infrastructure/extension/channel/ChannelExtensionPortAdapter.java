package com.bone.blueprint.infrastructure.extension.channel;

import com.bone.blueprint.application.port.out.ChannelExtensionPort;
import com.bone.blueprint.domain.extension.channel.ChannelListingResult;
import com.bone.blueprint.domain.extension.channel.ChannelOrderContext;
import com.bone.blueprint.domain.extension.channel.ChannelOrderDraft;
import com.bone.blueprint.domain.extension.channel.ChannelProductContext;
import com.bone.blueprint.domain.extension.channel.ChannelShipmentContext;
import com.bone.blueprint.domain.extension.channel.ChannelShipmentResult;
import com.bone.blueprint.domain.extension.channel.ChannelTraceResult;
import com.bone.engine.extension.api.model.definition.ExtensionDefinition;
import com.bone.engine.extension.core.register.ExtensionRegister;
import com.bone.engine.extension.support.context.BizContext;
import com.bone.engine.extension.support.context.ExtensionContextManager;
import com.bone.engine.extension.support.context.ExtensionScope;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
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
 *
 * <p><b>implCode 解析从「拼字符串反推」改为「读注册表」</b>：早期实现用两个 {@code switch} 从渠道码反推 {@code TB_CHANNEL_ORDER_EXT}
 * 这类code。新增渠道时忘记改switch 就会落 {@code DEFAULT}，让 {@code bp_channel.ext_impl_code}
 * 失真、监控台账跟着错——而路由本身不受影响， 属静默失真。现在改为直接从 {@link ExtensionRegister} 读每个扩展点的已注册实现：{@code code} 就是
 * {@code @Extension(name=...)} 的真值，{@code dimensionRules["channel"]} 就是
 * {@code @Extension(tags="channel=XXX")} 的真值， <strong>新增渠道只需加扩展类、不必改本适配器</strong>。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChannelExtensionPortAdapter implements ChannelExtensionPort {

  /** 业务域码：所有多渠道扩展共用，便于在扩展点目录里归组。 */
  private static final String BIZ_CODE = "CHANNEL_COMMERCE";

  /** 渠道维度的键名（对应 {@code @Extension(tags = "channel=XXX")}）。 */
  private static final String CHANNEL_DIMENSION = "channel";

  /** 未知渠道的兜底实现前缀，与 {@code DefaultChannel*Extension} 的命名保持一致。 */
  private static final String FALLBACK_PREFIX = "DEFAULT";

  private final ExtensionChannelOrderExtPoint channelOrderExtPoint;
  private final ExtensionChannelProductExtPoint channelProductExtPoint;
  private final ExtensionChannelFulfillmentExtPoint channelFulfillmentExtPoint;

  /** 扩展注册表用 {@link ObjectProvider} 注入：它在扩展 starter 缺席时不存在，适配器不应因此启动失败（此时 implCode 全部落兜底值）。 */
  private final ObjectProvider<ExtensionRegister> extensionRegisterProvider;

  /**
   * 启动期一次性建立「渠道码 × 能力族 → 实现 code」索引。
   *
   * <p>放在 {@code @PostConstruct} 而非每次调用时遍历：{@link #resolveImplCode} 虽只用于写投影字段，但它是观测链路上被高频调用的路径，
   * 每次遍历注册表会随扩展数量线性放大开销。
   */
  private volatile Map<String, Map<String, String>> implCodeIndex = Map.of();

  @jakarta.annotation.PostConstruct
  void buildImplCodeIndex() {
    ExtensionRegister register = extensionRegisterProvider.getIfAvailable();
    if (register == null) {
      log.warn(
          "扩展注册表不可用（bone-extension-starter 缺席），implCode 解析将全部落兜底值 {}："
              + "bp_channel.ext_impl_code 与监控台账会失真，但渠道路由不受影响",
          FALLBACK_PREFIX);
      return;
    }

    Map<String, Map<String, String>> index = new HashMap<>();
    index.put(
        ExtensionChannelOrderExtPoint.class.getName(),
        collect(register, ExtensionChannelOrderExtPoint.class, "ORDER"));
    index.put(
        ExtensionChannelProductExtPoint.class.getName(),
        collect(register, ExtensionChannelProductExtPoint.class, "PRODUCT"));
    index.put(
        ExtensionChannelFulfillmentExtPoint.class.getName(),
        collect(register, ExtensionChannelFulfillmentExtPoint.class, "LOGISTICS"));
    this.implCodeIndex = Map.copyOf(index);
    log.info("渠道实现 code 索引构建完成: {}", this.implCodeIndex);
  }

  /**
   * 从注册表里抽出「渠道码 → 实现 code」。
   *
   * <p>归类靠 code 里的族名片段（{@code *_CHANNEL_<FAMILY>_EXT}）而不是再加一个注解属性：code 本身已是注册真源，
   * 再维护一份「族」的平行声明就又回到了第 N 份真源。
   *
   * @param extPoint 扩展点接口，用于向注册表查询
   * @param family 该扩展点对应的能力族（ORDER / PRODUCT / LOGISTICS），用于剔除落在此点的其它族实现
   */
  private Map<String, String> collect(
      ExtensionRegister register, Class<?> extPoint, String family) {
    Map<String, String> byChannel = new HashMap<>();
    for (ExtensionDefinition def : register.findExtensionsByPoint(extPoint.getName())) {
      String channel = def.getDimensionRules().get(CHANNEL_DIMENSION);
      if (channel == null || channel.isBlank()) {
        // 无 channel 维度者是默认实现（DefaultChannel*Extension），走 L4 兜底，不进按渠道索引。
        continue;
      }
      String code = def.getCode();
      if (code != null && code.contains("_CHANNEL_" + family + "_EXT")) {
        byChannel.put(channel, code);
      }
    }
    return Map.copyOf(byChannel);
  }

  @Override
  public ChannelOrderDraft pullOrder(ChannelOrderContext request) {
    try (ExtensionScope ignored =
        ExtensionContextManager.with(
            bizContext(request.tenantId(), request.channelCode(), "PULL_ORDER", request))) {
      return channelOrderExtPoint.pullOrder(request);
    }
  }

  @Override
  public boolean ackOrder(ChannelShipmentContext request) {
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
      return channelFulfillmentExtPoint.pushShipment(request);
    }
  }

  @Override
  public ChannelTraceResult queryTrace(ChannelShipmentContext request) {
    try (ExtensionScope ignored =
        ExtensionContextManager.with(
            bizContext(request.tenantId(), request.channelCode(), "QUERY_TRACE", request))) {
      return channelFulfillmentExtPoint.queryTrace(request);
    }
  }

  /**
   * {@inheritDoc}
   *
   * <p><b>实现说明</b>：直接返回注册表里已登记的实现 {@code code}，不按命名规则拼字符串。 未注册的渠道/场景返回 {@code
   * DEFAULT_CHANNEL_<族>_EXT}，即默认实现的 code——与扩展引擎 L4 兜底实际命中的对象一致。
   */
  @Override
  public String resolveImplCode(String channelCode, String useCase) {
    String family = capabilityFamily(useCase);
    Map<String, String> byChannel = implCodeIndex.get(extPointNameOf(family));
    if (byChannel == null || byChannel.isEmpty()) {
      log.warn("扩展点 {} 尚未建立索引，implCode 落兜底值（注册表缺席或扩展未就绪）", family);
      return FALLBACK_PREFIX + "_CHANNEL_" + family + "_EXT";
    }
    // Map.of() 的 get(null) 抛 NPE，而渠道码确实可能为 null（渠道未绑定时上游传空）。
    // 旧 switch 实现用 `channelCode == null ? "" : channelCode` 天然安全，此处必须保留同等语义。
    String resolved = channelCode == null ? null : byChannel.get(channelCode);
    if (resolved == null) {
      log.warn(
          "渠道 {} 在 {} 族下无已注册实现，implCode 落兜底值。已注册渠道: {}——"
              + "新增渠道后此处出现告警即说明扩展类未被扫描（或未带 channel={} 标签）",
          channelCode,
          family,
          byChannel.keySet(),
          channelCode);
      return FALLBACK_PREFIX + "_CHANNEL_" + family + "_EXT";
    }
    return resolved;
  }

  /** 能力族 → 扩展点接口类名（索引表以接口类名为键）。 */
  private static String extPointNameOf(String family) {
    return switch (family) {
      case "PRODUCT" -> ExtensionChannelProductExtPoint.class.getName();
      case "LOGISTICS" -> ExtensionChannelFulfillmentExtPoint.class.getName();
      default -> ExtensionChannelOrderExtPoint.class.getName();
    };
  }

  /**
   * 业务场景 → 能力族。
   *
   * <p>未识别的场景归 ORDER：拉单是主用例，与旧实现一致；真正的兜底可见性由 {@link #resolveImplCode} 的告警提供。
   */
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
