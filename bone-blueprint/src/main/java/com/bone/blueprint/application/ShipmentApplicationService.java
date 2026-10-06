package com.bone.blueprint.application;

import com.bone.blueprint.application.port.out.ChannelExtensionPort;
import com.bone.blueprint.application.port.out.TenantPort;
import com.bone.blueprint.application.query.dto.ShipmentDto;
import com.bone.blueprint.application.query.dto.ShipmentTraceDto;
import com.bone.blueprint.common.BlueprintErrorCodes;
import com.bone.blueprint.common.BlueprintErrors;
import com.bone.blueprint.domain.extension.channel.ChannelShipmentContext;
import com.bone.blueprint.domain.extension.channel.ChannelShipmentResult;
import com.bone.blueprint.domain.extension.channel.ChannelTraceResult;
import com.bone.blueprint.domain.model.order.Order;
import com.bone.blueprint.domain.model.order.valueobject.OrderStatus;
import com.bone.blueprint.domain.model.shipment.Shipment;
import com.bone.blueprint.domain.model.shipment.ShipmentTrace;
import com.bone.blueprint.domain.model.shipment.valueobject.ShipmentStatus;
import com.bone.blueprint.domain.repository.OrderRepository;
import com.bone.blueprint.domain.repository.ShipmentRepository;
import com.bone.blueprint.domain.repository.ShipmentTraceRepository;
import com.bone.core.domain.event.DomainEventPublisher;
import com.bone.core.exception.DomainException;
import com.bone.core.model.PageResult;
import com.bone.core.util.DistributedIdGenerator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 发货物流应用层门面 —— 发货单创建、发货、签收与轨迹同步。
 *
 * <p><b>渠道回传失败的处置原则（重要）</b>：渠道回传失败<strong>不回退发货状态</strong>。
 * 货已经发出去了，把状态退回「未发货」会制造假象，客服看到的与实际不符。正确做法是 保留 SHIPPED + 标记 {@code channelAck=false} +
 * 记录失败原因，交由补偿任务重试—— 状态表达「物理事实」，回传标记表达「渠道同步事实」，二者分离。
 *
 * <p><b>为何轨迹要落本地库</b>：渠道轨迹接口通常有调用频率限制且需要渠道授权， 每次客服查看都去查渠道既不现实也会触发限流。落库后本地可查，渠道只作为更新源。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShipmentApplicationService {

  private final ShipmentRepository shipmentRepository;
  private final ShipmentTraceRepository shipmentTraceRepository;
  private final ChannelExtensionPort channelExtensionPort;
  private final TenantPort tenantProvider;
  private final DomainEventPublisher domainEventPublisher;
  private final OrderRepository orderRepository;

  @Transactional(readOnly = true)
  public PageResult<ShipmentDto> page(String channelCode, String status, int page, int size) {
    long tenantId = tenantProvider.currentTenantId();
    ShipmentStatus shipmentStatus =
        status == null || status.isBlank() ? null : ShipmentStatus.valueOf(status);
    PageResult<Shipment> result =
        shipmentRepository.findPage(tenantId, channelCode, shipmentStatus, page, size);
    List<ShipmentDto> records =
        result.getRecords() == null
            ? List.of()
            : result.getRecords().stream().map(ShipmentDto::from).toList();
    return PageResult.of(records, result.getTotal(), result.getPage(), result.getSize());
  }

  @Transactional(readOnly = true)
  public ShipmentDto detail(Long shipmentId) {
    long tenantId = tenantProvider.currentTenantId();
    Shipment entity = shipmentRepository.findById(shipmentId);
    if (entity == null) {
      throw BlueprintErrors.of(BlueprintErrorCodes.SHIPMENT_NOT_FOUND, shipmentId);
    }
    return ShipmentDto.from(entity);
  }

  /** 按订单查发货单（前端从订单跳转发货时常用）。 */
  @Transactional(readOnly = true)
  public ShipmentDto byOrder(Long orderId) {
    long tenantId = tenantProvider.currentTenantId();
    Shipment entity = shipmentRepository.findByOrderId(tenantId, orderId);
    if (entity == null) {
      throw BlueprintErrors.of(BlueprintErrorCodes.SHIPMENT_NOT_FOUND, "orderId=" + orderId);
    }
    return ShipmentDto.from(entity);
  }

  /** 创建发货单（尚未交运，状态 CREATED）。 */
  @Transactional
  public ShipmentDto create(
      Long orderId,
      String channelCode,
      String receiverName,
      String receiverPhone,
      String receiverAddress) {
    long tenantId = tenantProvider.currentTenantId();
    Shipment existing = shipmentRepository.findByOrderId(tenantId, orderId);
    if (existing != null) {
      // 幂等：一单一发货单，重复创建直接返回已有记录，避免重复发货。
      return ShipmentDto.from(existing);
    }
    // 前置守卫：订单必须存在且处于「可发货」状态。
    // 修复的真实缺陷——此前本方法完全不校验订单，于是已取消（CANCELLED）/已退款（REFUNDED）的
    // 订单也能建发货单并走完发货→签收，产生「已取消订单却显示已签收」的账实不符。
    requireOrderShippable(orderId);
    Shipment entity =
        Shipment.create(
            DistributedIdGenerator.generateLongId(),
            tenantId,
            orderId,
            channelCode,
            receiverName,
            receiverPhone,
            receiverAddress);
    shipmentRepository.insert(entity);
    return ShipmentDto.from(entity);
  }

  /**
   * 校验订单存在且可发货（PAID / SHIPPED）。
   *
   * <p>为何放在应用层而非聚合：需要读另一个聚合（Order）的状态才能判定，聚合内部无法跨聚合查询， 故由应用层编排、{@link Order}
   * 自身继续用状态机做二次守卫（领域层不因这里而放松）。
   */
  private void requireOrderShippable(Long orderId) {
    Order order = orderRepository.findById(orderId);
    if (order == null) {
      throw BlueprintErrors.of(BlueprintErrorCodes.ORDER_NOT_FOUND, orderId);
    }
    OrderStatus status = order.getStatus();
    if (status != OrderStatus.PAID && status != OrderStatus.SHIPPED) {
      throw BlueprintErrors.of(
          BlueprintErrorCodes.ORDER_STATUS_CONFLICT,
          "订单当前状态[" + status + "]不可发货（shipment 需要 orderId=" + orderId + "）");
    }
  }

  /**
   * 发货：生成运单号 → 推进状态 → 回传渠道。
   *
   * @param trackingNo 运单号，必填（无运单号的已发货 = 虚假发货）
   */
  @Transactional
  public ShipmentDto ship(Long shipmentId, String logisticsCompany, String trackingNo) {
    long tenantId = tenantProvider.currentTenantId();
    Shipment entity = requireShipment(tenantId, shipmentId);
    if (trackingNo == null || trackingNo.isBlank()) {
      throw BlueprintErrors.of(BlueprintErrorCodes.SHIPMENT_TRACKING_NO_REQUIRED, shipmentId);
    }
    try {
      entity.ship(logisticsCompany, trackingNo, Instant.now());
    } catch (DomainException ex) {
      throw BlueprintErrors.of(BlueprintErrorCodes.SHIPMENT_STATUS_CONFLICT, ex.getMessage());
    }
    shipmentRepository.update(entity);

    // 回传渠道：失败不影响本地发货状态（见类 javadoc）
    ChannelShipmentResult result =
        channelExtensionPort.pushShipment(
            new ChannelShipmentContext(
                tenantId,
                entity.getChannelCode(),
                null,
                entity.getShipmentNo(),
                logisticsCompany,
                trackingNo));
    if (result.success()) {
      entity.markChannelAcked(Instant.now());
    } else {
      entity.markChannelAckFailed(result.errorCode() + " - " + result.message());
      log.warn(
          "发货回传渠道失败 | shipment={} | channel={} | code={}", // NOSONAR
          shipmentId,
          entity.getChannelCode(),
          result.errorCode());
    }
    shipmentRepository.update(entity);

    // 本地落一条首发轨迹，保证「刚发货」时轨迹不为空
    appendTrace(
        tenantId,
        entity.getId(),
        Instant.now(),
        "SHIPPED",
        "已发货，承运商 " + logisticsCompany + "，运单号 " + trackingNo);

    // 发布 ShipmentShippedEvent，由 ShipmentOrderSyncEventHandler 推进订单状态（跨聚合只能走事件）。
    domainEventPublisher.publishFrom(entity);
    return ShipmentDto.from(entity);
  }

  /** 签收。 */
  @Transactional
  public ShipmentDto sign(Long shipmentId) {
    long tenantId = tenantProvider.currentTenantId();
    Shipment entity = requireShipment(tenantId, shipmentId);
    try {
      entity.sign(Instant.now());
    } catch (DomainException ex) {
      throw BlueprintErrors.of(BlueprintErrorCodes.SHIPMENT_STATUS_CONFLICT, ex.getMessage());
    }
    shipmentRepository.update(entity);
    appendTrace(tenantId, entity.getId(), Instant.now(), "SIGNED", "已签收");
    // 发布 ShipmentSignedEvent，把订单推进到 DELIVERED（与 ship() 同理，见类 javadoc）。
    domainEventPublisher.publishFrom(entity);
    return ShipmentDto.from(entity);
  }

  /**
   * 从渠道拉取物流轨迹并落库。
   *
   * <p>渠道查询失败时<strong>不清空</strong>已有轨迹，只返回空结果 + 记录失败原因 ——清空会让「渠道接口抖动」造成客服侧轨迹消失，是比不更新更糟的结果。
   */
  @Transactional
  public List<ShipmentTraceDto> syncTrace(Long shipmentId) {
    long tenantId = tenantProvider.currentTenantId();
    Shipment entity = requireShipment(tenantId, shipmentId);
    if (entity.getTrackingNo() == null || entity.getTrackingNo().isBlank()) {
      throw BlueprintErrors.of(BlueprintErrorCodes.SHIPMENT_TRACKING_NO_REQUIRED, shipmentId);
    }
    ChannelTraceResult result =
        channelExtensionPort.queryTrace(
            new ChannelShipmentContext(
                tenantId,
                entity.getChannelCode(),
                null,
                entity.getShipmentNo(),
                entity.getLogisticsCompany(),
                entity.getTrackingNo()));
    List<ShipmentTraceDto> saved = new ArrayList<>();
    if (result.success()) {
      for (ChannelTraceResult.TraceNode node : result.nodes()) {
        ShipmentTrace trace =
            ShipmentTrace.of(
                DistributedIdGenerator.generateLongId(),
                tenantId,
                entity.getId(),
                node.time(),
                node.status(),
                node.description());
        shipmentTraceRepository.insert(trace);
        saved.add(ShipmentTraceDto.from(trace));
      }
      if ("IN_TRANSIT".equals(nodeStatus(result))) {
        entity.markInTransit();
        shipmentRepository.update(entity);
      }
    } else {
      log.warn(
          "物流轨迹查询失败 | shipment={} | channel={} | code={}",
          shipmentId,
          entity.getChannelCode(),
          result.errorCode());
    }
    return saved;
  }

  /** 本地轨迹列表（按时间正序）。 */
  @Transactional(readOnly = true)
  public List<ShipmentTraceDto> traces(Long shipmentId) {
    long tenantId = tenantProvider.currentTenantId();
    return shipmentTraceRepository.findByShipment(tenantId, shipmentId).stream()
        .map(ShipmentTraceDto::from)
        .toList();
  }

  private void appendTrace(
      long tenantId, Long shipmentId, Instant time, String status, String desc) {
    ShipmentTrace trace =
        ShipmentTrace.of(
            DistributedIdGenerator.generateLongId(), tenantId, shipmentId, time, status, desc);
    shipmentTraceRepository.insert(trace);
  }

  private Shipment requireShipment(long tenantId, Long shipmentId) {
    Shipment entity = shipmentRepository.findById(shipmentId);
    if (entity == null) {
      throw BlueprintErrors.of(BlueprintErrorCodes.SHIPMENT_NOT_FOUND, shipmentId);
    }
    return entity;
  }

  /** 取轨迹里的最后一个状态码，用于决定是否推进到「运输中」。 */
  private static String nodeStatus(ChannelTraceResult result) {
    if (result.nodes() == null || result.nodes().isEmpty()) {
      return null;
    }
    return result.nodes().get(result.nodes().size() - 1).status();
  }
}
