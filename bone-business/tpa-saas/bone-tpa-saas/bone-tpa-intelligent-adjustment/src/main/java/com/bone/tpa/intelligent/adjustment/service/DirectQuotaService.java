package com.bone.tpa.intelligent.adjustment.service;


import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bone.tpa.api.ApiResult;
import com.bone.tpa.facade.feign.DirectPaymentFeignClient;
import com.bone.tpa.facade.feign.PooledAccountFeignClient;
import com.bone.tpa.facade.feign.YcClaimFeignClient;
import com.bone.tpa.facade.request.ClaimDetail;
import com.bone.tpa.facade.request.PooledAccountOperationRequest;
import com.bone.tpa.facade.request.QuotaInfoReq;
import com.bone.tpa.facade.vo.PeopleInfoResponse;
import com.bone.tpa.sdk.claim.enums.BizErrorCode;
import com.bone.tpa.sdk.claim.enums.PersonTypeEnum;
import com.bone.tpa.sdk.claim.enums.PkPushStatusEnum;
import com.bone.tpa.sdk.claim.exception.TpaBizException;
import com.bone.tpa.sdk.claim.model.Claim;
import com.bone.tpa.sdk.claim.model.ClaimInvoice;
import com.bone.tpa.sdk.claim.model.ClaimStakeholder;
import com.bone.tpa.sdk.service.ClaimInfoService;
import com.bone.tpa.sdk.util.DateUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 直付控额相关服务
 */
@Slf4j
@Service
public class DirectQuotaService {

    @Autowired
    private ClaimInfoService claimInfoService;

    @Autowired
    private PooledAccountFeignClient pooledAccountFeignClient;

    @Autowired
    private DirectPaymentFeignClient directPaymentFeignClient;

    @Autowired
    private YcClaimFeignClient ycClaimFeignClient;


