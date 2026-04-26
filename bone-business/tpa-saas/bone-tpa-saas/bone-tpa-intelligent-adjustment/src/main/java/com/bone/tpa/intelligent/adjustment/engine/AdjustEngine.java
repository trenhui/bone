package com.bone.tpa.intelligent.adjustment.engine;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bone.core.result.PageResult;
import com.bone.core.util.BizContextUtils;
import com.bone.core.util.DateParserUtil;
import com.bone.tpa.api.ApiResult;
import com.bone.tpa.api.enums.HangUpStatus;
import com.bone.tpa.facade.feign.DirectPaymentFeignClient;
import com.bone.tpa.facade.feign.PooledAccountFeignClient;
import com.bone.tpa.facade.feign.YcClaimFeignClient;
import com.bone.tpa.facade.request.ClaimDetail;
import com.bone.tpa.facade.request.QueryBalanceRequest;
import com.bone.tpa.facade.request.QuotaInfoReq;
import com.bone.tpa.facade.vo.PeopleInfoResponse;
import com.bone.tpa.facade.vo.PooledAccountBalance;
import com.bone.tpa.facade.vo.QueryBalanceResponse;
import com.bone.tpa.facade.vo.QueryPersonInfoV1;
import com.bone.tpa.intelligent.adjustment.converter.AdjustmentRecordConvert;
import com.bone.tpa.intelligent.adjustment.engine.constant.AdjustmentConstant;
import com.bone.tpa.intelligent.adjustment.engine.strategy.LiabilityAdjustmentStrategyFactory;
import com.bone.tpa.intelligent.adjustment.engine.strategy.ReimbursementLiabilityAdjustmentStrategy;
import com.bone.tpa.intelligent.adjustment.enums.*;
import com.bone.tpa.intelligent.adjustment.limit.LimitControlEngine;
import com.bone.tpa.intelligent.adjustment.limit.service.PersonalQuotaService;
import com.bone.tpa.intelligent.adjustment.model.PolicyInfoModel;
import com.bone.tpa.intelligent.adjustment.service.AdjustmentRecordService;
import com.bone.tpa.intelligent.adjustment.service.DirectQuotaService;
import com.bone.tpa.intelligent.adjustment.service.LiabilityInfoService;
import com.bone.tpa.intelligent.adjustment.service.PolicyService;
import com.bone.tpa.intelligent.adjustment.util.AdjustUtil;
import com.bone.tpa.intelligent.adjustment.util.StringUtil;
import com.bone.tpa.sdk.claim.enums.*;
import com.bone.tpa.sdk.service.LiabilityInfoBasicService;
import com.bone.tpa.sdk.util.DateUtil;
import com.bone.tpa.sdk.adjustment.api.AdjustmentEngine;
import com.bone.tpa.sdk.adjustment.enums.*;
import com.bone.tpa.sdk.adjustment.model.*;
import com.bone.tpa.sdk.adjustment.model.liability.*;
import com.bone.tpa.sdk.adjustment.request.ClaimAdjustmentRequest;
import com.bone.tpa.sdk.adjustment.response.ClaimAdjustmentResponse;
import com.bone.tpa.sdk.adjustment.response.InvoiceAdjustmentResponse;
import com.bone.tpa.sdk.claim.exception.TpaBizException;
import com.bone.tpa.sdk.claim.model.Claim;
import com.bone.tpa.sdk.claim.model.ClaimInvoice;
import com.bone.tpa.sdk.claim.model.ClaimStakeholder;
import com.bone.tpa.sdk.claim.model.InvoiceProjectItem;
import com.bone.tpa.sdk.service.AdjustmentRecordBasicService;
import com.bone.tpa.sdk.service.ClaimInfoService;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 理算入口，支持以下入参：
 *
 * 1. claimId
 * 2. 完整的claim、outInsure和invoice
 *
 *
 */

@Service
@Slf4j
public class AdjustEngine implements AdjustmentEngine {
    @Autowired
    private LiabilityAdjustmentStrategyFactory strategyFactory;

    @Autowired
    private LimitControlEngine limitControlEngine;

    @Autowired
    private ClaimInfoService claimInfoService;

    @Autowired
    private LiabilityInfoService liabilityInfoService;

    @Autowired
    private LiabilityInfoBasicService liabilityInfoBasicService;

    @Autowired
    private AdjustmentRecordService adjustmentRecordService;

    @Autowired
    private AdjustmentRecordBasicService adjustmentRecordBasicService;

    @Autowired
    private AdjustmentRecordConvert adjustmentRecordConvert;

    @Autowired
    private PersonalQuotaService personalQuotaService;

    @Autowired
    private PooledAccountFeignClient pooledAccountFeignClient;

    @Autowired
    private DirectPaymentFeignClient directPaymentFeignClient;

    @Autowired
    private DirectQuotaService directQuotaService;

    private final Logger logger = LoggerFactory.getLogger(ReimbursementLiabilityAdjustmentStrategy.class);


    @Override
    public ClaimAdjustmentResponse adjustClaim(Long claimId) {
        Claim claim = claimInfoService.getClaim(claimId);
        return adjustClaim(claim);
    }

