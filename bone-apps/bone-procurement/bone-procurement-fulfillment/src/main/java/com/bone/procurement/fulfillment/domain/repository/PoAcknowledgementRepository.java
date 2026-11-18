package com.bone.procurement.fulfillment.domain.repository;

import com.bone.core.model.PageResult;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.procurement.fulfillment.domain.model.ack.PoAcknowledgement;
import java.util.List;
import java.util.Optional;

/** PO 确认写侧仓储。幂等键 (tenant_id, po_no, po_version, vendor_party_id)。 */
public interface PoAcknowledgementRepository extends Repository<PoAcknowledgement, Long> {

  default Optional<PoAcknowledgement> findByIdempotentKey(
      Long tenantId, String poNo, Integer poVersion, String vendorPartyId) {
    List<PoAcknowledgement> list =
        findByCriteria(
            Criteria.<PoAcknowledgement>create()
                .entityClass(PoAcknowledgement.class)
                .eq("tenantId", tenantId)
                .eq("poNo", poNo)
                .eq("poVersion", poVersion)
                .eq("vendorPartyId", vendorPartyId));
    return list.stream().findFirst();
  }

  default PageResult<PoAcknowledgement> findPageForVendor(
      Long tenantId, String vendorPartyId, int page, int size) {
    return pageByCriteria(
        Criteria.<PoAcknowledgement>create()
            .entityClass(PoAcknowledgement.class)
            .eq("tenantId", tenantId)
            .eq("vendorPartyId", vendorPartyId)
            .orderByDesc("createdAt")
            .page(page, size));
  }
}
