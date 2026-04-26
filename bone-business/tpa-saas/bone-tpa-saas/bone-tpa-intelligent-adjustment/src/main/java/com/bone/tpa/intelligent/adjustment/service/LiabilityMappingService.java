package com.bone.tpa.intelligent.adjustment.service;

import com.bone.tpa.sdk.adjustment.model.LiabilityMapping;

import java.util.List;

public interface LiabilityMappingService {

    List<LiabilityMapping> getByPolicyNo(String policyNo);

    List<LiabilityMapping> getByPlanUuid(String planUuid);

    LiabilityMapping getByLiabilityAndVisitType(String liabilityUuid, String visitType);

    void batchAdd(List<LiabilityMapping> insertList);

    void batchUpdateById(List<LiabilityMapping> updateList);

    void updateById(LiabilityMapping mapping);

    void batchDeleteById(List<Long> deleteList);

    void deleteByPolicyNo(String policyNo);
}
