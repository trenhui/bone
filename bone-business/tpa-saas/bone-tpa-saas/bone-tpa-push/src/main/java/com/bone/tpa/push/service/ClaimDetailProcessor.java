package com.bone.tpa.push.service;

import com.bone.tpa.sdk.masterdb.model.TbOverClaimDetailExtend;

import java.util.List;

public interface ClaimDetailProcessor {

    /**
     * 是否支持当前保险公司或分支机构
     */
    boolean supports(String insuranceName, String branchName);

    /**
     * 执行明细处理逻辑
     */
    List<TbOverClaimDetailExtend> process(
            List<TbOverClaimDetailExtend> detailExtends,
            String finalDutyId,
            String finalTreatmentType);
}
