package com.bone.tpa.intelligent.adjustment.service;

import com.bone.tpa.intelligent.adjustment.dto.request.ChainLiabilityRequest;
import com.bone.tpa.intelligent.adjustment.dto.request.LiabilityCreateBatchRequest;
import com.bone.tpa.sdk.adjustment.model.liability.LiabilityConfig;
import com.bone.tpa.sdk.adjustment.model.Liability;

import java.util.List;
import java.util.Map;

public interface LiabilityService {

    /**
     * 检查保单是否已经启用
     */
    void checkPolicyConfigStatus(String policyNo);

    void checkPolicyConfigStatus(Long planId);

    /**
     * 根据计划id集合查询责任
     */
    List<Liability> queryByPlanIds(List<Long> planIds);

    /**
     * 根据计划id查询责任
     */
    List<Liability> getByPlanId(Long planId);

    LiabilityConfig findById(Long id);

    /**
     * 创建责任
     * @param request
     */
    void create(LiabilityCreateBatchRequest request);


    void update(LiabilityConfig config);

    /**
     * 根据计划id查询责任
     * @param planId
     * @return
     */
    List<LiabilityConfig> queryByPlanId(Long planId);


    List<LiabilityConfig> queryDraftListByPolicyNo(String policyNo);


    LiabilityConfig queryByUuidPlanId(Long planId,String liabilityUuid);


    List<LiabilityConfig> queryForBind(Long planId);


    /**
     * 查询已经被删除的责任
     *
     */
    LiabilityConfig queryDeletedLiability(String liabilityUuid);


    /**
     * 获取先后赔付责任的map
     * @param planId
     * @return
     * key 首赔付责任的id
     * value 赔付链路
     *
     * 如果出现循环依赖，或者超出3限制，则报错
     */
    Map<Long,List<LiabilityConfig>> chainLiabilityMap(Long planId);

    /**
     * 保存先后责任
     * @param request
     */
    void saveChainLiability(ChainLiabilityRequest request);


    /**
     * 删除先后责任
     * @param id
     */
    void deleteChainLiability(Long id);


    /**
     * 发布之前的校验
     * @param id
     */
    void checkForDeploy( List<Liability> liabilityList);



}
