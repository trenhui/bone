package com.bone.procurement.fulfillment.application;

import com.bone.core.annotation.NoDomainEvent;
import com.bone.procurement.fulfillment.application.command.AsnCommand;
import com.bone.procurement.fulfillment.domain.FulfillmentErrorCodes;
import com.bone.procurement.fulfillment.domain.FulfillmentErrors;
import com.bone.procurement.fulfillment.domain.model.asn.Asn;
import com.bone.procurement.fulfillment.domain.model.asn.AsnLine;
import com.bone.procurement.fulfillment.domain.model.asn.AsnStatus;
import com.bone.procurement.fulfillment.domain.port.PoQueryPort;
import com.bone.procurement.fulfillment.domain.port.VendorIdentityPort;
import com.bone.procurement.fulfillment.domain.repository.AsnLineRepository;
import com.bone.procurement.fulfillment.domain.repository.AsnRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** ASN 应用服务：asn_no 唯一 + 行引用已确认 PO + 数量/包装校验 + 显式迁移。 */
@Service
@NoDomainEvent
public class AsnApplicationService {

  private final AsnRepository asnRepository;
  private final AsnLineRepository lineRepository;
  private final PoQueryPort poQuery;
  private final VendorIdentityPort identity;

  public AsnApplicationService(
      AsnRepository asnRepository,
      AsnLineRepository lineRepository,
      PoQueryPort poQuery,
      VendorIdentityPort identity) {
    this.asnRepository = asnRepository;
    this.lineRepository = lineRepository;
    this.poQuery = poQuery;
    this.identity = identity;
  }

  @Transactional
  public Asn create(AsnCommand cmd) {
    String vendor = requireIdentity();
    // INV-03：asn_no 租户内唯一（重传幂等）
    Optional<Asn> existing = asnRepository.findByAsnNo(cmd.tenantId(), cmd.asnNo());
    if (existing.isPresent()) {
      if (!vendor.equals(existing.get().getVendorPartyId())) {
        throw FulfillmentErrors.of(FulfillmentErrorCodes.VENDOR_SCOPE_FORBIDDEN, "非本供应商的 ASN");
      }
      return existing.get();
    }

    PoQueryPort.PoSnapshot po =
        poQuery
            .findByNo(cmd.tenantId(), cmd.poNo())
            .orElseThrow(() -> FulfillmentErrors.of("PO_NOT_FOUND", cmd.poNo()));
    // M5-3：未确认 PO 直接发 ASN → 31503
    if (!po.isAcknowledged()) {
      throw FulfillmentErrors.of(
          FulfillmentErrorCodes.PO_NOT_ACKNOWLEDGED, "PO 未确认或已终态：" + cmd.poNo());
    }
    if (po.vendorPartyId() == null || !po.vendorPartyId().equals(vendor)) {
      throw FulfillmentErrors.of(FulfillmentErrorCodes.VENDOR_SCOPE_FORBIDDEN, "非本供应商的 PO");
    }

    // INV-04：行必须引用已确认 PO 的有效行，数量 ≤ PO 行数量
    validateLines(cmd, po);

    // 包装层级：每个 pack_ref 必须指向已声明的 SSCC
    Set<String> ssccSet = cmd.sscc() == null ? Set.of() : new HashSet<>(cmd.sscc());
    if (cmd.lines() != null) {
      for (AsnCommand.Line l : cmd.lines()) {
        if (l.packRef() != null && !l.packRef().isBlank() && !ssccSet.contains(l.packRef())) {
          throw FulfillmentErrors.of("ASN_PACK_REF_INVALID", l.packRef());
        }
      }
    }

    Asn asn = new Asn();
    asn.setTenantId(cmd.tenantId());
    asn.setAsnNo(cmd.asnNo());
    asn.setPoNo(cmd.poNo());
    asn.setVendorPartyId(vendor);
    asn.setShipDate(cmd.shipDate() == null ? null : parseDate(cmd.shipDate()));
    asn.setCarrier(cmd.carrier());
    asn.setTrackingNo(cmd.trackingNo());
    asn.setSsccJson(toJson(cmd.sscc()));
    asn.setStatus(AsnStatus.CREATED.name());
    asn.setVersion(0);
    Date now = new Date();
    asn.setCreatedAt(now);
    asn.setUpdatedAt(now);
    Long id = asnRepository.save(asn);
    asn.setId(id);

    int lineNo = 1;
    if (cmd.lines() != null) {
      for (AsnCommand.Line l : cmd.lines()) {
        AsnLine line = new AsnLine();
        line.setTenantId(cmd.tenantId());
        line.setAsnId(id);
        line.setLineNo(lineNo++);
        line.setPoLineNo(l.poLineNo());
        line.setItemId(l.itemId());
        line.setQty(l.qty());
        line.setUom(l.uom());
        line.setPackRef(l.packRef());
        line.setVersion(0);
        line.setCreatedAt(now);
        line.setUpdatedAt(now);
        lineRepository.save(line);
      }
    }
    return asn;
  }

  @Transactional
  public Asn transmit(Long tenantId, String asnNo) {
    String vendor = requireIdentity();
    Asn asn =
        asnRepository
            .findByAsnNo(tenantId, asnNo)
            .orElseThrow(() -> FulfillmentErrors.of("ASN_NOT_FOUND", asnNo));
    if (!vendor.equals(asn.getVendorPartyId())) {
      throw FulfillmentErrors.of(FulfillmentErrorCodes.VENDOR_SCOPE_FORBIDDEN, "非本供应商的 ASN");
    }
    asn.setStatus(
        AsnStatus.transition(AsnStatus.valueOf(asn.getStatus()), AsnStatus.Event.transmit).name());
    asnRepository.save(asn);
    return asn;
  }

  private void validateLines(AsnCommand cmd, PoQueryPort.PoSnapshot po) {
    if (cmd.lines() == null || cmd.lines().isEmpty()) {
      throw FulfillmentErrors.of("ASN_LINES_EMPTY", cmd.asnNo());
    }
    for (AsnCommand.Line l : cmd.lines()) {
      PoQueryPort.PoSnapshot.Line poLine =
          po.lines().stream().filter(x -> x.lineNo().equals(l.poLineNo())).findFirst().orElse(null);
      if (poLine == null) {
        throw FulfillmentErrors.of("ASN_PO_LINE_INVALID", "PO 行不存在：" + l.poLineNo());
      }
      if (l.qty() == null
          || l.qty().compareTo(poLine.qty() == null ? BigDecimal.ZERO : poLine.qty()) > 0) {
        throw FulfillmentErrors.of("ASN_QTY_EXCEED", "行 " + l.poLineNo() + " 发货数量超过 PO 确认数量");
      }
    }
  }

  private String requireIdentity() {
    try {
      return identity.requireVendorPartyId();
    } catch (IllegalStateException e) {
      throw FulfillmentErrors.of(FulfillmentErrorCodes.VENDOR_SCOPE_FORBIDDEN, "无供应商身份");
    }
  }

  private Date parseDate(String s) {
    try {
      return Date.from(LocalDate.parse(s).atStartOfDay(ZoneOffset.UTC).toInstant());
    } catch (RuntimeException e) {
      throw FulfillmentErrors.of("ASN_SHIP_DATE_INVALID", s);
    }
  }

  private String toJson(java.util.List<String> sscc) {
    if (sscc == null || sscc.isEmpty()) return "[]";
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < sscc.size(); i++) {
      if (i > 0) sb.append(',');
      sb.append('"').append(sscc.get(i).replace("\"", "\\\"")).append('"');
    }
    return sb.append(']').toString();
  }
}
