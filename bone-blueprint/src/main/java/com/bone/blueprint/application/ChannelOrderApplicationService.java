package com.bone.blueprint.application;

import com.bone.blueprint.application.port.out.ChannelExtensionPort;
import com.bone.blueprint.application.port.out.TenantPort;
import com.bone.blueprint.common.BlueprintErrorCodes;
import com.bone.blueprint.common.BlueprintErrors;
import com.bone.blueprint.domain.extension.channel.ChannelOrderContext;
import com.bone.blueprint.domain.extension.channel.ChannelOrderDraft;
import com.bone.blueprint.domain.extension.channel.ChannelShipmentContext;
import com.bone.blueprint.domain.extension.channel.ChannelShipmentResult;
import com.bone.blueprint.domain.model.channel.Channel;
import com.bone.blueprint.domain.model.channel.event.ChannelRoutedEvent;
import com.bone.blueprint.domain.model.channel.valueobject.ChannelCode;
import com.bone.blueprint.domain.model.channelbuyer.event.ChannelBuyerObservedEvent;
import com.bone.blueprint.domain.model.order.Order;
import com.bone.blueprint.domain.model.order.OrderItem;
import com.bone.blueprint.domain.repository.ChannelRepository;
import com.bone.blueprint.domain.repository.OrderRepository;
import com.bone.core.domain.event.DomainEventPublisher;
import com.bone.core.util.DistributedIdGenerator;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 渠道订单应用层门面 —— 把渠道订单经扩展点归一化后落成内部订单。
 *
 * <p><b>本类是「多渠道订单」的主链路</b>，编排顺序刻意固定为：
 *
 * <ol>
 *   <li><b>幂等前置</b>：先查 {@code (tenant, channelCode, channelOrderNo)}。渠道推送必然重复
 *       （网络重试、渠道侧重推），不查就会重复建单；查了则重复推送返回已有订单号，语义正确。
 *   <li><b>渠道校验</b>：渠道必须已注册且启用。已停用渠道拉进来的订单会被后续发货回传卡住， 且运营无从判断这批订单从哪来。
 *   <li><b>扩展点归一化</b>：经 {@link ChannelExtensionPort} 路由到对应渠道实现， 拿到与渠道无关的统一草稿 {@link
 *       ChannelOrderDraft}。
 *   <li><b>库存预留</b>：渠道订单同样占用共享库存，不预留就会超卖。
 *   <li><b>落单</b>：以渠道回传金额为准建单（见 {@link Order#createFromChannel} 的说明）。
 * </ol>
 *
 * <p><b>为何金额不走内部定价链路</b>：渠道订单是已成交事实，其内部定价会与渠道实付对不上， 对账时无法解释差异。所以这里不走 {@code PricingPort}，只做库存校验。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChannelOrderApplicationService {

  private final OrderRepository orderRepository;
  private final ChannelRepository channelRepository;
  private final ChannelExtensionPort channelExtensionPort;
  private final InventoryApplicationService inventoryApplicationService;
  private final ChannelBuyerApplicationService channelBuyerApplicationService;
  private final DomainEventPublisher domainEventPublisher;
  private final TenantPort tenantProvider;

  /**
   * 拉取渠道订单并落成内部订单。
   *
   * @param context 渠道订单上下文
   * @return 内部订单ID（重复推送返回已有订单ID，保证幂等）
   */
  @Transactional
  public Long pullAndCreate(ChannelOrderContext context) {
    long tenantId = tenantProvider.currentTenantId();
    String code = normalize(context.channelCode());
    // 渠道必须已注册且启用：已停用渠道拉进来的订单会在发货回传时卡住
    requireEnabledChannel(tenantId, code, context.channelCode());

    // ① 幂等前置
    Order existing = orderRepository.findByChannelOrderNo(tenantId, code, context.channelOrderNo());
    if (existing != null) {
      log.info(
          "[{}] 渠道订单重复推送，幂等返回已有订单 | orderNo={} | orderId={}",
          code,
          context.channelOrderNo(),
          existing.getId());
      return existing.getId();
    }

    // ② 扩展点归一化（渠道差异在此处被吃掉）
    ChannelOrderDraft draft = channelExtensionPort.pullOrder(context);
    if (draft == null || draft.lines() == null || draft.lines().isEmpty()) {
      throw BlueprintErrors.of(
          BlueprintErrorCodes.CHANNEL_CODE_INVALID,
          "渠道 " + code + " 返回的订单草稿无有效明细: " + context.channelOrderNo());
    }

    // ③ 库存预留：渠道订单同样占用共享库存
    for (ChannelOrderDraft.ChannelDraftLine line : draft.lines()) {
      inventoryApplicationService.reserve(line.productId(), "DEFAULT", line.quantity());
    }

    // ④ 落单（客户维度来自「渠道买家 ↔ 内部客户」映射，只读不写：写映射是另一个聚合的事务）
    long orderId = DistributedIdGenerator.generateLongId();
    long customerId = resolveCustomerId(context, code);
    List<OrderItem> items = new ArrayList<>();
    for (ChannelOrderDraft.ChannelDraftLine line : draft.lines()) {
      items.add(
          OrderItem.create(
              DistributedIdGenerator.generateLongId(),
              orderId,
              line.productId(),
              line.productName(),
              line.quantity(),
              line.unitPrice()));
    }
    Order order =
        Order.createFromChannel(
            orderId,
            tenantId,
            customerId,
            items,
            draft.channelSource(),
            draft.freightAmount(),
            draft.discountAmount(),
            code,
            context.channelOrderNo());
    // 渠道订单在业务上**已经付款**：订单来自渠道平台「买家已下单」列表（淘宝/京东/抖音/拼多多
    // 均在买家付款后才向商家推送），钱在渠道侧已收妥，不存在内部待支付态。
    //
    // 修复的真实缺陷：此前落单后停在 CREATED（未支付），导致① 订单列表对渠道单显示「待支付」，
    // 与平台事实不符；② 发货守卫「仅 PAID 可发货」把所有渠道单挡在发货之外；
    // ③ 退款判定 isRefundable() 依赖 PAID，渠道单将永远无法退款。
    //
    // ★为何用 markChannelPaid 而非 confirmPaid：confirmPaid 会发 OrderPaidEvent，
    // 其订阅者 OrderPaidEventHandler 会调 inventoryGateway.confirmStock「消费预留」。
    // 而本方法上面第③ 步已对每行 reserve 过预留，若再 confirm 就是**同一批预留被扣两次**
    // ⇒ 库存凭空少一份，正是超卖的另一面（少卖）。渠道链路已在此处同步完成预留，
    // 因此只推进状态、不发支付事件。
    order.markChannelPaid();
    orderRepository.save(order);
    domainEventPublisher.publishFrom(order);

    // ⑤ 渠道同步标记：跨聚合，交给提交后的独立事务投影（R9 一事务一聚合）
    domainEventPublisher.publish(
        new ChannelRoutedEvent(tenantId, code, "PULL_ORDER", java.time.Instant.now()));
    // ⑥ 渠道买家观测：建影子映射 / 累计笔数同样跨聚合，交 AFTER_COMMIT 独立事务处理。
    // 未携带买家ID 时消费者会跳过（无映射键），此处仍发布以保留「渠道报文缺字段」的排查线索。
    if (context.buyerId() != null && !context.buyerId().isBlank()) {
      domainEventPublisher.publish(
          ChannelBuyerObservedEvent.of(
              tenantId, code, context.buyerId(), context.buyerNick(), orderId));
    } else {
      log.warn("[{}] 渠道订单未携带买家ID，客户维度与映射登记均跳过 | orderNo={}", code, context.channelOrderNo());
    }
    log.info(
        "[{}] 渠道订单落单成功 | orderNo={} | orderId={} | amount={}",
        code,
        context.channelOrderNo(),
        orderId,
        order.getTotalAmount());
    return orderId;
  }

  /**
   * 发货信息回传渠道（人工/补偿触发；履约链路的正式回传见 {@code ShipmentApplicationService}）。
   *
   * <p><b>为何必须携带承运商与运单号</b>：早期本方法只传渠道订单号，承运商与运单号由渠道实现自行填固定值，
   * 结果是「无论真实发什么货，回传给渠道的都是同一串假运单号」——渠道侧据此展示的物流轨迹与实际不符， 而本地日志显示回传成功。这类错误不会报错、只会在用户收货时暴露。
   *
   * @param logisticsCompany 内部物流公司名（如「顺丰速运」），由渠道实现映射为渠道侧编码
   * @param trackingNo 真实运单号
   */
  @Transactional
  public ChannelShipmentResult ackToChannel(
      String channelCode, String channelOrderNo, String logisticsCompany, String trackingNo) {
    long tenantId = tenantProvider.currentTenantId();
    String code = normalize(channelCode);
    return channelExtensionPort.ackOrder(
        new ChannelShipmentContext(
            tenantId, code, channelOrderNo, null, logisticsCompany, trackingNo));
  }

  /**
   * 渠道买家账号 → 内部客户ID（查「渠道买家 ↔ 内部客户」映射表）。
   *
   * <p><b>为何不再用 {@code buyerNick.hashCode()}</b>：昵称可被买家随时修改，改名即换客户——同一人的订单被拆到不同客户下， 客户维度的统计、
   * 会员权益、售后与对账全部失真；且哈希值不是真实客户ID，无法与其它域 join。现改为查映射表： 命中且已绑定 → 真实客户ID； 未绑定/无映射 → {@code 0}（未知客户），并由
   * {@link ChannelBuyerObservedEvent} 异步登记影子映射供后续人工绑定。
   *
   * <p>本方法<strong>只读</strong>：写映射属于另一个聚合，必须在独立事务里做（R9）。
   */
  private long resolveCustomerId(ChannelOrderContext context, String normalizedChannelCode) {
    return channelBuyerApplicationService.resolveCustomerId(context, normalizedChannelCode);
  }

  private Channel requireEnabledChannel(long tenantId, String code, String raw) {
    Channel channel = channelRepository.findByCode(tenantId, code);
    if (channel == null) {
      throw BlueprintErrors.of(BlueprintErrorCodes.CHANNEL_NOT_FOUND, raw);
    }
    if (!channel.isEnabled()) {
      throw BlueprintErrors.of(BlueprintErrorCodes.CHANNEL_DISABLED, raw);
    }
    return channel;
  }

  private static String normalize(String channelCode) {
    ChannelCode code = ChannelCode.parseOrNull(channelCode);
    if (code == null) {
      throw BlueprintErrors.of(BlueprintErrorCodes.CHANNEL_CODE_INVALID, channelCode);
    }
    return code.name();
  }
}