    /**
     *理赔结果发送到直付
     * @param zfAmountModels 责任的赔付额度
     * @return
     */
//    private void uploadZfAmount(List<ZfAmountModel> zfAmountModels) throws Exception {
//        ClaimDetail[] claimDetails = new ClaimDetail[zfAmountModels.size()];
//        insertClaimEntryRequest insertClaimEntryRequest = new insertClaimEntryRequest();
//        for (int i = 0; i < zfAmountModels.size(); i++) {
//            ClaimDetail claimDetail = new ClaimDetail();
//            claimDetail.setClaimdCode(String.valueOf(i + 1));
//            if (scClaimdetailxp != null) {
//                claimDetail.setClaimdPersonName(insurerName);
//                claimDetail.setClaimdPersonCertId(insurerIdentityNo);
//                String policyNo = selectMorePolicyInfo(zfAmountModels.get(i).getResponsibilityguid());
//                claimDetail.setSlipCode(policyNo);
//                claimDetail.setClaimdApplyAmt(zfAmountModels.get(i).getCompensationAmount().toString());
//                claimDetail.setClaimdApplyAbtmAmt("0");
//                claimDetail.setClaimdApprovedAmt("0");
//                claimDetail.setCertOrMobile(0);
//                List<np_insurancetype> npInsurancetypeList = npInsurancetypeMapper.selectList(new QueryWrapper<np_insurancetype>()
//                        .eq("IsDelete", 0)
//                        .eq("ID", zfAmountModels.get(i).getInsuranceguid()));
//
//                if (!CollectionUtils.isEmpty(npInsurancetypeList)) {
//                    claimDetail.setDutySubCode(npInsurancetypeList.get(0).getInsurancecode());
//                }
//                List<np_responsibility> npResponsibilities = npResponsibilityMapper.selectList(new QueryWrapper<np_responsibility>()
//                        .eq("IsDelete", 0)
//                        .eq("id", zfAmountModels.get(i).getResponsibilityguid()));
//
//                if (!CollectionUtils.isEmpty(npResponsibilities)) {
//                    claimDetail.setInsuType(npResponsibilities.get(0).getReponsibilitycodetwo());
//                    claimDetail.setDutyCode(npResponsibilities.get(0).getReponsibilitycodeone());
//
//                }
//            }
//            claimDetails[i] = claimDetail;
//        }
//        BigDecimal sumAmount = zfAmountModels.stream().map(ZfAmountModel::getCompensationAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
//        insertClaimEntryRequest.setDetails(JSON.toJSONString(claimDetails));
//        insertClaimEntryRequest.setClaimInsuName(scClaimdetail.getInsurancename());
//        insertClaimEntryRequest.setClaimCorpName(scClaimdetail.getInsurename());
//        insertClaimEntryRequest.setClaimTotalAmt(sumAmount.toString());
//        Date currentTime = new Date();
//        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
//        String dateString = formatter.format(currentTime);
//        insertClaimEntryRequest.setClaimCreateDate(dateString);
//        insertClaimEntryRequest.setClaimReferenceCode(scClaimdetail.getClaimnumber().toString());
//        if (scClaimdetail.getInsurancename() != null && !"工银安盛人寿保险有限公司".equals(scClaimdetail.getInsurancename())
//                && scClaimdetail.getOnlinetype() != null && "线上".equals(scClaimdetail.getOnlinetype())) {
//            insertClaimEntryRequest.setClaimCodeAPP(scClaimdetail.getCaseno());
//        }
//        if (claimDetails != null && claimDetails.length > 0) {
//
//            try {
//                String uuid = UUID.randomUUID().toString();
//                insertClaimEntryRequest.setClaimCode(uuid);
//                insertClaimEntryRequest.setClaimFrom(1);
//                if (scClaimdetail.getOnlinetype().equals("线上")) {
//                    insertClaimEntryRequest.setClaimFrom(2);
//                }
//
//                sc_claiminvoice claiminvoice = scClaiminvoiceMapper.selectOne(new QueryWrapper<sc_claiminvoice>()
//                        .eq("IsDelete", 0)
//                        .eq("ClaimNumber", scClaimdetail.getClaimnumber())
//                        .orderByAsc("LiveStartDate").last("limit 1"));
//
//                insertClaimEntryRequest.setClaimDate(claiminvoice.getLivestartdate());
//
//                tpa_commonlog inUplodeParm = new tpa_commonlog();
//                inUplodeParm.setMainKey(scClaimdetail.getClaimnumber().toString());
//                inUplodeParm.setObjectType("insertClaimEntry:调用直付理赔上传");
//                inUplodeParm.setRemark("Request:" + JSON.toJSONString(insertClaimEntryRequest));
//                inUplodeParm.setOperation("调调用直付理赔上传");
//                inUplodeParm.setCreateBy(UserContext.getUserName());
//                inUplodeParm.setCreateTime(new Date());
//                tpaCommonlogMapper.insert(inUplodeParm);
//
//                TpcClaimResponse tpcClaimResponse = tpcClaimClient.insertClaimEntry(insertClaimEntryRequest);
//
//                tpa_commonlog backUplodeParm = new tpa_commonlog();
//                backUplodeParm.setMainKey(scClaimdetail.getClaimnumber().toString());
//                backUplodeParm.setObjectType("insertClaimEntry:调用直付理赔上传结果");
//                backUplodeParm.setRemark("Result:" + JSON.toJSONString(tpcClaimResponse));
//                backUplodeParm.setOperation("调用直付理赔上传返回结果");
//                backUplodeParm.setCreateBy(UserContext.getUserName());
//                backUplodeParm.setCreateTime(new Date());
//                tpaCommonlogMapper.insert(backUplodeParm);
//
//                if (tpcClaimResponse.getErrorCode() != 0) {
//                    throw new Exception("直付系统返回-:" + tpcClaimResponse.getErrorMessage());
//                }
//                BigDecimal lockedAmt = new BigDecimal(0.00);
//                try {
//                    lockedAmt = new BigDecimal(tpcClaimResponse.getLockedAmt());
//                } catch (Exception e) {
//                    throw new Exception("理算失败,直付冻结额度为空");
//                }
//                if (sumAmount.compareTo(lockedAmt) != 0) {//a!=b
//                    throw new Exception("调用直付理赔上传错误,申请额度:" + sumAmount + ",实际冻结额度:" + tpcClaimResponse.getLockedAmt());
//                }
//            } catch (Exception e) {
//                tpa_commonlog parm = new tpa_commonlog();
//                parm.setMainKey(scClaimdetail.getClaimnumber().toString());
//                parm.setObjectType("调用直付理赔上传错误");
//                parm.setRemark(e.toString());
//                parm.setOperation("调用直付理赔上传错误");
//                parm.setCreateBy(UserContext.getUserName());
//                parm.setCreateTime(new Date());
//                tpaCommonlogMapper.insert(parm);
//                throw new Exception(e);
//            }
//        }
//    }


