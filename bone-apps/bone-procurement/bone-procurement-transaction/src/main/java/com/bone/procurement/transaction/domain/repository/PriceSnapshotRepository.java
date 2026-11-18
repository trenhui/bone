package com.bone.procurement.transaction.domain.repository;

import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.procurement.transaction.domain.model.pricing.PriceSnapshotEntity;
import java.util.List;
import java.util.Optional;

/** 价格快照仓储（只追加；无 update/delete 入口）。 */
public interface PriceSnapshotRepository extends Repository<PriceSnapshotEntity, Long> {

  default Optional<PriceSnapshotEntity> findByPoVersion(Long tenantId, String poNo, int poVersion) {
    List<PriceSnapshotEntity> list =
        findByCriteria(
            Criteria.<PriceSnapshotEntity>create()
                .entityClass(PriceSnapshotEntity.class)
                .eq("tenantId", tenantId)
                .eq("poNo", poNo)
                .eq("poVersion", poVersion));
    return list.stream().findFirst();
  }
}
