package com.bone.procurement.fulfillment.domain.repository;

import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.procurement.fulfillment.domain.model.asn.AsnLine;
import java.util.List;

/** ASN 行仓储。 */
public interface AsnLineRepository extends Repository<AsnLine, Long> {

  default List<AsnLine> findByAsnId(Long tenantId, Long asnId) {
    return findByCriteria(
        Criteria.<AsnLine>create()
            .entityClass(AsnLine.class)
            .eq("tenantId", tenantId)
            .eq("asnId", asnId));
  }
}