    @Override
    public ClaimAdjustmentResponse adjustClaim(Claim claim) {
        if (BizContextUtils.getUser() == null) {
            throw new TpaBizException(BizErrorCode.UNAUTHORIZED);
        }

        //做一个后门，admin可以绕过用户相关的判定
        if (!BizContextUtils.getUser().equals("admin")) {
            if (claim.getOperatorUserName() == null || claim.getOperatorUserName().isBlank()) {
                throw new TpaBizException(BizErrorCode.FORBIDDEN, "赔案" + claim.getClaimNo() + "操作人员未分配");
            }

            if (!claim.getOperatorUserName().equals(BizContextUtils.getUser())) {
                throw new TpaBizException(BizErrorCode.FORBIDDEN, "赔案" + claim.getClaimNo() + "分配用户为" + claim.getOperatorUserName() + ", 当前登录用户为" + BizContextUtils.getUser());
            }
        }

        //不能是已经挂起的
        if (HangUpStatus.HANG_UP.getCode().equals(claim.getHangUpStatus())) {
            throw new TpaBizException(BizErrorCode.CLAIM_HANG_UP, claim.getClaimNo());
        }

        //保单
        Policy policy = liabilityInfoService.getPolicy(claim.getPolicyNo());

        if (!policy.getConfigStatus().equals(PolicyConfigStatusEnum.ACTIVE.getCode())) {
            throw new TpaBizException(BizErrorCode.ADJUST_NOT_ALLOWED, "保单" + claim.getPolicyNo() + "不处于启用状态！");
        }

        List<ClaimInvoice> invoiceList = claimInfoService.getInvoiceList(claim.getId(), claim.getBizIdentityCode(), claim.getTenantId());
        ClaimStakeholder outInsure = claimInfoService.getStakeHolder(claim.getId(), claim.getBizIdentityCode(), claim.getTenantId(), PersonTypeEnum.OUT_INSURE.getCode(), true).get(0);

        //从这个入口进去的理算，将要对发票排序
        //就诊日期升序排列，就诊日期相同时按发票id
        List<ClaimInvoice> sortedInvoiceList = invoiceList.stream()
                .sorted(Comparator.comparing(ClaimInvoice::getVisitDate,
                                Comparator.nullsFirst(Date::compareTo))
                        .thenComparing(ClaimInvoice::getId))
                .collect(Collectors.toList());

        return adjust(claim, outInsure, sortedInvoiceList, null);
    }

//    @Override
//    public ClaimAdjustmentResponse adjustClaim(ClaimAdjustmentRequest request) {
//        return adjust(request.getClaim(), request.getStakeholder(), request.getClaimInvoices(), request.getLiabilities());
//    }

    @Override
    public ClaimAdjustmentResponse readjustClaim(Long claimId) {
        clearClaimAdjustment(claimId);
        return adjustClaim(claimId);
    }

//    @Override
//    public ClaimAdjustmentResponse readjustClaim(ClaimAdjustmentRequest request) {
//        clearClaimAdjustment(request.getClaimId());
//        return adjustClaim(request);
//    }

    @Override
    public void clearClaimAdjustment(Long claimId) {
        Claim claim = claimInfoService.getClaim(claimId);
        ClaimStakeholder outInsure = claimInfoService.getStakeHolder(claim.getId(), claim.getBizIdentityCode(), claim.getTenantId(), PersonTypeEnum.OUT_INSURE.getCode(), true).get(0);

        //确认理算记录的状态，然后将其删除
        List<AdjustmentResult> adjustmentResultList = adjustmentRecordBasicService.getAdjustmentResultByClaim(claimId, true);

        if (adjustmentResultList == null || adjustmentResultList.isEmpty()) {
            return;
        }

        List<AdjustmentRecord> adjustmentRecordList = adjustmentRecordBasicService.getAllAdjustmentRecordByClaim(claimId);

        //已经确认的情况不能够清除理算记录
        if (QuotaStatusEnum.CONFIRMED.getCode().equals(adjustmentResultList.get(0).getResultStatus())) {
            throw new TpaBizException(BizErrorCode.STATUS_ERROR, "理算结果已确认！");
        }

        //其他情况就可以删除了
        logger.debug("开始清除理算结果，赔案id：{}", claimId);

        //遍历每一条理算记录
        List<Long> recordIdList = adjustmentRecordList.stream().map(AdjustmentRecord::getId).toList();
        List<Long> resultIdList = adjustmentResultList.stream().map(AdjustmentResult::getId).toList();


        //更新个人控额
        PersonalQuota personalQuota = personalQuotaService.getPersonalQuota(claim.getPolicyNo(), outInsure.getIdentityTypeCn(), outInsure.getIdentityNo());

        personalQuotaService.calculatePersonalQuota(personalQuota, adjustmentRecordList, PersonalQuotaCalculateEnum.UNFREEZE);

        adjustmentRecordService.clearAdjustmentResult(recordIdList, resultIdList);

        //外部解冻
        releaseDirectPayAmount(claim);
        logger.debug("清除理算结果完成，赔案id：{}", claimId);
    }

