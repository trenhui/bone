package com.bone.blueprint.domain.model.shipment;

import com.bone.blueprint.domain.model.shipment.event.ShipmentShippedEvent;
import com.bone.blueprint.domain.model.shipment.event.ShipmentSignedEvent;
import com.bone.blueprint.domain.model.shipment.valueobject.ShipmentStatus;
import com.bone.core.domain.TenantAggregateRoot;
import com.bone.metadata.sdk.domain.annotation.Table;
import com.bone.metadata.sdk.domain.annotation.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 发货单聚合根 —— 一笔订单的履约凭证。
 *
 * <p><b>为何与 Order 分开</b>：订单回答「卖了什么、收了多少钱」，发货单回答「怎么送到、送到哪儿、物流到哪一步」。 二者生命周期不同（一单可能拆多包裹、也可能退换货重发），强行塞进
 * Order 会让 Order 聚合膨胀到无法维护， 且 Order 的状态机（CREATED→PAID→SHIPPED→DELIVERED）与物流轨迹（运输中/派送中/已签收）粒度不一致。
 *
 * <p><b>渠道回传 {@code channelAck}</b>：渠道订单必须把运单号回传到平台（否则平台判定虚假发货并罚款）。
 * 回传失败<strong>不回退发货状态</strong>——货已经发出去了，状态回退会制造「未发货」的假象， 正确做法是保留 SHIPPED + 置 FAILED +
 * 记录原因，由补偿任务重试回传。
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("bp_shipment")
public class Shipment extends TenantAggregateRoot<Long> {

  private static final DateTimeFormatter NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

  private Long orderId;
  private String channelCode;
  private String shipmentNo;
  private String logisticsCompany;
  private String trackingNo;
  private ShipmentStatus status;
  private String receiverName;
  private String receiverPhone;
  private String receiverAddress;
  private Boolean channelAck;
  private Instant channelAckAt;
  private Instant shippedAt;
  private Instant signedAt;
  private String failReason;
  private String remark;
  private Instant createdAt;
  private Instant updatedAt;

  @Version private Long version;

  private Shipment(
      long id,
      Long tenantId,
      Long orderId,
      String channelCode,
      String receiverName,
      String receiverPhone,
      String receiverAddress) {
    setId(id);
    setTenantId(tenantId);
    this.orderId = orderId;
    this.channelCode = channelCode;
    this.receiverName = receiverName;
    this.receiverPhone = receiverPhone;
    this.receiverAddress = receiverAddress;
    this.status = ShipmentStatus.CREATED;
    this.channelAck = Boolean.FALSE;
    touch();
  }

  public static Shipment create(
      long id,
      Long tenantId,
      Long orderId,
      String channelCode,
      String receiverName,
      String receiverPhone,
      String receiverAddress) {
    Shipment shipment =
        new Shipment(
            id, tenantId, orderId, channelCode, receiverName, receiverPhone, receiverAddress);
    shipment.shipmentNo = generateShipmentNo(id);
    return shipment;
  }

  /**
   * 发货：生成运单号，进入 SHIPPED。
   *
   * @param trackingNo 运单号，必填——没有运单号的「已发货」是虚假发货
   */
  public void ship(String logisticsCompany, String trackingNo, Instant at) {

    touch();
    if (!status.canShip()) {
      throw new IllegalStateException("当前状态不允许发货: " + status + "（shipment=" + idForLog() + "）");
    }
    if (trackingNo == null || trackingNo.isBlank()) {
      throw new IllegalArgumentException("运单号不能为空");
    }
    this.logisticsCompany = logisticsCompany;
    this.trackingNo = trackingNo;
    this.status = ShipmentStatus.SHIPPED;
    this.shippedAt = at;
    this.failReason = null;
    // 记录事件：把「货已交运」同步给 Order 聚合（跨聚合边界只能走事件，见 ShipmentShippedEvent javadoc）。
    // orderId 为 null 表示该发货单未挂订单（历史数据/手工发货），此时不产生联动事件。
    if (this.orderId != null) {
      addDomainEvent(
          new ShipmentShippedEvent(
              this.orderId, getTenantId(), getId(), this.channelCode, trackingNo, at));
    }
  }

  /** 标记渠道回传成功。 */
  public void markChannelAcked(Instant at) {

    touch();
    this.channelAck = Boolean.TRUE;
    this.channelAckAt = at;
  }

  /** 渠道回传失败：不回退发货状态，只记录原因交由补偿重试。 */
  public void markChannelAckFailed(String reason) {

    touch();
    this.channelAck = Boolean.FALSE;
    this.failReason = reason == null ? "渠道回传失败" : reason;
  }

  /** 更新物流轨迹推进的状态（运输中）。 */
  public void markInTransit() {

    touch();
    if (this.status == ShipmentStatus.SHIPPED) {
      this.status = ShipmentStatus.IN_TRANSIT;
    }
  }

  /** 签收。 */
  public void sign(Instant at) {

    touch();
    if (!status.canSign()) {
      throw new IllegalStateException("当前状态不允许签收: " + status + "（shipment=" + idForLog() + "）");
    }
    this.status = ShipmentStatus.SIGNED;
    this.signedAt = at;
    // 同ship()：签收也要同步给订单，否则订单停在 SHIPPED 与「已签收」事实不符。
    if (this.orderId != null) {
      addDomainEvent(new ShipmentSignedEvent(this.orderId, getTenantId(), getId(), at));
    }
  }

  /** 发货失败（地址不可达 / 渠道拒收）。 */
  public void markFailed(String reason) {

    touch();
    this.status = ShipmentStatus.FAILED;
    this.failReason = reason == null ? "发货失败" : reason;
  }

  public void updateReceiver(String name, String phone, String address) {

    touch();
    this.receiverName = name;
    this.receiverPhone = phone;
    this.receiverAddress = address;
  }

  public void updateRemark(String remark) {

    touch();
    this.remark = remark;
  }

  private String idForLog() {
    return String.valueOf(getId());
  }

  private static String generateShipmentNo(long id) {
    return "SF" + LocalDate.now().format(NO_FORMATTER) + id;
  }

  /** 刷新修改时间；创建时间只在构造时赋值一次，之后不再变动。 */
  private void touch() {
    this.updatedAt = Instant.now();
    if (this.createdAt == null) {
      this.createdAt = this.updatedAt;
    }
  }
}
