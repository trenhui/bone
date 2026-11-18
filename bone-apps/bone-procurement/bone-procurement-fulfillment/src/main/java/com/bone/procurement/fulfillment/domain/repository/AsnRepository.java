package com.bone.procurement.fulfillment.domain.repository;

import com.bone.core.model.PageResult;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.procurement.fulfillment.domain.model.asn.Asn;
import java.util.List;
import java.util.Optional;

/** ASN 写侧仓储。 */
public interface AsnRepository extends Repository<Asn, Long> {

  default Optional<Asn> findByAsnNo(Long tenantId, String asnNo) {
    List<Asn> list =
        findByCriteria(
            Criteria.<Asn>create()
                .entityClass(Asn.class)
                .eq("tenantId", tenantId)
                .eq("asnNo", asnNo));
    return list.stream().findFirst();
  }

  default PageResult<Asn> findPageForVendor(
      Long tenantId, String vendorPartyId, int page, int size) {
    return pageByCriteria(
        Criteria.<Asn>create()
            .entityClass(Asn.class)
            .eq("tenantId", tenantId)
            .eq("vendorPartyId", vendorPartyId)
            .orderByDesc("createdAt")
            .page(page, size));
  }
}