    /**
     * 解冻/冻结公账金额
     */
    public void freezePublicBalance(Claim claim, String name
            , String identityNo, Map<String, BigDecimal> gZAmountMap, Integer type) {
        String zfxtReturnInfo = "直付系统返回-";
        String typeName = type==2?"调用公账解冻接口":"调用公账冻结接口";

        //如果公账额度map为空就直接返回
        if (gZAmountMap.isEmpty()) {
            log.info(typeName+"成功");
            return;
        }

        for (String key : gZAmountMap.keySet()) {
            BigDecimal comPensationAmount = gZAmountMap.get(key);
            PooledAccountOperationRequest operationRequest = new PooledAccountOperationRequest();
            operationRequest.setSlipCode(key);
            operationRequest.setAmount(comPensationAmount);
            operationRequest.setType(type);
            operationRequest.setClaimCode(claim.getClaimNo());
            operationRequest.setName(name);
            operationRequest.setCertId(identityNo);
            operationRequest.setPcCode(claim.getBatchNo());
            operationRequest.setCaseNo(claim.getInsurerClaimNo());
            operationRequest.setRemark(type==2?"解冻结金额":"冻结金额");

            try {
                //调用直付接口
                ApiResult<String> freezeBalance ;
                if(type==1){
                    freezeBalance = pooledAccountFeignClient.freezeBalance(operationRequest);
                }else{
                    freezeBalance = pooledAccountFeignClient.thawBalance(operationRequest);
                }

                if(freezeBalance.getCode()!=0 && !freezeBalance.getMessage().contains("没有冻结，请冻结后在操作")){
                    log.info(zfxtReturnInfo+freezeBalance.getMessage());
                    return;
                }
            }catch (Exception e){
                log.info(typeName+"报错");
                return;
            }
        }
        log.info(typeName+"成功");
    }


    // 永诚个账、公账 冻结/解冻接口
    public String freezeAndThaw(Claim claim, Integer type, List<ClaimDetail> claimDetails) {

        return "成功";
//
//        //查询出该赔案的出险人和被保人
//        ClaimStakeholder outInsure = claimInfoService.getStakeHolder(claim.getId(), claim.getBizIdentityCode(), claim.getTenantId(), PersonTypeEnum.OUT_INSURE.getCode(), true).get(0);
//        ClaimStakeholder mainInsure = claimInfoService.getStakeHolder(claim.getId(), claim.getBizIdentityCode(), claim.getTenantId(), PersonTypeEnum.MAIN_INSURE.getCode(), true).get(0);
//
//        //查询出该赔案的发票列表
//        List<ClaimInvoice> claimInvoiceList = claimInfoService.getInvoiceList(claim.getId(), claim.getBizIdentityCode(), claim.getTenantId());
//
//        //查询直付个人信息
//        PeopleInfoResponse infoResponse;
//
//        QuotaInfoReq req = new QuotaInfoReq();
//        // 家属关系时接口被保险人和使用人信息互换
//        if (Objects.equals(claim.getRelationType(), 20)) {
//            req.setPersonName(mainInsure.getName());
//            req.setPersonType(mainInsure.getIdentityType());
//            req.setPersonNumber(mainInsure.getIdentityNo());
//            req.setUserName(outInsure.getName());
//            req.setUserType(outInsure.getIdentityType());
//            req.setUserNumber(outInsure.getIdentityNo());
//
//            infoResponse = directPaymentFeignClient.queryPersonInfoListByParam(mainInsure.getIdentityNo(), mainInsure.getName(), claim.getPolicyNo());
//        } else {
//            req.setPersonName(outInsure.getName());
//            req.setPersonType(outInsure.getIdentityType()); // todo 这里的可能要注意一下
//            req.setPersonNumber(outInsure.getIdentityNo());
//            req.setUserName(mainInsure.getName());
//            req.setUserType(mainInsure.getIdentityType());
//            req.setUserNumber(mainInsure.getIdentityNo());
//
//            infoResponse = directPaymentFeignClient.queryPersonInfoListByParam(outInsure.getIdentityNo(), outInsure.getName(), claim.getPolicyNo());
//        }
//
//        if(infoResponse.getData().get(0).getCardForwardType().equals("18") && type == 1){
//            log.info("赔案{}是永诚控额,tpa端不需要调用冻结接口", claim.getClaimNo());
//            //这里记一个日志
//
//            return "";
//        }
//
//        req.setChangType(type);
//        // 冻结/解冻
//        String typeStr = "解冻";
//        if(StringUtils.isEmpty(infoResponse.getData().get(0).getCardForwardType())) {
//            throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "个账、公账 " + typeStr + "接口执行失败,原因是处理类型为null");
//        }
//
//        //收集出住院时间
//        List<String> hospitalStartDateList = claimInvoiceList.stream().filter(t -> !org.apache.commons.lang.StringUtils.isEmpty(t.getHospitalPeriod()))
//                .map(t -> t.getHospitalPeriod().split(",")[0]).collect(Collectors.toList());
//        req.setUserTime(DateUtil.getFirstDayInPeriod(claim.getPolicyStartDate(), claim.getPolicyEndDate(), hospitalStartDateList));
//
//        req.setRelation(claim.getPolicyRelation());
//        req.setBusinessNo(claim.getClaimNo());
//        req.setReportNumber(claim.getInsurerClaimNo());
//        req.setRemark(infoResponse.getData().get(0).getCardForwardType());
//        req.setClaimDetails(claimDetails);
//
//        req.setRefundReason("02");
//
//        // 这是一个数修的锁
////        if(redisUtil.get("tpa_error_correction_"+claimdetail.getClaimnumber())!=null){
////            String efundStr = redisUtil.get("tpa_error_correction_"+claimdetail.getClaimnumber()).toString();
////            req.setRefundReason(efundStr.split("_")[0]);
////            req.setRefundReasonDesc(efundStr.split("_")[1]);
////            redisUtil.del("tpa_error_correction_"+claimdetail.getClaimnumber());
////        }
//        //18是永诚控额
//        if(infoResponse.getData().get(0).getCardForwardType().equals("18")){
////            List<claim_push_record> claimPushRecords =
////                    .selectList(new QueryWrapper<claim_push_record>()
////                            .eq("ClaimNumber", claimdetail.getClaimnumber())
////                            .eq("IsDeleted", 0));
////            if(CollectionUtil.isEmpty(claimPushRecords)
////                    ||!claimPushRecords.get(0).getErrorType().equals("保司")){
////                insertCommonLog(claimdetail.getClaimnumber(), "freezeAndThaw：个账、公账 冻结/解冻接口",
////                        "此赔案是永诚控额,tpa端不需要调用解冻接口", "freezeAndThaw：" + typeStr);
////                return new TwoTuple<>(Boolean.TRUE, typeStr + "成功");
////            }
//            if (!claim.getPkPushStatus().equals(PkPushStatusEnum.PUSH_SUCCESS.getName())) {
//                return "此赔案是永诚控额,tpa端不需要调用解冻接口";
//            }
//        }
//
//        try {
//            long startTime = System.currentTimeMillis();
//            log.info("operateBalance开始 {}", startTime);
//            ApiResult<String> freezeAndThawResponse = ycClaimFeignClient.operateBalance(req);
//            long endTime = System.currentTimeMillis();
//            log.info("operateBalance结束 {}, 耗时 {}", endTime, endTime - startTime);
//            if(endTime-startTime>(2*60*1000)) {
//                //超时发送钉钉群消息
//            }
//            if (freezeAndThawResponse.getCode() != 0) {
//                if(freezeAndThawResponse.getMessage().contains("Time-out") || freezeAndThawResponse.getMessage().contains("timed-out")){
//                    //超时发送钉钉群消息
//                }
//                throw new TpaBizException(BizErrorCode.OUTER_CLIENT_ERROR, "好管家个账、公账 " + typeStr + "失败（直付返回原因）：" + freezeAndThawResponse.getMessage());
//            }
//        } catch (Exception e) {
//            log.error("freezeAndThaw异常：", e);
//            throw new TpaBizException(BizErrorCode.OUTER_CLIENT_ERROR, "个账、公账 " + typeStr + "接口异常：" + e.getMessage());
//        }
//
//        return "";
    }