    /**
     * 理算核心方法 触发理算
     *
     * @return
     */
    private ClaimAdjustmentResponse adjust(Claim claim, ClaimStakeholder outInsure, List<ClaimInvoice> invoiceList, List<Liability> liabilityList) {

        /**
         * 赔案上的保单有效期
         */
        Date policyStartDate = DateParserUtil.parseDate(claim.getPolicyStartDate());
        Date policyEndDate = DateParserUtil.parseDate(claim.getPolicyEndDate());

        /**
         * 初始数据准备
         */
        Plan plan = liabilityInfoBasicService.getPlan(claim.getPlanUuid());

        List<Coverage> coverageList = liabilityInfoService.getCoverageList(plan.getId(), plan.getVersion());

        //coverageId, coverage
        Map<Long, Coverage> coverageMap = coverageList.stream().collect(Collectors.toMap(Coverage::getId, t -> t));

        List<LiabilityConfig> liabilityConfigList;
        if (liabilityList == null) {
            liabilityConfigList = liabilityInfoService.getLiabilityList(plan.getId(), plan.getVersion());
        } else {
            liabilityConfigList = liabilityInfoService.tranferToConfigList(liabilityList);
        }

        //Uuid, liability
        Map<String, LiabilityConfig> liabilityUuidMap = liabilityConfigList.stream().collect(Collectors.toMap(LiabilityConfig::getUuid, t -> t));

        //invoiceId, liabilityUuidList
        Map<Long, List<String>> invoiceLiabilityMap = new HashMap<>();

        //遍历发票，整理所有关联的责任种类
        for (ClaimInvoice invoice : invoiceList) {
            String liabilityString = invoice.getRelateLiability();

            if (liabilityString == null || liabilityString.isEmpty()) {
                throw new TpaBizException(BizErrorCode.ADJUST_NOT_ALLOWED, "发票" + invoice.getInvoiceNo() + "未绑定责任！");
            }

            //关联责任的字符串，格式为
            //责任uuid
            String[] liabilityUuidList = liabilityString.split(",");
            List<String> relatedLiabilityUuid = new ArrayList<>(Arrays.asList(liabilityUuidList));

            invoiceLiabilityMap.put(invoice.getId(), relatedLiabilityUuid);
        }

        List<LiabilitySharing> sharingList = liabilityInfoService.getSharingList(plan.getId(), plan.getVersion());

        //shareCode, liabilitySharing
        Map<String, LiabilitySharing> sharingMap = sharingList.stream().collect(Collectors.toMap(LiabilitySharing::getShareCode, t -> t));

        List<LiabilitySharingRelation> sharingRelationList = liabilityInfoService.getSharingRelationList(plan.getId(), plan.getVersion());

        //liabilityUuid, List<shareId>
        Map<String, List<String>> sharingRelationMap = sharingRelationList.stream().collect(Collectors.groupingBy(LiabilitySharingRelation::getLiabilityUuid, Collectors.mapping(LiabilitySharingRelation::getShareCode, Collectors.toList())));

        //从库里面捞取的历史理算记录
        List<AdjustmentRecord> historyAdjustmentRecordList = adjustmentRecordService.getAdjustmentRecordByPlan(plan.getUuid(), null, true, outInsure);

        //liabilityUuid, List<AdjustmentRecord> 理算过程中之前算好的记录，但是还没入库
        Map<String, List<AdjustmentRecord>> ongoingAdjustmentRecordMap = new HashMap<>();

        //保单，用来判断一些时间区间的
        Policy policy = liabilityInfoService.getPolicy(claim.getPolicyNo());

        //最后用于插入数据库的list
        List<AdjustmentRecord> adjustmentRecordList = new ArrayList<>();

        //涉及到的个人控额
        //这里的个人控额只使用个人控额初始值，实际使用时用初始值减去历史记录的汇总值计算额度。
        PersonalQuota personalQuota = personalQuotaService.getPersonalQuota(policy.getPolicyNo(), outInsure.getIdentityTypeCn(), outInsure.getIdentityNo());

        //直付公账map
        Map<String, BigDecimal> publicAccountMap = new HashMap<>();
        Map<String, BigDecimal> publicAccountPayMap = new HashMap<>();

        //直付个账
        BigDecimal personalAccount = null;
        BigDecimal personalAccountPay = null;

        /**
         * 开始理算
         */

        List<InvoiceAdjustmentResponse> invoiceResponseList = new ArrayList<>();

        //开始遍历发票
        for (ClaimInvoice invoice : invoiceList) {
            List<String> uuidList = invoiceLiabilityMap.get(invoice.getId());
            if (uuidList == null || uuidList.isEmpty()) {
                throw new TpaBizException(BizErrorCode.ADJUST_NOT_ALLOWED, "发票" + invoice.getInvoiceNo() + "未绑定责任！");
            }

            InvoiceAdjustmentContext context = InvoiceAdjustmentContext.builder()
                    .policy(policy)
                    .policyStartDate(policyStartDate)
                    .policyEndDate(policyEndDate)
                    .claim(claim)
                    .invoice(invoice)
                    .outInsure(outInsure)
                    .plan(plan)
                    .allLiability(liabilityConfigList)
                    .sharingRelationList(sharingRelationList)
                    .liabilityFeeMap(new HashMap<>())
                    .liabilityFeeOccupyDifferenceMap(new HashMap<>())
                    .personalQuota(personalQuota)
                    .build();


            //按顺序遍历责任
            for (String liabilityUuid : uuidList) {
                LiabilityConfig liability = liabilityUuidMap.get(liabilityUuid);
                if (liability == null) {
                    log.warn("责任名称 {} 并不存在。保单号 {}", liabilityUuid, context.getPolicy().getPolicyNo());
                    continue;
                }
                String uuid = liability.getUuid();

                //这个方案是为了后付责任考虑。第一次循环的时候是直接责任，然后只要存在后付责任就会一直走这个循环
                while (uuid != null) {
                    liability = liabilityUuidMap.get(uuid);
                    if (liability == null) {
                        log.warn("责任uuid {} 并不存在。保单号 {}", uuid, context.getPolicy().getPolicyNo());
                        break;
                    }

                    List<LiabilitySharing> relatedSharingList = new ArrayList<>();
                    List<String> relationCodeList = sharingRelationMap.get(liability.getUuid());
                    if (relationCodeList != null) {
                        relatedSharingList = relationCodeList.stream().map(sharingMap::get).collect(Collectors.toList());
                    }

                    //补充责任相关内容到上下文中
                    context.setLiabilityPayAmount(BigDecimal.ZERO);
                    context.setLiabilityDeductAmount(BigDecimal.ZERO);
                    context.setLiability(liability);
                    context.setCoverage(coverageMap.get(liability.getCoverageId()));
                    context.setSharingList(relatedSharingList);
                    context.setQuotaMap(new HashMap<>());

//                    context.setLiabilityFeeOccupyDifferenceMap(new HashMap<>());
                    context.setFeeTypeList(new ArrayList<>());
                    context.setRemainingLiabilityDeductLimit(BigDecimal.ZERO);
                    context.setDeductRatio(null);
                    context.setCurrentDeductAmount(null);
                    context.setHistoryAdjustmentRecord(historyAdjustmentRecordList);

                    context.setNextLiabilityFlag(false);

                    //补充直付额度相关
                    context.setPublicAccountMap(publicAccountMap);
                    context.setPublicAccountPayMap(publicAccountPayMap);
                    context.setPersonalAccount(personalAccount);
                    context.setPersonalAccountPay(personalAccountPay);

                    //处理显示在理算记录中的责任名称
                    if (context.getFormerName() != null) {
                        context.setRecordName(String.format(AdjustmentConstant.NAME_BUILDER, context.getFormerName(), liability.getLiabilityName()));
                    } else {
                        context.setRecordName(liability.getLiabilityName());
                    }

                    //理算
                    AdjustmentRecord record = adjustOneLiability(context);
                    adjustmentRecordList.add(record);

                    //记录在途理算记录
                    if (record != null) {
                        //插入到返回结果中
                        InvoiceAdjustmentResponse invoiceResponse = adjustmentRecordConvert.toDto(record);

                        invoiceResponseList.add(invoiceResponse);

                        if (record.getRecordStatus().equals(QuotaStatusEnum.FROZEN.getCode())) {

                            //记录到历史理算记录中
                            historyAdjustmentRecordList.add(record);
                            log.info("责任{}的理算记录插入到历史中", liability.getLiabilityName());

                            //插入到在途的理算记录
                            if (ongoingAdjustmentRecordMap.containsKey(uuid)) {
                                ongoingAdjustmentRecordMap.get(uuid).add(record);
                            } else {
                                List<AdjustmentRecord> recordList = new ArrayList<>();
                                recordList.add(record);
                                ongoingAdjustmentRecordMap.put(uuid, recordList);
                            }


                            //结算直付公账个账的扣额
                            if (liability.getQuotaController().getType().equals(QuotaControlSourceEnum.DIRECT.getCode())) {
                                if (liability.getAccountType().equals(AccountTypeEnum.PUBLIC.getCode())) {
                                    if (publicAccountPayMap.containsKey(liability.getQuotaController().getPolicyNo())) {
                                        publicAccountPayMap.replace(liability.getQuotaController().getPolicyNo(), publicAccountMap.get(liability.getQuotaController().getPolicyNo()).add(record.getPayoutAmount()));
                                    } else {
                                        publicAccountMap.put(liability.getQuotaController().getPolicyNo(), context.getDirectAmount());
                                        publicAccountPayMap.put(liability.getQuotaController().getPolicyNo(), record.getPayoutAmount());
                                    }
                                }
                                if (liability.getAccountType().equals(AccountTypeEnum.PERSONAL.getCode())) {
                                    if (personalAccountPay != null) {
                                        personalAccountPay = personalAccountPay.add(record.getPayoutAmount());
                                    } else {
                                        personalAccount = context.getDirectAmount();
                                        personalAccountPay = record.getPayoutAmount();
                                    }
                                }
                            }
                        }
                    }

                    //判定这个责任是否将责任额度用完了（这里判断责任额度、个人专属额度、直付个人额度）
                    Map<String, BigDecimal> quotaMap = new HashMap<>();
                    if (context.getQuotaMap().containsKey(QuotaControllerTypeEnum.LIABILITY.getCode())) {
                        quotaMap.put(QuotaControllerTypeEnum.LIABILITY.getCode(), context.getQuotaMap().get(QuotaControllerTypeEnum.LIABILITY.getCode()));
                    }
                    if (context.getQuotaMap().containsKey(QuotaControllerTypeEnum.PERSONAL.getCode())) {
                        quotaMap.put(QuotaControllerTypeEnum.PERSONAL.getCode(), context.getQuotaMap().get(QuotaControllerTypeEnum.PERSONAL.getCode()));
                    }
                    if (context.getQuotaMap().containsKey(QuotaControllerTypeEnum.DIRECT_PERSONAL.getCode())) {
                        quotaMap.put(QuotaControllerTypeEnum.DIRECT_PERSONAL.getCode(), context.getQuotaMap().get(QuotaControllerTypeEnum.DIRECT_PERSONAL.getCode()));
                    }

                    //定额这里是有可能为空的
                    if (quotaMap.isEmpty()) {
                        uuid = null;
                        context.setFormerName(null);
                        context.setLiabilityFeeOccupyDifferenceMap(new HashMap<>());
                    } else {
                        BigDecimal minAmount = quotaMap.values().stream()
                                .min(BigDecimal::compareTo)
                                .orElse(BigDecimal.ZERO);

                        //其实这里不可能大于
                        if (record.getPayoutAmount().compareTo(minAmount) >= 0 || context.getNextLiabilityFlag()) {
                            log.info("责任 {} 的责任额度已用完: {}, {}", liability.getLiabilityName(), minAmount, record.getPayoutAmount());
                            //取得后付责任的uuid, 并根据后付责任的类型判定是否要结算后付责任
                            if (NextLiabilityTypeEnum.PRIOR_ZERO_PAY.getCode().equals(liability.getNextLiabilityType())) {
                                //如果是这种情况，需要判定该次理算结果是否为0赔付额
                                if (record.getPayoutAmount().compareTo(BigDecimal.ZERO) == 0) {
                                    uuid = liability.getNextLiabilityUuid();
                                    context.setFormerName(liability.getLiabilityName());
                                    context.setLiabilityFeeOccupyDifferenceMap(new HashMap<>());
                                } else {
                                    uuid = null;
                                    context.setFormerName(null);
                                    context.setLiabilityFeeOccupyDifferenceMap(new HashMap<>());
                                }
                            } else if (NextLiabilityTypeEnum.EXCEEDING_PAY.getCode().equals(liability.getNextLiabilityType())) {
                                //如果是这种情况，直接理算就好了
                                uuid = liability.getNextLiabilityUuid();
                                context.setFormerName(liability.getLiabilityName());
                                context.setLiabilityFeeOccupyDifferenceMap(new HashMap<>());
                            } else if (NextLiabilityTypeEnum.EXCEEDING_NO_PAY.getCode().equals(liability.getNextLiabilityType())) {
                                //如果是这种情况，需要传一个flag过去，让下一个赔案计算时是占据额度
                                uuid = liability.getNextLiabilityUuid();
                                context.setFormerName(liability.getLiabilityName());
//                        context.setLiabilityFeeOccupyDifferenceMap(context.getLiabilityFeeOccupyDifferenceMap());
                            } else {
                                uuid = null;
                                context.setFormerName(null);
                                context.setLiabilityFeeOccupyDifferenceMap(new HashMap<>());
                            }
                        } else {
                            uuid = null;
                            context.setFormerName(null);
                            context.setLiabilityFeeOccupyDifferenceMap(new HashMap<>());
                        }
                    }
                }
            }
        }

        //将理算记录插入库中
        adjustmentRecordService.insertRecordList(adjustmentRecordList);

        AdjustmentResult adjustmentResult = adjustmentRecordService.insertAndBuildResult(ongoingAdjustmentRecordMap, liabilityUuidMap, claim);

        //根据个人控额相关内容，更新个人控额
        personalQuotaService.calculatePersonalQuota(personalQuota, historyAdjustmentRecordList, PersonalQuotaCalculateEnum.FREEZE);

        // 6. 发送理算结果通知
        //sendResultNotification(claimAdjudicationRequest, claimAdjustmentResponse);

        //检查直付额度是否有变化
        checkDirectAmount(publicAccountMap, personalAccount, outInsure.getIdentityNo(), outInsure.getName(), policy.getPolicyNo());

        //进行直付控额


        /**
         * 永诚：冻结交由直付调用，只有特殊情况会执行解冻
         */

        //构造返回结果
        List<AdjustmentRecord> returnRecord = adjustmentRecordBasicService.getAdjustmentRecordByClaim(claim.getId(), true);
        List<InvoiceAdjustmentResponse> responseList = new ArrayList<>();
        for (AdjustmentRecord adjustmentRecord : returnRecord) {
            InvoiceAdjustmentResponse response = adjustmentRecordConvert.toDto(adjustmentRecord);
            responseList.add(response);
        }

        ClaimAdjustmentResponse claimResponse = ClaimAdjustmentResponse.builder()
                .relatedId(claim.getId())
                .invoiceResults(responseList)
                .payoutAmount(adjustmentResult.getPayoutAmount())
                .individualAmount(adjustmentResult.getIndividualAmount())
                .publicAmount(adjustmentResult.getPublicAmount())
                .result(adjustmentResult.getResult())
                .resultDetail(adjustmentResult.getResultDetail())
                .build();

        return claimResponse;
    }


