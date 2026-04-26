package com.bone.tpa.push.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.alibaba.fastjson.JSON;
import com.aliyun.openservices.ons.api.Message;
import com.aliyun.openservices.ons.api.Producer;
import com.aliyun.openservices.ons.api.SendResult;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.bone.tpa.api.enums.SourceType;
import com.bone.tpa.push.bean.PushClaimContext;
import com.bone.tpa.push.config.RocketMQConfig;
import com.bone.tpa.push.convert.*;
import com.bone.tpa.push.dto.*;
import com.bone.tpa.push.enums.ClaimChannelEnum;
import com.bone.tpa.push.enums.PushTypeEnum;
import com.bone.tpa.push.feign.request.ClaimCancelRequest;
import com.bone.tpa.push.feign.response.CompanyInfoResponse;
import com.bone.tpa.push.service.ClaimDetailProcessor;
import com.bone.tpa.push.service.InsurancePushManager;
import com.bone.tpa.push.service.PushClaimAction;
import com.bone.tpa.push.util.CommonTool;
import com.bone.tpa.push.util.JsonValueUtils;
import com.bone.tpa.sdk.adjustment.model.AdjustmentRecord;
import com.bone.tpa.sdk.adjustment.model.AdjustmentResult;
import com.bone.tpa.sdk.claim.enums.InsurancePushStatusEnum;
import com.bone.tpa.sdk.claim.enums.OperationTypeEnum;
import com.bone.tpa.sdk.claim.enums.PkPushStatusEnum;
import com.bone.tpa.sdk.claim.model.*;
import com.bone.tpa.sdk.dao.ClaimRepository;
import com.bone.tpa.sdk.dao.ClaimTrackLogRepository;
import com.bone.tpa.sdk.masterdb.mapper.*;
import com.bone.tpa.sdk.masterdb.model.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.*;

import static com.bone.tpa.push.constants.CommonConstant.*;

/**
 * @Author feihaiming
 * @create 2025/10/16 17:50
 */
@Component
@Slf4j
public class PushClaimDBActionImpl extends PushClaimAction {

    @Autowired
    private TbPushImageMapper pushImageMapper;

    @Autowired
    private TbOverClaimConclusionExtendMapper conclusionExtendMapper;

    @Autowired
    private TbOverClaimMapper overClaimMapper;

    @Autowired
    private TbClaimMapper claimMapper;

    @Autowired
    private TbOverClaimDetailMapper claimDetailMapper;

    @Autowired
    private TbOverClaimConclusionMapper claimConclusionMapper;

    @Autowired
    private TbOverDutyMapper overDutyMapper;

    @Autowired
    private TbOverClaimExtendMapper tbOverClaimExtendMapper;

    @Autowired
    private TbOverClaimDetailExtendMapper detailExtendMapper;

    @Autowired
    private TbOverProjectMapper tbOverProjectMapper;

    @Autowired
    private TbOverDrugMapper tbOverDrugMapper;

    @Autowired
    private ClaimConclusionConverter claimConclusionConverter;

    @Autowired
    private ClaimPushConverter claimConverter;

    @Autowired
    private ClaimDetailConverter claimDetailConverter;

    @Autowired
    private ClaimDrugConverter claimDrugConverter;

    @Autowired
    private ClaimImageConverter claimImageConverter;

    @Autowired
    private ClaimProjectConverter claimProjectConverter;

    @Autowired
    private ClaimDetailProcessorFactory claimDetailProcessorFactory;

    @Autowired
    private ClaimRepository claimRepository;

    @Autowired
    private ClaimTrackLogRepository claimTrackLogRepository;

    @Autowired
    private TpaPersonClaimMapper tpaPersonClaimMapper;

    @Autowired
    private InsurancePushManager insurancePushManager;

    @Autowired
    private Producer producer;

    @Autowired
    private RocketMQConfig rocketMQConfig;

    @Override
    public boolean support(String pushType) {
        return PushTypeEnum.DB_PUSH.getCode().equals(pushType);
    }

