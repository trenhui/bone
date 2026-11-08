package com.bone.claim.domain.repository;

import com.bone.claim.domain.model.config.AdjFeeType;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.domain.annotation.TenantScope;
import com.bone.metadata.sdk.domain.annotation.TenantScopeMode;
import com.bone.metadata.sdk.query.criteria.Criteria;

/** 费用类型字典仓储（§9.5 A4，可变表）。 */
@TenantScope(TenantScopeMode.AUTO)
public interface AdjFeeTypeRepository extends Repository<AdjFeeType, Long> {

  /** 按租户 + 费用类型编码定位唯一字典项（UK {@code uk_fee_type_code}）。 */
  default AdjFeeType findByFeeTypeCode(Long tenantId, String feeTypeCode) {
    Criteria<AdjFeeType> criteria =
        Criteria.<AdjFeeType>create()
            .eq(AdjFeeType::getTenantId, tenantId)
            .eq(AdjFeeType::getFeeTypeCode, feeTypeCode);
    return findOneByCriteria(criteria);
  }
}
