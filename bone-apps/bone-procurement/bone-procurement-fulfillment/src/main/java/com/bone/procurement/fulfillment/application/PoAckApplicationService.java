package com.bone.procurement.fulfillment.application;

import com.bone.core.annotation.NoDomainEvent;
import com.bone.procurement.fulfillment.application.command.AckCommand;
import com.bone.procurement.fulfillment.domain.FulfillmentErrorCodes;
import com.bone.procurement.fulfillment.domain.FulfillmentErrors;
import com.bone.procurement.fulfillment.domain.model.ack.PoAcknowledgement;
import com.bone.procurement.fulfillment.domain.port.PoQueryPort;
import com.bone.procurement.fulfillment.domain.port.VendorIdentityPort;
import com.bone.procurement.fulfillment.domain.repository.PoAcknowledgementRepository;
import java.lang.reflect.Method;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** PO 行级确认应用服务：身份绑定 + CAS 幂等 + 终态拒绝 + 驱动 PO ACKNOWLEDGED。 */
@Service
@NoDomainEvent
public class PoAckApplicationService {

  private static final String PO_SERVICE_BEAN = "purchaseOrderApplicationService";

  private final PoAcknowledgementRepository repository;
  private final PoQueryPort poQuery;
  private final VendorIdentityPort identity;
  private final ApplicationContext context;

  public PoAckApplicationService(
      PoAcknowledgementRepository repository,
      PoQueryPort poQuery,
      VendorIdentityPort identity,
      ApplicationContext context) {
    this.repository = repository;
    this.poQuery = poQuery;
    this.identity = identity;
    this.context = context;
  }

  @Transactional
  public PoAcknowledgement ack(AckCommand cmd) {
    String vendor = requireIdentity();
    PoQueryPort.PoSnapshot po =
        poQuery
            .findByNo(cmd.tenantId(), cmd.poNo())
            .orElseThrow(() -> FulfillmentErrors.of("PO_NOT_FOUND", cmd.poNo()));

    // M5-1：当前供应商必须是该 PO 的归属供应商，越权 403 + 零数据
    if (po.vendorPartyId() == null || !po.vendorPartyId().equals(vendor)) {
      throw FulfillmentErrors.of(FulfillmentErrorCodes.VENDOR_SCOPE_FORBIDDEN, "非本供应商的 PO");
    }
    // INV-02：已关闭/终态 PO 不可确认；必须是 SENT 或 ACKNOWLEDGED（重复确认走幂等）
    if (!"SENT".equals(po.status()) && !"ACKNOWLEDGED".equals(po.status())) {
      throw FulfillmentErrors.of(FulfillmentErrorCodes.INVALID_TRANSITION, "PO 状态=" + po.status());
    }
    if (cmd.poVersion() == null || cmd.poVersion() != po.version()) {
      throw FulfillmentErrors.of(
          FulfillmentErrorCodes.INVALID_TRANSITION,
          "版本不匹配：提交=" + cmd.poVersion() + ", 当前=" + po.version());
    }
    validateAckType(cmd.ackType());

    // INV-01：幂等键 (po_no, po_version, vendor)；重复确认返回首条
    Optional<PoAcknowledgement> existing =
        repository.findByIdempotentKey(cmd.tenantId(), cmd.poNo(), cmd.poVersion(), vendor);
    if (existing.isPresent()) {
      return existing.get();
    }

    PoAcknowledgement ack = new PoAcknowledgement();
    ack.setTenantId(cmd.tenantId());
    ack.setPoNo(cmd.poNo());
    ack.setPoVersion(cmd.poVersion());
    ack.setVendorPartyId(vendor);
    ack.setAckType(cmd.ackType());
    ack.setAckLinesJson(toJson(cmd.lines()));
    ack.setAckAt(new Date());
    ack.setVersion(0);
    Date now = new Date();
    ack.setCreatedAt(now);
    ack.setUpdatedAt(now);
    Long id = repository.save(ack);
    ack.setId(id);

    // 驱动 PO SENT → ACKNOWLEDGED（仅 SENT 时；已 ACK 的重复确认不再推进）
    if ("SENT".equals(po.status())) {
      drivePoTransition(cmd.tenantId(), cmd.poNo(), "ACKNOWLEDGE");
    }
    return ack;
  }

  private String requireIdentity() {
    try {
      return identity.requireVendorPartyId();
    } catch (IllegalStateException e) {
      throw FulfillmentErrors.of(FulfillmentErrorCodes.VENDOR_SCOPE_FORBIDDEN, "无供应商身份");
    }
  }

  private void validateAckType(String ackType) {
    try {
      PoAcknowledgement.AckType.valueOf(ackType);
    } catch (IllegalArgumentException | NullPointerException e) {
      throw FulfillmentErrors.of("INVALID_ACK_TYPE", String.valueOf(ackType));
    }
  }

  /** 反射调用 transaction 的通用迁移入口，避免 fulfillment domain 反向依赖 transaction。 */
  private void drivePoTransition(Long tenantId, String poNo, String event) {
    try {
      Object poService = context.getBean(PO_SERVICE_BEAN);
      Method transition =
          poService.getClass().getMethod("transition", Long.class, String.class, String.class);
      transition.invoke(poService, tenantId, poNo, event);
    } catch (ReflectiveOperationException | RuntimeException e) {
      throw FulfillmentErrors.of("PO_TRANSITION_FAILED", poNo, e);
    }
  }

  private String toJson(List<AckCommand.AckLine> lines) {
    if (lines == null || lines.isEmpty()) {
      return "[]";
    }
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < lines.size(); i++) {
      if (i > 0) sb.append(',');
      AckCommand.AckLine l = lines.get(i);
      sb.append("{\"lineNo\":")
          .append(l.lineNo())
          .append(",\"acceptedQty\":")
          .append(l.acceptedQty())
          .append(",\"reason\":")
          .append(jsonStr(l.reason()))
          .append('}');
    }
    return sb.append(']').toString();
  }

  private String jsonStr(String s) {
    return s == null ? "null" : "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
  }
}