    @Override
    public void afterCompletion(PushClaimRequestDTO pushClaimRequestDTO, PushClaimContext pushClaimContext) {
        MQMessageDTO messageDTO = MQMessageDTO.builder().claimCode(pushClaimContext.getClaim().getClaimNo()).build();
        Message msg = new Message(rocketMQConfig.getTopic(), rocketMQConfig.getYctag(), JSON.toJSONString(messageDTO).getBytes());
        msg.setKey(pushClaimContext.getClaim().getClaimNo());
        try {
            log.info("冻结消息开始发送：{}", JSON.toJSONString(messageDTO));
            SendResult sendResult = producer.send(msg);
            log.info("冻结消息发送成功：{},{}", pushClaimContext.getClaim().getClaimNo(), JSON.toJSONString(sendResult));
        } catch (Exception e) {
            log.error("冻结消息发送失败：{}", pushClaimContext.getClaim().getClaimNo(), e);
        }
    }

    @Override
    public void prePush(PushClaimRequestDTO pushClaimRequestDTO, PushClaimContext pushClaimContext) {

    }

    @Override
    public void postPush(PushClaimRequestDTO pushClaimRequestDTO, PushClaimContext pushClaimContext) {
        Claim claim = pushClaimContext.getClaim();
        SignRecord signRecord = pushClaimContext.getSignRecord();
        List<ClaimInvoice> invoiceList = pushClaimContext.getInvoiceList();
        List<AdjustmentRecord> adjustmentRecordList = pushClaimContext.getAdjustmentRecordList();
        CompanyInfoResponse companyInfoResponse = pushClaimContext.getCompanyInfoResponse();
        AdjustmentResult adjustmentResult = pushClaimContext.getAdjustmentResult();
        addTrackLog(claim);
        syncPersonClaimInfo(claim, signRecord, invoiceList, adjustmentRecordList, companyInfoResponse, adjustmentResult);
        checkIsManyPush(claim);
    }

    public void checkIsManyPush(Claim claim) {
        String claimNo = claim.getClaimNo();
        List<TbOverClaim> claimList = overClaimMapper
                .selectList(new QueryWrapper<TbOverClaim>()
                        .eq("claim_claim_code", claimNo)
                        .ne("claim_status", -1));
        Long dataCount = claimMapper.selectCount(new QueryWrapper<TbClaim>()
                .eq("tbclaim_case_code", claimNo).ne("tbclaim_status", -1));

        Long dataCount1 = tbOverClaimExtendMapper.selectCount(new QueryWrapper<TbOverClaimExtend>().eq("claim_code", claimNo));

        if ((claimList != null && claimList.size() > 1) || (dataCount != null && dataCount > 1) || (dataCount1 != null && dataCount1 > 1)) {
            try {
                ClaimCancelRequest claimCancelRequest = new ClaimCancelRequest();
                claimCancelRequest.setClaimNumbers(Arrays.asList(claimNo));
                insurancePushManager.claimCancel(claimCancelRequest);
                Claim curClaim = new Claim();
                curClaim.setId(claim.getId());
                curClaim.setPkPushStatus(PkPushStatusEnum.WAITING_PUSH.getName());
                claimRepository.update(curClaim);

                //有重复数据时手动作废TbClaim表的数据
                int updateCount = claimMapper.update(null, new UpdateWrapper<TbClaim>()
                        .eq("tbclaim_case_code", claimNo).ne("tbclaim_status", -1)
                        .set("tbclaim_status", -1));
                log.info("TbClaim表数据作废条数:{},赔案号:{}", updateCount, claimNo);

                //有重复数据时手动作废TbOverClaim_extend表的数据
                int deleteCount = tbOverClaimExtendMapper.delete(new QueryWrapper<TbOverClaimExtend>().eq("claim_code", claimNo));
                log.info("TbOverClaim_extend表数据作废条数:{},赔案号:{}", deleteCount, claimNo);
            } catch (Exception e) {
                log.error("推送masterdb数据重复删除时异常, 原因：{}", e.getMessage(), e);
            }
        }
    }