    /**
     * 进行一个责任的理算
     */
    private AdjustmentRecord adjustOneLiability(InvoiceAdjustmentContext context) {
        try {
            //1. 检查该责任是否能够理算
            checkAdjustable(context);

            //2. 检查限额。取出全部相关的限额并检查是不是有某一项已经达到上限，做快速失败。把这些泛用限额直接放入上下文
            BigDecimal remainLimit = limitControlEngine.limitControl(context);
            context.setLiabilityLimit(remainLimit);

            //3. 正式开始理算
            AdjustmentRecord adjustmentRecord = strategyFactory.getLiabilityAdjudicationStrategy(context.getLiability().getLiabilityType()).adjustmentInvoice(context);


            //4. 后置检查
            checkAdjustRecord(context, adjustmentRecord);

            return adjustmentRecord;

        } catch (TpaBizException ex) {
            if (ex.getErrorCode().equals(BizErrorCode.ADJUST_NOT_ALLOWED)) {
                AdjustmentRecord adjustmentRecord = adjustmentRecordService.failRecordBuilder(context, ex.getMessage());

                return adjustmentRecord;
            }
            AdjustmentRecord adjustmentRecord = adjustmentRecordService.failRecordBuilder(context, ex.getErrorCode() + " : " + ex.getMessage());

            return adjustmentRecord;
        } catch (Exception e) {
            log.error("[理算发生未知异常]", e);

            AdjustmentRecord adjustmentRecord = adjustmentRecordService.failRecordBuilder(context, "发生未知异常，清通知技术人员");

            return adjustmentRecord;
        }
    }


