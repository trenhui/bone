package com.bone.procurement.sourcing.domain.repository;

import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.procurement.sourcing.domain.model.Rfx;
import java.util.List;
import java.util.Optional;

/** RFx 写侧仓储：查询落在 domain.repository 的 default 方法。 */
public interface RfxSqlRepository extends Repository<Rfx, Long> {

  default Optional<Rfx> findByRfxNo(Long tenantId, String rfxNo) {
    List<Rfx> list =
        findByCriteria(
            Criteria.<Rfx>create()
                .entityClass(Rfx.class)
                .eq("tenantId", tenantId)
                .eq("rfxNo", rfxNo));
    return list.stream().findFirst();
  }
}