    public void syncPersonClaimInfo(Claim claim, SignRecord signRecord, List<ClaimInvoice> invoiceList, List<AdjustmentRecord> adjustmentRecordList, CompanyInfoResponse companyInfoResponse, AdjustmentResult adjustmentResult) {
        try {
            if (!SourceType.线上.getCode().equals(claim.getSource())) {
                return;
            }
            if (Objects.isNull(signRecord) || !ClaimChannelEnum.PKB.getName().equals(signRecord.getSignChannel())) {
                return;
            }
            String caseNoStr = claim.getInsurerClaimNo();
            if (StringUtils.isEmpty(caseNoStr)) {
                return;
            }
            List<TpaPersonClaim> tpaPersonClaimList = tpaPersonClaimMapper
                    .selectList(new QueryWrapper<TpaPersonClaim>()
                            .eq("pclaim_code", caseNoStr)
                            .lt("pclaim_status", 3));
            if (CollectionUtils.isEmpty(tpaPersonClaimList)) {
                log.warn("TpaPersonClaim没有查询到案件{}", claim.getClaimNo());
                return;
            }

            TpaPersonClaim personClaim = tpaPersonClaimList.get(0);

            BigDecimal payAmount = new BigDecimal(0);
            BigDecimal changeAmount = new BigDecimal(0);
            if (!CollectionUtils.isEmpty(adjustmentRecordList)) {

                payAmount = adjustmentRecordList.stream()
                        .map(AdjustmentRecord::getPayoutAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
            }
            BigDecimal totalPayAmount = payAmount.add(changeAmount);
            personClaim.setPclaimMemo(adjustmentResult.getResultDetail());
            personClaim.setPclaimActualamt(payAmount);
            personClaim.setPclaimTotalamt(invoiceList.stream()
                    .map(ClaimInvoice::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
            personClaim.setPclaimLockedamt(payAmount);
            personClaim.setPclaimDeductamt(payAmount);
            personClaim.setPclaimRatios(invoiceList.stream()
                    .map(ClaimInvoice::getTotalMedicalFundPayment).reduce(BigDecimal.ZERO, BigDecimal::add));
            String ratioPayoutList = adjustmentRecordList.get(0).getRatioPayoutList();
            BigDecimal firstRatio = JsonValueUtils.getFirstValue(ratioPayoutList);
            personClaim.setPclaimRuleClaimratio(firstRatio.divide(BigDecimal.valueOf(100)));//赔付比例
            personClaim.setPclaimInvoicenum(invoiceList.size());
            personClaim.setPclaimPersonageamt(personClaim.getPclaimTotalamt().subtract(totalPayAmount));//个人承担金额
            personClaim.setPclaimNotclaimamt(personClaim.getPclaimTotalamt()
                    .subtract(payAmount));
            if (Objects.nonNull(companyInfoResponse)) {
                personClaim.setPclaimTpaBranchCode(companyInfoResponse.getCompanyNo());
            }
            personClaim.setPclaimEndtime(claim.getReviewingPassTime());
            List<String> strings = adjustmentResult.getResultCode();
            String str = null;
            if (CollectionUtil.isNotEmpty(strings)) {
                str = StringUtils.equals(strings.get(0), "D") ? "拒赔" : "给付";
            }
            personClaim.setPclaimConclusion(str);
            personClaim.setPclaimStatus(3);
            tpaPersonClaimMapper.updateById(personClaim);

            CommonTool.insertTrackLog(claimTrackLogRepository, claim, null, "普康宝数据补偿", OperationTypeEnum.UPDATE, "");
        } catch (Exception ex) {
            log.error("syncPersonClaimInfo,Request:{},Error:", JSON.toJSONString(claim), ex);
        }
    }

    @Transactional(value = "saasTransactionManager", rollbackFor = Exception.class)
    public void addTrackLog(Claim claim) {
        Claim curClaim = new Claim();
        curClaim.setId(claim.getId());
        curClaim.setPkPushStatus(PkPushStatusEnum.PUSH_SUCCESS.getName());
        curClaim.setInsurancePushStatus(InsurancePushStatusEnum.WAITING_PUSH.getName());//工银未推送
        //story2 calculate claim age
        claimRepository.update(curClaim);

        CommonTool.insertTrackLog(claimTrackLogRepository, claim, curClaim, "赔案直付已推送,太保未推送", OperationTypeEnum.UPDATE, "");
    }

    @Override
    @Transactional(transactionManager = "masterdataTransactionManager", rollbackFor = Exception.class)
    public void pushData(PushClaimRequestDTO pushClaimRequestDTO, PushClaimContext pushClaimContext) {
        Claim claim = pushClaimContext.getClaim();
        String claimNumber = claim.getClaimNo();
        Boolean flag = false;
        try {
            List<TbOverClaim> over_claims = overClaimMapper
                    .selectList(new QueryWrapper<TbOverClaim>()
                            .eq("claim_claim_code", claimNumber)
                            .orderByDesc("claim_createTime"));
            if (over_claims != null && over_claims.size() > 0) {
                Integer status = over_claims.get(0).getClaimStatus();
                // claim_status>1,claim_push_record = 未推送 反更新  claim_push_record
                if (status >= 1) {
                    Claim curClaim = new Claim();
                    curClaim.setId(claim.getId());
                    curClaim.setPkPushStatus(PkPushStatusEnum.PUSH_SUCCESS.getName());
                    claimRepository.update(curClaim);
                    flag = false;
                }
                if (status == -1) {
                    TbOverClaim tempclaim = new TbOverClaim();
                    tempclaim.setClaimStatus(-1);
                    tempclaim.setClaimTpaStatus(3); //迁移到历史踢回里面
                    overClaimMapper.update(tempclaim, new QueryWrapper<TbOverClaim>().eq("claim_id", over_claims.get(0).getClaimId()));
                    flag = true;
                }
            } else {
                flag = true;
            }
            if (!flag) {
                log.warn("该赔案已推送  claimId：" + claimNumber);
                return;
            }

            Date currentDate = new Date();
            List<TbClaim> claims = claimMapper.selectList(new QueryWrapper<TbClaim>().eq("tbclaim_case_code", claimNumber).ne("tbclaim_status", -1));
            if (claims != null && claims.size() > 0) {
                TbClaim tempTbClaim = new TbClaim();
                tempTbClaim.setTbclaimStatus(-1);
                tempTbClaim.setTbclaimUpdatetime(currentDate);
                claimMapper.update(tempTbClaim, new QueryWrapper<TbClaim>().eq("tbclaim_case_code", claimNumber).ne("tbclaim_status", -1));
            }
            TbClaim tbClaim = claimConverter.convert2TbClaim(pushClaimRequestDTO.getClaimPushInfo().getClaim());
            tbClaim.setTbclaimCreatetime(currentDate);
            claimMapper.insert(tbClaim);

            TbOverClaim overClaim = claimConverter.convert2TbOverClaim(pushClaimRequestDTO.getClaimPushInfo().getClaim());
            overClaim.setClaimCreatetime(currentDate);
            overClaim.setClaimUpdatetime(currentDate);
            overClaimMapper.insert(overClaim);

            List<TbOverClaimExtend> claimExtends = tbOverClaimExtendMapper.selectList(new QueryWrapper<TbOverClaimExtend>().eq("claim_code", claimNumber));
            TbOverClaimExtend extend = claimConverter.convert2TbOverClaimExtend(pushClaimRequestDTO.getClaimPushInfo().getClaim());
            if (claimExtends != null && claimExtends.size() > 0) {
                tbOverClaimExtendMapper.update(extend, new QueryWrapper<TbOverClaimExtend>().eq("id", claimExtends.get(0).getId()));
            } else {
                extend.setClaimCode(claimNumber);
                tbOverClaimExtendMapper.insert(extend);
            }

            if (CollectionUtil.isNotEmpty(pushClaimRequestDTO.getClaimPushInfo().getImages())) {
                List<TbPushImage> pushImages = pushImageMapper.selectList(new QueryWrapper<TbPushImage>().eq("claim_code", claimNumber).ne("image_status", -1));
                if (pushImages != null && pushImages.size() > 0) {
                    TbPushImage tempoverclaim = new TbPushImage();
                    tempoverclaim.setImageStatus(-1);
                    tempoverclaim.setGmtUpdated(new Date());
                    pushImageMapper.update(tempoverclaim, new QueryWrapper<TbPushImage>().eq("claim_code", claimNumber));
                }
                for (ClaimImageDTO r : pushClaimRequestDTO.getClaimPushInfo().getImages()) {
                    TbPushImage pushImage = claimImageConverter.convert2TbPushImage(r);
                    pushImage.setIsDeleted(0);
                    pushImage.setGmtCreate(currentDate);
                    pushImageMapper.insert(pushImage);
                }
            }

            //多责任理算,需修改推送逻辑
            List<TbOverClaimDetail> overClaims = claimDetailMapper.selectList(new QueryWrapper<TbOverClaimDetail>().eq("claimd_claim_code", claimNumber).ne("claimd_status", -1));
            if (overClaims != null && overClaims.size() > 0) {
                TbOverClaimDetail tempoverclaim = new TbOverClaimDetail();
                tempoverclaim.setClaimdStatus(-1);
                tempoverclaim.setClaimdUpdatetime(currentDate);
                claimDetailMapper.update(tempoverclaim, new QueryWrapper<TbOverClaimDetail>().eq("claimd_claim_code", claimNumber));
            }
            List<TbOverClaimDetailExtend> oldOverClaimDetailExtends = detailExtendMapper.selectList(new QueryWrapper<TbOverClaimDetailExtend>().ne("bill_status", -1).eq("claim_code", claimNumber));
            if (!CollectionUtils.isEmpty(oldOverClaimDetailExtends)) {
                //作废发票扩展表数据
                TbOverClaimDetailExtend updateDetailExtend = new TbOverClaimDetailExtend();
                updateDetailExtend.setBillStatus((long) -1);
                updateDetailExtend.setUpdateTime(currentDate);
                detailExtendMapper.update(updateDetailExtend, new QueryWrapper<TbOverClaimDetailExtend>().ne("bill_status", -1).eq("claim_code", claimNumber));
            }
            TbOverProject project = new TbOverProject();
            project.setProjectStatus(-1);
            project.setProjectUpdateTime(currentDate);
            tbOverProjectMapper.update(project, new QueryWrapper<TbOverProject>().eq("project_claim_code", claimNumber));
            TbOverDrug drugitem = new TbOverDrug();
            drugitem.setDrugStatus(-1);
            drugitem.setDrugUpdatetime(currentDate);
            tbOverDrugMapper.update(drugitem, new QueryWrapper<TbOverDrug>().eq("drug_claim_code", claimNumber));

            List<TbOverClaimDetailExtend> detailExtends = new ArrayList<>();
            if (CollectionUtil.isNotEmpty(pushClaimRequestDTO.getClaimPushInfo().getDetails())) {
                for (ClaimDetailDTO r : pushClaimRequestDTO.getClaimPushInfo().getDetails()) {
                    TbOverClaimDetail claimDetail = claimDetailConverter.convert2TbClaimDetail(r);
                    claimDetail.setClaimdCreatetime(currentDate);
                    claimDetailMapper.insert(claimDetail);

                    if (CollectionUtil.isNotEmpty(r.getClaimDetailExtends())) {
                        for (ClaimDetailExtendDTO ext : r.getClaimDetailExtends()) {
                            TbOverClaimDetailExtend detailExtend = claimDetailConverter.convert2TbClaimDetailExtend(ext);
                            detailExtend.setClaimCode(r.getClaimCode());
                            detailExtend.setBillCode(r.getBillCode());
                            detailExtend.setCreatetime(currentDate);
                            detailExtend.setBillId(claimDetail.getClaimdId());
                            detailExtendMapper.insert(detailExtend);
                            detailExtends.add(detailExtend);
                        }
                    }

                    // 鼎和，瑞泰，中邮
                    if (CollectionUtil.isNotEmpty(r.getClaimProjects())) {
                        for (ClaimProjectDTO projectInfo : r.getClaimProjects()) {
                            TbOverProject tbOverProject = claimProjectConverter.convert2ClaimProject(projectInfo);
                            tbOverProject.setProjectClaimCode(r.getClaimCode());
                            tbOverProject.setBillId(claimDetail.getClaimdId());
                            tbOverProject.setProjectCreateTime(currentDate);
                            tbOverProjectMapper.insert(tbOverProject);
                        }
                    }

                    // 鼎和，瑞泰，中邮
                    if (CollectionUtil.isNotEmpty(r.getClaimDrugs())) {
                        for (ClaimDrugDTO drugInfo : r.getClaimDrugs()) {
                            TbOverDrug tbOverDrug = claimDrugConverter.convert2ClaimDrug(drugInfo);
                            tbOverDrug.setBillId(String.valueOf(claimDetail.getClaimdId()));
                            tbOverDrug.setDrugClaimCode(String.valueOf(claimNumber));
                            tbOverDrug.setDrugCreatetime(currentDate);
                            tbOverDrug.setDrugUpdatetime(currentDate);
                            tbOverDrugMapper.insert(tbOverDrug);
                        }
                    }
                }
            }

            //判断状态
            List<TbOverClaimConclusion> claim_conclusions = claimConclusionMapper.selectList(new QueryWrapper<TbOverClaimConclusion>()
                    .eq("conclusion_claim_code", claimNumber).ne("conclusion_status", -1));
            if (claim_conclusions != null && claim_conclusions.size() > 0) {
                TbOverClaimConclusion tempclaim_conclusion = new TbOverClaimConclusion();
                tempclaim_conclusion.setConclusionStatus(-1);
                tempclaim_conclusion.setConclusionUpdatetime(currentDate);
                claimConclusionMapper.update(tempclaim_conclusion, new QueryWrapper<TbOverClaimConclusion>()
                        .eq("conclusion_claim_code", claimNumber));
            }
            //作废之前TbOverDuty表中数据
            List<TbOverDuty> duties = overDutyMapper.selectList(new QueryWrapper<TbOverDuty>().
                    eq("duty_claim_code", claimNumber).ne("duty_status", -1));
            if (duties != null && duties.size() > 0) {
                TbOverDuty duty = new TbOverDuty();
                duty.setDutyStatus(-1);
                duty.setDutyUpdatetime(currentDate);
                overDutyMapper.update(duty, new QueryWrapper<TbOverDuty>().eq("duty_claim_code", claimNumber));
            }
            //先把数据作废掉
            List<TbOverClaimConclusionExtend> conclusion_extends = conclusionExtendMapper.selectList(new QueryWrapper<TbOverClaimConclusionExtend>().
                    eq("claim_code", claimNumber).eq("claim_status", 1).eq("deleted", 0));
            if (conclusion_extends != null && conclusion_extends.size() > 0) {
                TbOverClaimConclusionExtend conclusion_extend = new TbOverClaimConclusionExtend();
                conclusion_extend.setClaimStatus((byte) -1);
                conclusion_extend.setMemo("作废数据重新推送");
                conclusion_extend.setUpdateTime(currentDate);
                //写一下 备注原因
                conclusionExtendMapper.update(conclusion_extend, new QueryWrapper<TbOverClaimConclusionExtend>().
                        eq("claim_code", claimNumber).eq("claim_status", 1).eq("deleted", 0));
            }


            //按照责任分组的 发票数据
            if (CollectionUtil.isNotEmpty(pushClaimRequestDTO.getClaimPushInfo().getConclusions())) {
                for (ClaimConclusionDTO r : pushClaimRequestDTO.getClaimPushInfo().getConclusions()) {
                    TbOverClaimConclusion claimConclusion = claimConclusionConverter.convert2TbClaimConclusion(r);
                    claimConclusion.setConclusionCreatetime(currentDate);
                    claimConclusionMapper.insert(claimConclusion);

                    //写入TbOverClaimConclusionExtend
                    TbOverClaimConclusionExtend conclusionExtend = claimConclusionConverter.convert2TbClaimConclusionExtend(r);
                    conclusionExtend.setDeleted((byte) 0);
                    conclusionExtend.setConclusionId(claimConclusion.getConclusionId());
                    conclusionExtend.setCreateTime(currentDate);
                    conclusionExtend.setUpdateTime(currentDate);
                    conclusionExtendMapper.insert(conclusionExtend);

                    String finalDutyId = r.getRelatedObjectGuid();
                    String finalTreatmentType = r.getExtraFields().getOrDefault(CLAIM_CONCLUSION_TREATMENT_TYPE, "");
                    ClaimDetailProcessor processor = claimDetailProcessorFactory.getProcessor(pushClaimRequestDTO.getInsuranceName(), pushClaimRequestDTO.getBranchName());
                    List<TbOverClaimDetailExtend> tbOverClaimDetailExtends = processor.process(detailExtends, finalDutyId, finalTreatmentType);

                    for (TbOverClaimDetailExtend tbOverClaimDetailExtend : tbOverClaimDetailExtends) {
                        TbOverClaimDetailExtend updateDetailExtend = new TbOverClaimDetailExtend();
                        updateDetailExtend.setConclusionId(claimConclusion.getConclusionId());
                        updateDetailExtend.setId(tbOverClaimDetailExtend.getId());
                        detailExtendMapper.updateById(updateDetailExtend);
                    }

                    TbOverDuty overDuty = claimConclusionConverter.convert2ClaimDuty(r);
                    overDuty.setDutyCreatetime(currentDate);
                    overDutyMapper.insert(overDuty);
                }
            }
        } catch (Exception e) {
            log.error("推送数据异常 claimId：" + claimNumber);
            throw e;
        }
    }
}