    /**
     * 检查是否能够理算的核心方法
     */
    private void checkAdjustable(InvoiceAdjustmentContext context) {
        LiabilityConfig liability = context.getLiability();
        ClaimInvoice invoice = context.getInvoice();
        ClaimStakeholder outInsure = context.getOutInsure();

        //0.0 没有就诊日期
        if (invoice.getVisitDate() == null) {
            log.error("发票缺失就诊日期: " + invoice.getInvoiceNo());
            throw new TpaBizException(BizErrorCode.ADJUST_NOT_ALLOWED, "发票缺失就诊日期");
        }

        //1.0 检查责任类型的特殊条件
        strategyFactory.getLiabilityAdjudicationStrategy(liability.getLiabilityType()).checkAdjustable(context);

        //1.0 检查发票的诊断时间是否在保单区间内
        if (!liability.getLiabilityType().equals(LiabilityTypeEnum.ALLOWANCE)) {
            if (!DateUtil.isInPeriod(context.getPolicyStartDate(), context.getPolicyEndDate(), invoice.getVisitDate())) {
                log.error("发票就诊日期在保单有效期之外: " + invoice.getVisitDate());
                throw new TpaBizException(BizErrorCode.ADJUST_NOT_ALLOWED, AdjustmentDetailFailReasonEnum.ONE.getDesc());
            }
        }

        //1.0 检查发票的姓名是否和赔案的出险人相同
        if (!invoice.getInvoiceName().equals(outInsure.getName())) {
            log.error("发票的姓名 " + invoice.getInvoiceName() + " 与出险人 " + outInsure.getName()+ " 不一致");
            throw new TpaBizException(BizErrorCode.ADJUST_NOT_ALLOWED, AdjustmentDetailFailReasonEnum.TWO.getDesc());
        }

        //1.1 检查适用对象
        RestrictObject restrictObject = liability.getRestrictObject();
        if (restrictObject != null) {
            //出险人性别
            if (restrictObject.getGender() != null) {
                if (!GenderEnum.getEnumByDesc(restrictObject.getGender()).getCode().equals(outInsure.getGender())) {
                    throw new TpaBizException(BizErrorCode.ADJUST_NOT_ALLOWED, "出险人性别错误: 应为" + restrictObject.getGender());
                }
            }

            //出险人年龄
            if (restrictObject.getAge() != null) {
                Integer age = AdjustUtil.calculateAge(outInsure.getIdentityNo());
                RangeTypeEnum rangeType = RangeTypeEnum.getByCode(restrictObject.getAge().getIntervalType());
                if (rangeType == null) {
                    throw new TpaBizException(BizErrorCode.ADJUST_NOT_ALLOWED, "年龄区间类型错误: " + restrictObject.getAge().getIntervalType() + "并非可行区间类型");
                }
                if (!rangeType.isValueInRange(age, restrictObject.getAge().getLowerLimit(), restrictObject.getAge().getUpperLimit())) {
                    throw new TpaBizException(BizErrorCode.ADJUST_NOT_ALLOWED, "出险人年龄错误: " + age + "岁不在可行区间内");
                }
            }

            //出险人职业 todo 暂时不需要
            if (restrictObject.getOccupation() != null) {
                if (!restrictObject.getOccupation().contains(outInsure.getOccupation())) {
                    throw new TpaBizException(BizErrorCode.ADJUST_NOT_ALLOWED, "出险人职业错误: 应为" + restrictObject.getOccupation());
                }
            }
        }

        //1.2 检查适用限定 todo 好像还是不需要
        List<RestrictScope> restrictScope = liability.getRestrictScope();

        if (restrictScope != null) {
            for (RestrictScope scope : restrictScope) {
                if (scope.getOpen()) {
                    if (scope.getRestrictRange().equals(RestrictRangeEnum.DRUG.getCode()) || scope.getRestrictRange().equals(RestrictRangeEnum.DIAGNOSE.getCode())) {
                        //如果是药品诊疗相关，要去获取费用项目细则
                        List<InvoiceProjectItem> invoiceProjectItemList = claimInfoService.getItemList(invoice.getId(), invoice.getBizIdentityCode(), invoice.getTenantId());

                        List<String> itemNameList = invoiceProjectItemList.stream().map(InvoiceProjectItem::getItemName).toList();

                        //检查是否符合黑白名单
                        boolean result = AdjustUtil.checkElementSection(itemNameList, scope.getRestrictList(), scope.getType().equals(ControlTypeEnum.WHITE.getCode()));

                        if (!result) {
                            throw new TpaBizException(BizErrorCode.ADJUST_NOT_ALLOWED, scope.getRestrictRange() + "错误: 应为" + scope.getRestrictList());
                        }
                    } else if (scope.getRestrictRange().equals(RestrictRangeEnum.HOSPITAL.getCode())) {
                        //如果是医院限定，则看发票的医院名称
                        String hospitalName = invoice.getHospitalName();

                        boolean containFlag = scope.getRestrictList().contains(hospitalName);

                        //检测是不是true对应白名单，false对应黑名单
                        if (containFlag != scope.getType().equals(ControlTypeEnum.WHITE.getCode())) {
                            throw new TpaBizException(BizErrorCode.ADJUST_NOT_ALLOWED, scope.getRestrictRange() + "错误: 应为" + scope.getRestrictList());
                        }
                    } else if (scope.getRestrictRange().equals(RestrictRangeEnum.DISEASE.getCode())) {
                        //如果是疾病限定，则看发票的诊断
                        String disease = invoice.getDiagnosis();

                        boolean containFlag = scope.getRestrictList().contains(disease);

                        //检测是不是true对应白名单，false对应黑名单
                        if (containFlag != scope.getType().equals(ControlTypeEnum.WHITE.getCode())) {
                            throw new TpaBizException(BizErrorCode.ADJUST_NOT_ALLOWED, scope.getRestrictRange() + "错误: 应为" + scope.getRestrictList());
                        }
                    } else {
                        throw new TpaBizException(BizErrorCode.INNER_PARAMETER_ERROR, "适用限定范围错误！" + scope.getRestrictRange());
                    }
                }
            }
        }

        //1.4 检查等待期
        if (liability.getWaitingPeriod() != null && liability.getWaitingPeriod() > 0) {
            long period = AdjustUtil.calculateDateDifference(context.getPolicyStartDate(), context.getPolicyEndDate(), TimeUnit.DAYS);
            if (period < liability.getWaitingPeriod()) {
                log.error("发票" + invoice.getInvoiceNo() + "尚在等待期中");
                throw new TpaBizException(BizErrorCode.INNER_PARAMETER_ERROR, AdjustmentDetailFailReasonEnum.TWELVE.getDesc());
            }
        }
    }


