package com.bone.masterdata.domain.repository;

import com.bone.masterdata.domain.model.subscription.EntitySubscription;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.criteria.Criteria;
import java.util.List;

/** 消费订阅仓储（G10）。 */
public interface EntitySubscriptionRepository extends Repository<EntitySubscription, Long> {

  default List<EntitySubscription> findByEntityId(Long masterDataEntityId) {
    return findByCriteria(
        Criteria.<EntitySubscription>create()
            .entityClass(EntitySubscription.class)
            .eq("masterDataEntityId", masterDataEntityId));
  }

  default long countByEntityAppAndMode(Long masterDataEntityId, Long appId, String subscribeMode) {
    return countByCriteria(
        Criteria.<EntitySubscription>create()
            .entityClass(EntitySubscription.class)
            .eq("masterDataEntityId", masterDataEntityId)
            .eq("appId", appId)
            .eq("subscribeMode", subscribeMode));
  }
}
