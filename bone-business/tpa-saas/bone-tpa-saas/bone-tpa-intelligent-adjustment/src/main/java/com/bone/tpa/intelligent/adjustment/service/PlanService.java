package com.bone.tpa.intelligent.adjustment.service;

import com.bone.tpa.intelligent.adjustment.dto.PlanDTO;
import com.bone.tpa.sdk.adjustment.enums.PlanStatus;
import com.bone.tpa.sdk.adjustment.model.Plan;

import java.util.List;

public interface PlanService {

    List<PlanDTO> convert(List<Plan> inList);

    /**
     * 创建计划
     * @param planDTO
     * @return
     */
    PlanDTO createPlan(PlanDTO planDTO);

    List<PlanDTO> createPlanList(List<PlanDTO> planDTOList);

    /**
     * 更新计划
     * @param planDTO
     * @return
     */
    PlanDTO updatePlan(PlanDTO planDTO);

    PlanDTO findById(Long id);


    List<PlanDTO> findByIdList(List<Long> idList);


    List<PlanDTO> queryByPolicyNo(String policyNo, PlanStatus statusEm);

    PlanDTO queryByPlanUuid(String planUuid);

    /**
     *
     * @param planId
     * @return
     */
    void checkPlanDraft(Long planId);


    Boolean deployPlan(Long planId);


    void rollBackFromPlan(Long planId);
}