    /**
     * 后置检查
     */
    private void checkAdjustRecord(InvoiceAdjustmentContext context, AdjustmentRecord adjustmentRecord) {
        Map<String, BigDecimal> quotaMap = StringUtil.rowsToMap(adjustmentRecord.getQuotaDetail());
        Map<String, BigDecimal> feePayoutMap = AdjustUtil.stringToMap(adjustmentRecord.getFeePayoutList());
        Map<String, BigDecimal> feeDeductMap = AdjustUtil.stringToMap(adjustmentRecord.getFeeDeductList());
        Map<String, BigDecimal> feeRemainMap = AdjustUtil.stringToMap(adjustmentRecord.getFeeRemainList());

        // 如果合理金额为0
        if (feePayoutMap.containsKey("合理金额")) {
            BigDecimal validAmount = feeRemainMap.get("合理金额").add(feePayoutMap.get("合理金额"));
            if (validAmount.compareTo(BigDecimal.ZERO) == 0) {
                adjustmentRecord.setResultDetail(AdjustmentDetailFailReasonEnum.FIVE.getDesc());
            }
        }

        // 如果合理金额由于免赔导致为0
        if (feePayoutMap.containsKey("合理金额")) {
            if (feePayoutMap.get("合理金额").compareTo(BigDecimal.ZERO) == 0 && feeDeductMap.get("合理金额").compareTo(BigDecimal.ZERO) > 0) {
                adjustmentRecord.setResultDetail(AdjustmentDetailFailReasonEnum.SIX.getDesc());
            }
        }

        // 如果控额存在某个数值为0，导致为0
        for (BigDecimal quotaAmount : quotaMap.values()) {
            if (quotaAmount.compareTo(BigDecimal.ZERO) == 0) {
                adjustmentRecord.setResultDetail(AdjustmentDetailFailReasonEnum.NINE.getDesc());
                break;
            }
        }





        // 填入赔付原因类型
        if (AdjustmentDetailFailReasonEnum.getEnumByDesc(adjustmentRecord.getResultDetail()) != null) {
            adjustmentRecord.setCompensateType(AdjustmentDetailFailReasonEnum.getEnumByDesc(adjustmentRecord.getResultDetail()).getCode());
        }
    }


