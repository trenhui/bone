package com.bone.tpa.sdk.tpasaasdb.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bone.tpa.sdk.claim.model.Claim;
import com.bone.tpa.sdk.tpasaasdb.model.ClaimPushFailDO;
import com.bone.tpa.sdk.tpasaasdb.model.ClaimPushFailOperateDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.web.bind.annotation.PostMapping;

@Mapper
public interface ClaimMapper extends BaseMapper<Claim> {

    /**
     * 分页查询赔案推送失败记录
     *
     * @param page
     * @param batchNo
     * @param claimNo
     * @param policyNo
     * @param outInsureName
     * @param outInsureIdentityNo
     * @param insureName
     * @param insuranceName
     * @param branchName
     * @param errorType
     * @param auditingOperatorName
     * @param vipSign
     * @param pkPushStatus
     * @param insurancePushStatus
     * @return
     */
    Page<ClaimPushFailDO> pageClaimPushFailDO(Page page,
                                              @Param("batchNo") String batchNo,
                                              @Param("claimNo") String claimNo,
                                              @Param("policyNo") String policyNo,
                                              @Param("outInsureName") String outInsureName,
                                              @Param("outInsureIdentityNo") String outInsureIdentityNo,
                                              @Param("insureName") String insureName,
                                              @Param("insuranceName") String insuranceName,
                                              @Param("branchName") String branchName,
                                              @Param("errorType") String errorType,
                                              @Param("auditingOperatorName") String auditingOperatorName,
                                              @Param("vipSign") String vipSign,
                                              @Param("pkPushStatus") String pkPushStatus,
                                              @Param("insurancePushStatus") String insurancePushStatus
                                              );


    /**
     *
     * @param page
     * @param batchNo
     * @param claimNo
     * @param policyNo
     * @param outInsureName
     * @param outInsureIdentityNo
     * @param insureName
     * @param insuranceName
     * @param branchName
     * @param errorType
     * @param auditingOperatorName
     * @param vipSign
     * @param pkPushStatus
     * @param insurancePushStatus
     * @return
     */
    Page<ClaimPushFailOperateDO> pageClaimPushFailOperateDO(Page page,
                                                            @Param("batchNo") String batchNo,
                                                            @Param("claimNo") String claimNo,
                                                            @Param("policyNo") String policyNo,
                                                            @Param("outInsureName") String outInsureName,
                                                            @Param("outInsureIdentityNo") String outInsureIdentityNo,
                                                            @Param("insureName") String insureName,
                                                            @Param("insuranceName") String insuranceName,
                                                            @Param("branchName") String branchName,
                                                            @Param("errorType") String errorType,
                                                            @Param("auditingOperatorName") String auditingOperatorName,
                                                            @Param("vipSign") String vipSign,
                                                            @Param("pkPushStatus") String pkPushStatus,
                                                            @Param("insurancePushStatus") String insurancePushStatus
    );

}
