package com.bone.procurement.fulfillment.application;

import com.bone.procurement.fulfillment.domain.model.ack.PoAcknowledgement;
import com.bone.procurement.fulfillment.domain.model.asn.Asn;
import com.bone.procurement.fulfillment.domain.model.exception.IrException;
import com.bone.procurement.fulfillment.domain.model.invoice.InvoiceSubmission;
import com.bone.procurement.fulfillment.domain.port.VendorIdentityPort;
import com.bone.procurement.fulfillment.domain.repository.AsnRepository;
import com.bone.procurement.fulfillment.domain.repository.InvoiceSubmissionRepository;
import com.bone.procurement.fulfillment.domain.repository.IrExceptionRepository;
import com.bone.procurement.fulfillment.domain.repository.PoAcknowledgementRepository;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 履约只读聚合查询：按 PO 汇总 Ack/ASN/开票/例外，越权返回空。 */
@Service
public class FulfillmentQueryService {

  private final PoAcknowledgementRepository ackRepository;
  private final AsnRepository asnRepository;
  private final InvoiceSubmissionRepository invoiceRepository;
  private final IrExceptionRepository exceptionRepository;
  private final VendorIdentityPort identity;

  public FulfillmentQueryService(
      PoAcknowledgementRepository ackRepository,
      AsnRepository asnRepository,
      InvoiceSubmissionRepository invoiceRepository,
      IrExceptionRepository exceptionRepository,
      VendorIdentityPort identity) {
    this.ackRepository = ackRepository;
    this.asnRepository = asnRepository;
    this.invoiceRepository = invoiceRepository;
    this.exceptionRepository = exceptionRepository;
    this.identity = identity;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> view(Long tenantId, String poNo) {
    String vendor = identity.currentVendorPartyId().orElse("");
    Map<String, Object> out = new java.util.LinkedHashMap<>();
    out.put("poNo", poNo);

    List<PoAcknowledgement> acks =
        allAcks(tenantId).stream()
            .filter(a -> poNo.equals(a.getPoNo()) && a.getVendorPartyId().equals(vendor))
            .toList();
    List<Asn> asns =
        allAsns(tenantId).stream()
            .filter(a -> poNo.equals(a.getPoNo()) && a.getVendorPartyId().equals(vendor))
            .toList();
    List<InvoiceSubmission> invoices =
        invoiceRepository.findPage(tenantId, vendor, 1, 200).getRecords().stream()
            .filter(i -> poNo.equals(i.getPoNo()))
            .toList();
    List<IrException> exceptions =
        exceptionRepository.findPage(tenantId, 1, 200).getRecords().stream()
            .filter(e -> invoices.stream().anyMatch(i -> i.getInvoiceNo().equals(e.getInvoiceNo())))
            .toList();

    out.put("acknowledged", !acks.isEmpty());
    out.put("acks", acks);
    out.put("asns", asns);
    out.put("invoices", invoices);
    out.put("exceptions", exceptions);
    return out;
  }

  private List<PoAcknowledgement> allAcks(Long tenantId) {
    return ackRepository
        .findPageForVendor(tenantId, identity.currentVendorPartyId().orElse(""), 1, 200)
        .getRecords();
  }

  private List<Asn> allAsns(Long tenantId) {
    return asnRepository
        .findPageForVendor(tenantId, identity.currentVendorPartyId().orElse(""), 1, 200)
        .getRecords();
  }
}