    /**
     * 全部完成以后进行直付冻结额度
     */
    private void checkDirectAmount(Map<String, BigDecimal> publicAccountMap, BigDecimal personalAccount,
                              String identityNo, String name, String slipCode) {
        //首先查询直付额度，确认其数值一致
        //然后调用直付冻结

        //公账额度相关
        for (String policyNo : publicAccountMap.keySet()) {
            ApiResult<PooledAccountBalance> balance = pooledAccountFeignClient
                    .queryAccount(policyNo);

            if (balance.getCode() != 0) {
                throw new TpaBizException(BizErrorCode.OUTER_CLIENT_ERROR,  "调用直付失败：" + balance.getMessage());
            }
            log.info("从直付获得{}公账额度: {}", balance.getData().getSlipCode(), balance.getData().getBalance());
            if (publicAccountMap.get(policyNo).equals(balance.getData().getBalance())) {
                throw new TpaBizException(BizErrorCode.ADJUST_NOT_ALLOWED, "两次查询直付的额度不同：" + policyNo);
            }
        }

        //个账额度相关
        if (personalAccount != null) {
            QueryBalanceRequest getPeopleInfoRequest = new QueryBalanceRequest();
            getPeopleInfoRequest.setPersonCertId(identityNo);
            getPeopleInfoRequest.setPersonName(name);
            getPeopleInfoRequest.setSlipCode(slipCode);

            ApiResult<List<QueryBalanceResponse>> balance
                    = directPaymentFeignClient.queryAccount(getPeopleInfoRequest);
            if (balance.getCode() != 0) {
                throw new TpaBizException(BizErrorCode.OUTER_CLIENT_ERROR,  "调用直付失败：" + balance.getMessage());
            }
            log.info("从直付获得{}个账额度: {}", balance.getData().get(0).getAccountSlipCode(), balance.getData().get(0).getAccountCurrAmt());
            if (personalAccount.equals(balance.getData().get(0).getAccountCurrAmt())) {
                throw new TpaBizException(BizErrorCode.ADJUST_NOT_ALLOWED, "两次查询直付个账的额度不同");
            }
        }
    }