    public void deleteDirectPaymentAmount(Claim claim) {
//        BackClaimRequest backClaimRequest = new BackClaimRequest();
//        backClaimRequest.setClaimCode(claim.getClaimNo());
//        backClaimRequest.setBackCause("重新理算解冻直付金额");
//        try {
//            // log.info("调用理赔踢回 入参 " + JSON.toJSONString(backClaimRequest));
//            TpcClaimResponse tpcClaimResponse = tpcClaimClient.backClaim(backClaimRequest);
//
//            if (tpcClaimResponse.getErrorCode() != 0 &&
//                    tpcClaimResponse.getErrorCode() != 4) {
//                if(!"理赔申请单状态(10)有误".equals(tpcClaimResponse.getErrorMessage())) {
//                    return new TwoTuple<>(Boolean.FALSE, "直付系统返回-"+tpcClaimResponse.getErrorMessage());
//                }
//            }
//
//        } catch (Exception e) {
//            return new TwoTuple<>(Boolean.FALSE, "调用理赔踢回接口不通");
//        }
//        return new TwoTuple<>(Boolean.TRUE, "释放成功");
    }


    // 公账发票扣款信息
    public List<ClaimDetail> publicAccountClaimDetail(Map<String,BigDecimal> gZAmountMap) {
        List<ClaimDetail> claimDetails = new ArrayList<>();

        //如果公账额度map为空就直接返回
        if (gZAmountMap.isEmpty()) {
            return claimDetails;
        }

        for (String key : gZAmountMap.keySet()) {
            // 永诚公账扣款发票详情数据
            BigDecimal compensationAmount = gZAmountMap.get(key);
            // 为零就不调了
            if (compensationAmount.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            ClaimDetail claimDetail = new ClaimDetail();
            claimDetail.setSlipCode(key);
            claimDetail.setApplyAmt(compensationAmount);
            claimDetail.setAccountType("20");
            claimDetails.add(claimDetail);
        }

        return claimDetails;
    }
}
