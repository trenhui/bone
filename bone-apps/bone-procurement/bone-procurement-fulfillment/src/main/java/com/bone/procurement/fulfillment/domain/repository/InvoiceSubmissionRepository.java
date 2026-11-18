package com.bone.procurement.fulfillment.domain.repository;

import com.bone.core.model.PageResult;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.procurement.fulfillment.domain.model.invoice.InvoiceSubmission;
import java.util.List;
import java.util.Optional;

/** 发票提交仓储。去重键 (tenant_id, invoice_no, vendor_party_id)。 */
public interface InvoiceSubmissionRepository extends Repository<InvoiceSubmission, Long> {

  default Optional<InvoiceSubmission> findByDedupKey(
      Long tenantId, String invoiceNo, String vendorPartyId) {
    List<InvoiceSubmission> list =
        findByCriteria(
            Criteria.<InvoiceSubmission>create()
                .entityClass(InvoiceSubmission.class)
                .eq("tenantId", tenantId)
                .eq("invoiceNo", invoiceNo)
                .eq("vendorPartyId", vendorPartyId));
    return list.stream().findFirst();
  }

  default PageResult<InvoiceSubmission> findPage(
      Long tenantId, String vendorPartyId, int page, int size) {
    return pageByCriteria(
        Criteria.<InvoiceSubmission>create()
            .entityClass(InvoiceSubmission.class)
            .eq("tenantId", tenantId)
            .eq("vendorPartyId", vendorPartyId)
            .orderByDesc("createdAt")
            .page(page, size));
  }

  /** 按租户分页（供应商隔离由调用方按 vendor 过滤，供内部归属反查）。 */
  default PageResult<InvoiceSubmission> findPageByTenant(Long tenantId, int page, int size) {
    return pageByCriteria(
        Criteria.<InvoiceSubmission>create()
            .entityClass(InvoiceSubmission.class)
            .eq("tenantId", tenantId)
            .orderByDesc("createdAt")
            .page(page, size));
  }
}