    /**
     * 释放理算
     */
    public String releaseDirectPayAmount(Claim claim) {
        //判断其他理算的时候,如果案件正在理算，不让调用 todo 朱子元

        boolean isYcQuota = Objects.equals(claim.getInsuranceName(), "新永诚财产保险股份有限公司");

        // 获取出险人
        ClaimStakeholder outInsurePerson = claimInfoService.getStakeHolder(claim.getId(), claim.getBizIdentityCode(),
                claim.getTenantId(), PersonTypeEnum.OUT_INSURE.getCode(), true).get(0);

        //这里会查询直付是否有该人员的保单信息，暂时不需要
//        ApiResult<List<QueryPersonInfoV1>> queryPersonInfoList = directPaymentFeignClient
//                .queryPersonInfoList(scClaimdetailxp.getOutinsurename()
//                        ,scClaimdetailxp.getOutinsureidentityno(),claimdetail.getPolicyno());
//        if(queryPersonInfoList.getCode()!=0){
//            return new TwoTuple<>(Boolean.FALSE, "直付系统回传-该人员无保单信息!");
//        }

        if(Integer.parseInt(claim.getStatus()) >= 42 && !claim.getStatus().equals("46")){
            log.info("赔案号:{}已审核通过,无法返还额度!", claim.getClaimNo());
//            redisTemplate.delete("releaseAdjustmentLock" + claimNo);
            return "赔案已审核通过,无法返还额度";
        }
        if(claim.getInsureName()==null){
            claim.setInsureName("");
        }
        if (isYcQuota && StrUtil.isBlank(claim.getInsurerClaimNo())) {
            log.info("赔案号:{}好管家个账、公账释放理算失败：报案号不能为空!",claim.getClaimNo());
            return "好管家个账、公账释放理算失败：报案号不能为空";
        }

        // 永诚控额发票详情参数
        List<ClaimDetail> claimDetails = new ArrayList<>();

        //解冻公账金额
        List<AdjustmentRecord> adjustmentRecordList = adjustmentRecordBasicService.getAdjustmentRecordByClaim(claim.getId(), true);

        //gZAmountMap指的是公账保单号:公账额度的map
        Map<String, BigDecimal> gZAmountMap = new HashMap<>();

        for (AdjustmentRecord record : adjustmentRecordList) {
            if (record.getAccountType().equals(AccountTypeEnum.PUBLIC.getCode()) && record.getPublicAmountPolicyNo() != null && !record.getPublicAmountPolicyNo().isBlank()) {
                if (gZAmountMap.containsKey(record.getPublicAmountPolicyNo())) {
                    gZAmountMap.put(record.getPublicAmountPolicyNo(), record.getPayoutAmount());
                } else {
                    gZAmountMap.replace(record.getPublicAmountPolicyNo(), gZAmountMap.get(record.getPublicAmountPolicyNo()).add(record.getPayoutAmount()));
                }
            }
        }

        if (isYcQuota) {
            claimDetails = directQuotaService.publicAccountClaimDetail(gZAmountMap);
        } else {
            //解冻公账金额
            directQuotaService.freezePublicBalance(claim, outInsurePerson.getName(), outInsurePerson.getIdentityNo(), gZAmountMap, 2);
        }

        if (isYcQuota) {
            // 调用永诚个账公账解冻
            // 该接口解冻时不需要传个账信息
            directQuotaService.freezeAndThaw(claim, 2, claimDetails);
        }else {
            //解冻直付金额
            //目前不该走到这里
            log.error("赔案号:{}并非永城控额!", claim.getClaimNo());
            directQuotaService.deleteDirectPaymentAmount(claim);
        }

        log.info("赔案号:{}释放额度成功!", claim.getClaimNo());
        return "释放成功";
    }
}
