package com.bone.blueprint.application.port.out;

import com.bone.blueprint.domain.extension.channel.ChannelListingResult;
import com.bone.blueprint.domain.extension.channel.ChannelOrderContext;
import com.bone.blueprint.domain.extension.channel.ChannelOrderDraft;
import com.bone.blueprint.domain.extension.channel.ChannelProductContext;
import com.bone.blueprint.domain.extension.channel.ChannelShipmentContext;
import com.bone.blueprint.domain.extension.channel.ChannelShipmentResult;
import com.bone.blueprint.domain.extension.channel.ChannelTraceResult;

/**
 * 多渠道能力出站端口 —— 应用层对「渠道差异」的唯一依赖面。
 *
 * <p><b>为何要多这一层，而不是让应用服务直接注入扩展点接口</b>：扩展点接口虽在 {@code domain} 包， 但要让它真正工作必须装配 {@code
 * BizContext}（租户、渠道维度、场景码）并压入线程上下文。 这段装配代码属于<strong>技术设施</strong>，若散落在四个应用服务里，就会出现「某处忘了设 channel 维度
 * → 路由到默认实现 → 渠道功能静默不可用」这类极难排查的缺陷。收口到一个适配器，装配逻辑只有一份。
 *
 * <p><b>对应用服务的契约</b>：只传领域入参，只拿领域出参，感知不到扩展引擎的存在。 因此应用服务的单元测试可以直接 Mock 本端口，不必启动扩展引擎。
 */
public interface ChannelExtensionPort {

  /** 拉取并归一化渠道订单。 */
  ChannelOrderDraft pullOrder(ChannelOrderContext request);

  /**
   * 发货信息回传渠道（人工/补偿触发）。
   *
   * <p><b>与 {@link #pushShipment} 的分工</b>：{@code pushShipment} 是履约链路的正式回传（失败落库、可重试）；
   * 本方法是给运营/补偿用的旁路入口。两者都要求携带<b>真实</b>承运商与运单号—— 早期实现只传订单号、运单号在渠道实现里写死，导致无论真实发什么货、回传给渠道的都是同一串假单号。
   */
  boolean ackOrder(ChannelShipmentContext request);

  /** 商品上架到渠道。 */
  ChannelListingResult listProduct(ChannelProductContext request);

  /** 商品从渠道下架。 */
  ChannelListingResult delistProduct(ChannelProductContext request);

  /** 同步库存到渠道。 */
  ChannelListingResult syncInventory(ChannelProductContext request);

  /** 发货回传渠道。 */
  ChannelShipmentResult pushShipment(ChannelShipmentContext request);

  /** 查询渠道物流轨迹。 */
  ChannelTraceResult queryTrace(ChannelShipmentContext request);

  /**
   * 解析「给定渠道 + 场景」下扩展点路由会命中的实现 code。
   *
   * <p><b>用途严格限定为可观测</b>：写回 {@code bp_channel.ext_impl_code}，排障时回答「这次请求究竟命中了哪个实现」， 从而发现「配置说走 A
   * 实现、实际走了 B 实现」这类偏差。<strong>它不参与路由</strong>——路由只由 {@code BizContext} 的 {@code channel} 维度决定。
   *
   * <p><b>为何要单独开一个方法，而不是让调用方拼字符串</b>：实现 code 的命名规则属于扩展引擎的注册约定， 若散落在业务代码里就会变成第 N
   * 份真源，改一处注册名就要全仓找字符串。收口到端口后只有适配器一处知道规则。
   *
   * @param channelCode 渠道码
   * @param useCase 业务场景码（{@code PULL_ORDER} / {@code LIST_PRODUCT} / {@code PUSH_SHIPMENT} …）
   * @return 实现 code；未知渠道返回默认实现的 code
   */
  String resolveImplCode(String channelCode, String useCase);
}
