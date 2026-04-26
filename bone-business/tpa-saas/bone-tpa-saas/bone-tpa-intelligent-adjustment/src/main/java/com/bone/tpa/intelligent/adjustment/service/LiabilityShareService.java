package com.bone.tpa.intelligent.adjustment.service;

import com.bone.tpa.intelligent.adjustment.dto.LiabilityShareDTO;

import java.util.List;

public interface LiabilityShareService {

    List<LiabilityShareDTO> findDraftListByPolicyNo(String policyNo);
    /**
     * 根据id加载
     * @param id
     * @return
     */
    LiabilityShareDTO findById(Long id);

    /**
     * 根据共保代码查询
     * @param planId
     * @param code
     * @return
     */
    LiabilityShareDTO findByCode(Long planId,String code);



    /**
     * 根据计划id加载
     * @param planId
     * @return
     */
    List<LiabilityShareDTO> findByPlanId(Long planId);

    /**
     * 保存责任编码
     * @param liabilityShareDTO
     */
    void create(List<LiabilityShareDTO> liabilityShareDTOList);

    /**
     * 更新共保额度
     * @param dto
     */
    void updateLimit( LiabilityShareDTO  dto);


    /**
     * 删除共保
     * @param id
     */
    void delete(Long id);




}
