package com.bone.tpa.intelligent.adjustment.service;

import com.bone.tpa.intelligent.adjustment.dto.CoverageDTO;

import java.util.List;

public interface CoverageService {

    List<CoverageDTO> queryByPlanId(Long planId);


    CoverageDTO findById(Long id );

    List<CoverageDTO> findByIds(List<Long> ids);


    /**
     * 编辑保存险种
     * @param coverageDTO
     * @return
     */
    void  save(List< CoverageDTO> coverageDTOList);

}
