package com.bone.procurement.qualification.domain.repository;

import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.procurement.qualification.domain.model.qualification.Qualification;
import com.bone.procurement.qualification.domain.model.qualification.VerifyStatus;
import java.util.Date;
import java.util.List;

/** 证照写侧仓储：到期巡检查询落在 default 方法，EXPIRED 终态一律排除（INV-10）。 */
public interface QualificationSqlRepository extends Repository<Qualification, Long> {

  /** 30 天内到期且非 EXPIRED 的证照；租户锚定，不跨租户。 */
  default List<Qualification> findExpiring(Long tenantId) {
    Date limit = new Date(System.currentTimeMillis() + 30L * 86400000L);
    return findByCriteria(
        Criteria.<Qualification>create()
            .entityClass(Qualification.class)
            .eq("tenantId", tenantId)
            .isNotNull("expiresAt")
            .lt("expiresAt", limit)
            .ne("verifyStatus", VerifyStatus.EXPIRED.name()));
  }
}
