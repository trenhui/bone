package com.bone.tpa.claim.application;

import cn.hutool.core.collection.CollectionUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bone.core.result.PageResult;
import com.bone.core.util.BizContextUtils;
import com.bone.core.util.JsonUtil;
import com.bone.core.util.DateParserUtil;
import com.bone.core.util.PkListUtil;
import com.bone.core.util.SpringContextUtils;
import com.bone.tpa.api.ApiResult;
import com.bone.tpa.api.enums.CollectTypeEnum;
import com.bone.tpa.api.enums.EventType;
import com.bone.tpa.api.enums.HangUpStatus;
import com.bone.tpa.claim.application.converter.ClaimConverter;
import com.bone.tpa.claim.application.converter.ClaimCopyLogConverter;
import com.bone.tpa.claim.application.dto.ClaimCopyLogDTO;
import com.bone.tpa.claim.application.dto.ClaimDTO;
import com.bone.tpa.claim.application.dto.HangUpReordDTO;
import com.bone.tpa.claim.application.dto.TpaLogDTO;
import com.bone.tpa.claim.application.enums.BizModelEnum;
import com.bone.tpa.claim.application.enums.CommonLogType;
import com.bone.tpa.claim.application.enums.MoneyTypeEnum;
import com.bone.tpa.claim.application.request.*;
import com.bone.tpa.claim.application.response.*;
import com.bone.tpa.claim.domain.constant.RegistConstant;
import com.bone.tpa.claim.domain.service.ClaimService;
import com.bone.tpa.claim.domain.service.ClaimStakeholderService;
import com.bone.tpa.claim.domain.service.ClaimTrackLogService;
import com.bone.tpa.claim.domain.ext.strategy.YongChengStrategy;
import com.bone.tpa.claim.domain.service.*;
import com.bone.tpa.claim.flow.stage.ApproveStageService;
import com.bone.tpa.claim.flow.stage.InputStageService;
import com.bone.tpa.claim.flow.stage.QualityStageService;
import com.bone.tpa.claim.flow.stage.ReviewApproveStageService;
import com.bone.tpa.claim.infrastructure.log.SimpleLog;
import com.bone.tpa.claim.sync.ClaimToTpaChangeService;
import com.bone.tpa.core.redis.RedisLockManage;
import com.bone.tpa.core.util.ExtraStoreUtil;
import com.bone.tpa.facade.feign.TpaDataSyncFeign;
import com.bone.tpa.facade.request.*;
import com.bone.tpa.facade.vo.*;
import com.bone.tpa.hook.ClaimHookUtil;
import com.bone.tpa.hook.impl.claim.YongchengPageSaveValidHook;
import com.bone.tpa.hook.inter.ClaimPageSaveAfterHook;
import com.bone.tpa.hook.vo.ClaimUpdateHookParam;
import com.bone.tpa.intelligent.adjustment.engine.AdjustEngine;
import com.bone.tpa.intelligent.adjustment.service.AdjustmentRecordService;
import com.bone.tpa.intelligent.adjustment.service.LiabilityMappingService;
import com.bone.tpa.intelligent.adjustment.util.AdjustUtil;
import com.bone.tpa.push.service.PushClaimService;
import com.bone.tpa.sdk.adjustment.enums.AccountTypeEnum;
import com.bone.tpa.sdk.adjustment.model.*;
import com.bone.tpa.sdk.adjustment.model.liability.LiabilityConfig;
import com.bone.tpa.sdk.claim.enums.*;
import com.bone.tpa.sdk.claim.exception.TpaBizException;
import com.bone.tpa.sdk.claim.model.*;
import com.bone.tpa.sdk.dao.ClaimRepository;
import com.bone.tpa.sdk.service.AdjustmentRecordBasicService;
import com.bone.tpa.sdk.service.LiabilityInfoBasicService;
import com.bone.tpa.sdk.service.PolicyBasicService;
import com.bone.tpa.sdk.util.DateUtil;
import com.bone.tpa.sdk.util.FieldCheckingUtil;
import com.bone.tpa.task.impl.CheckSameInvoiceTrigger;
import com.bone.tpa.task.impl.CopyClaimTrigger;
import com.pkh.cloud.auth.sdk.util.UserUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 理赔案件应用层服务
 */
@Slf4j
@Service
@Transactional
public class ClaimApplicationService {
    @Autowired
    private CreateCertificateApplicationService createCertificateApplicationService;
    @Autowired
    private ClaimConverter claimConvertor;

    @Autowired
    private ClaimCopyLogService claimCopyLogService;

    @Autowired
    private ClaimCopyLogConverter claimCopyLogConverter;

    @Autowired
    private SignRecordService signRecordService;


    @Autowired
    private RedisLockManage redisLockManage;
    @Autowired
    private TpaDataSyncFeign tpaDataSyncFeign;


    @Autowired
    private ClaimStakeholderService claimStakeholderService;

    @Autowired
    private ClaimService claimService;

    @Autowired
    private ClaimImageService claimImageService;

    @Autowired
    private InvoiceImageRelationService invoiceImageRelationService;

    @Autowired
    private CommonLogService commonLogService;
    @Autowired
    ClaimToTpaChangeService toTpaChangeService;

    @Autowired
    private ClaimInvoiceService claimInvoiceService;

    @Autowired
    private ClaimTrackLogService claimTrackLogService;

    @Autowired
    private ClaimHookUtil claimHookUtil;
    @Autowired
    private ClaimRepository claimRepository;
    @Autowired
    private AdjustmentRecordService adjustmentRecordService;

    @Autowired
    private AdjustmentRecordBasicService adjustmentRecordBasicService;

    @Autowired
    private LiabilityInfoBasicService liabilityInfoBasicService;

    @Autowired
    private PolicyBasicService policyBasicService;

    @Autowired
    private LiabilityMappingService liabilityMappingService;


    @Autowired
    private InputStageService inputStageService;
    @Autowired
    private QualityStageService qualityStageService;

    @Autowired
    private ApproveStageService approveStageService;
    @Autowired
    private ReviewApproveStageService reviewApproveStageService;

    @Autowired
    @Qualifier("remoteRestTemplate")
    private RestTemplate restTemplate;


    @Value("${outurl.ycrul:http://api.test0.pukangpay.com.cn/mock/614}")
    private String ycgjMasterUrl = "";


    @SimpleLog
    @Transactional(rollbackFor = Throwable.class)
    public ClaimDetailObject getClaimDetail(Long claimId) {
        // 1. 获取赔案信息
        Claim claim = claimService.getClaimDetail(claimId);

        // 2. 获取赔案相关人信息
        List<String> personTypeList = new ArrayList<>();
        personTypeList.add(PersonTypeEnum.OUT_INSURE.getCode());
        personTypeList.add(PersonTypeEnum.MAIN_INSURE.getCode());
        personTypeList.add(PersonTypeEnum.COLLECT.getCode());
        List<ClaimStakeholder> stakeholderList = claimStakeholderService.getByClaimId(claimId, claim.getBizIdentityCode(), claim.getTenantId(), personTypeList);

        // 4. 组装结果
        ClaimDetailObject claimDetailObject = claimService.processClaimDetail(claim, stakeholderList);

        return claimDetailObject;
    }

    @SimpleLog
    @Transactional(rollbackFor = Throwable.class)
    public ClaimDetailObject submitClaimDetail(ClaimDetailObject claimDetailObject) {
        //提交赔案信息
         String redisLockKey="tpaSaas=submitClaimDetail-:"+claimDetailObject.getId();
         boolean lockret =   redisLockManage.tryLock(redisLockKey, 100);
         if(!lockret){
             throw new TpaBizException("系统处理中");
         }
        try {
            submitClaim(claimDetailObject);
        } finally {
            redisLockManage.unlock(redisLockKey);
        }

        return claimDetailObject;
    }


    public void submitClaim(ClaimDetailObject claimDetailObject) {
        // 1. 获取赔案基本信息
        Claim claim = claimRepository.findById(claimDetailObject.getId());
        if (claim == null) {
            log.error("Claim not found for id: {}", claimDetailObject.getId());
            throw new TpaBizException(BizErrorCode.NO_RECORD ,"Claim not found for id: " + claimDetailObject.getId());
        }
        claimService.checkFinish(claim);
        claimService.checkUserId(claim);

        // 检查是否挂起。
        Boolean hangUpFlag = claimService.checkClaimHangUpStatus(claim);
        if (hangUpFlag && claim.getHangUpType().equals(TpaHangupReason.资料不齐挂给客户.getCode())) {
            throw new TpaBizException(BizErrorCode.CLAIM_HANG_UP, claim.getClaimNo());
        }

        String oldClaim = JsonUtil.toJson(claim);

        if (ClaimStageEnum.getByValue(claimDetailObject.getStage()) == null) {
            //阶段错误，报错
            log.warn("Unknown stage type: {}", claimDetailObject.getStage());
            throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "Unknown stage type: " + claimDetailObject.getStage());
        }

        if (ClaimActionEnum.getByCode(claimDetailObject.getAction()) == null) {
            //状态错误，报错
            log.warn("Unknown status type: {}", claimDetailObject.getAction());
            throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "Unknown status type: " + claimDetailObject.getAction());
        }

        //阶段与操作信息
        if (claimDetailObject.getAction().equals(ClaimActionEnum.COMPLETE.getCode())) {
            if (hangUpFlag) {
                throw new TpaBizException(BizErrorCode.CLAIM_HANG_UP, claim.getClaimNo());
            }

            ClaimStatusEnum currentStatus = ClaimStatusEnum.getByCode(claim.getStatus(),claim.getStatusSub());


            switch (ClaimStageEnum.getByCode(claim.getStage())) {
                case SUBMITTING:
                    if(currentStatus != ClaimStatusEnum.PUKANG_INPUTING){
                        throw new TpaBizException("赔案不在录入中");
                    }
                    claim.setSubmittingOperatorName(claim.getOperatorUserName());
                    claim.setSubmittingPassTime(new Date());
                    break;
                case INSPECTION:
                    if(currentStatus != ClaimStatusEnum.INSPECTIONING){
                        throw new TpaBizException("赔案不在质检中");
                    }
                    claim.setInspectionOperatorName(claim.getOperatorUserName());
                    claim.setInspectionPassTime(new Date());
                    break;
                case AUDITING:
                    if(currentStatus != ClaimStatusEnum.Auditing){
                        throw new TpaBizException("赔案不在审核中");
                    }
                    List<AdjustmentRecord> adjustmentRecordList = adjustmentRecordBasicService.getAdjustmentRecordByClaim(claimDetailObject.getId(), true);
                    if (adjustmentRecordList == null || adjustmentRecordList.isEmpty()) {
                        throw new TpaBizException(BizErrorCode.NO_RECORD, "赔案还未理算！");
                    }
                    List<AdjustmentResult> adjustmentResultList = adjustmentRecordBasicService.getAdjustmentResultByClaim(claimDetailObject.getId(), true);
                    if (adjustmentResultList == null || adjustmentResultList.isEmpty()) {
                        throw new TpaBizException(BizErrorCode.NO_RECORD, "赔案还未理算！");
                    }

                    claim.setAuditingOperatorName(claim.getOperatorUserName());
                    claim.setAuditingPassTime(new Date());
                    break;
                case REVIEWING:
                    if(currentStatus != ClaimStatusEnum.AuditingReviewIng){
                        throw new TpaBizException("赔案不在复核");
                    }
                    claim.setReviewingOperatorName(claim.getOperatorUserName());
                    claim.setReviewingPassTime(new Date());

                    claim.setLimitHour(claimService.limitHourCalculator(claim));
                    claimRepository.update(claim);

                    reviewApproveStageService.pageApproveAction(claim.getId());
                    return;
                default:
                    throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "赔案阶段错误" + claim.getStage());
            }

//            claim.setStatus(ClaimActionEnum.DRAFT.getCode());
            //claim.setStage(ClaimStageEnum.getByCode(claim.getStage()).getNextStage());
        }

        //赔案基础信息，大部分并不能更改
        claim.setPlanUuid(claimDetailObject.getPlanUuid());
//        claim.setEmergency(claimDetailObject.getEmergency());
        claim.setSource(claimDetailObject.getSource());

        //赔案签收信息，全部都不能更改

        //赔案三方信息 这些也不能更改才是
//        claim.setInsurerClaimNo(claimDetailObject.getInsurerClaimNo());
//        claim.setInsurerRequestNo(claimDetailObject.getInsurerRequestNo());
//        claim.setInsurerBatchNo(claimDetailObject.getInsurerBatchNo());
//        claim.setInsurerPolicyNo(claimDetailObject.getInsurerPolicyNo());
//        claim.setInsurerReceiptNo(claimDetailObject.getInsurerReceiptNo());

        //出险信息
        claim.setOutInsureType(claimDetailObject.getOutInsureInfo().getOutInsureType());
        claim.setOutInsureTypeCn(claimDetailObject.getOutInsureInfo().getOutInsureTypeCn());
        claim.setOutInsureTime(claimDetailObject.getOutInsureInfo().getOutInsureTime());
        claim.setOutInsureRegion(claimDetailObject.getOutInsureInfo().getOutInsureRegion());
        claim.setOutInsureAddress(claimDetailObject.getOutInsureInfo().getOutInsureAddress());

        //领款人类型
        claim.setCollectType(claimDetailObject.getCollectInfo().getCollectType());

        //领款人信息
        if (claimDetailObject.getCollectInfo() != null) {
            CollectTypeEnum collectTypeEnum =  CollectTypeEnum.getEnumByCode(claimDetailObject.getCollectInfo().getCollectType());
            claim.setCollectType(collectTypeEnum == null ? CollectTypeEnum.PERSON.getCode(): collectTypeEnum.getCode());
        }

        //进行保司特定规则检查和处理
        // yongChengUpdateRule.onUpdateClaim(claim,claimDetailObject);
        List<ClaimPageSaveAfterHook> claimPageSaveAfterHookList =  claimHookUtil.getClaimSaveHookList(claim.getBizIdentityCode());
        for(ClaimPageSaveAfterHook hook : claimPageSaveAfterHookList){
            ClaimUpdateHookParam saveEventParam = new ClaimUpdateHookParam();
            saveEventParam.setClaimExist(claim);
            saveEventParam.setClaimDetailObject(claimDetailObject);
            hook.doEvent(saveEventParam);
        }
        //赔案专属字段
        claim.getExtraProperties().putAll(claimDetailObject.getExtraProperties());
        claim.getExtraProperties().putAll(claimDetailObject.getOutInsureInfo().getExtraProperties());
        claim.getExtraProperties().putAll(claimDetailObject.getCollectInfo().getExtraProperties());
        String newStroe = ExtraStoreUtil.mergeExtraStore(claimDetailObject.getExtraStore(),claim.getExtraStore());

        newStroe = ExtraStoreUtil.mergeExtraStore(claimDetailObject.getOutInsureInfo().getExtraStore(),newStroe);
        newStroe = ExtraStoreUtil.mergeExtraStore(claimDetailObject.getCollectInfo().getExtraStore(),newStroe);
        claim.setExtraStore(newStroe);
        //校验赔案
        List<YongchengPageSaveValidHook>  preValidHookList =  claimHookUtil.getYongchengPageSaveValidHookList(claim.getBizIdentityCode());
        for(YongchengPageSaveValidHook hook : preValidHookList){
            hook.doEvent(claim);
        }
        // claimValidationExt.validate(claim);

        //赔案持久化
        claimRepository.save(claim);

        //相关人信息持久化
        claimStakeholderService.saveStakeHolder(claimDetailObject);

        //理算结论的更新
        adjustmentRecordService.saveConclusion(claimDetailObject.getAdjustConclusion(), claimDetailObject.getId());

        //增加操作记录
        claimTrackLogService.claimRecord(claim, BizContextUtils.getUser(), oldClaim, JsonUtil.toJson(claim),
                OperationTypeEnum.UPDATE , null, "页面提交更新赔案");


        //同步到新tpa
        if (claimDetailObject.getAction().equals(ClaimActionEnum.COMPLETE.getCode())) {
            if (claimDetailObject.getStage().equals(ClaimStageEnum.SUBMITTING.getValue())) {

                inputStageService.pageApproveAction(claim.getId());
                //claimActionService.manualInputSuccess(claim.getId());
            } else if (claimDetailObject.getStage().equals(ClaimStageEnum.INSPECTION.getValue())) {
                //额外判断 暂时不要，后续移到保司分类规则中
                qualityStageService.pageApproveAction(claim.getId());
            }else if(claimDetailObject.getStage().equals(ClaimStageEnum.AUDITING.getValue())){

                // 撤件
                if (!StringUtils.equals(ClmProcessType.HALF_PROCESS.getCode(), claim.getProcessType())
                        && StringUtils.contains(claimDetailObject.getAdjustConclusion().getPayOutConclusion(), ClaimStatusEnum.Cancel.getSubStatus())) {
                    claimService.cancelClaim(claim);
                    claim.setLimitHour(claimService.limitHourCalculator(claim));
                    claimRepository.update(claim);
                    // 通知
                    claimService.notifyTpaChangeStatus(claim,new Date(),ClaimStatusEnum.Cancel,
                            PkListUtil.newArrayList(),
                            BizContextUtils.getUser(),
                            "撤件到终态","撤件到终态" ,true);
                    return;
                }

                claim.setLimitHour(claimService.limitHourCalculator(claim));
                claimRepository.update(claim);

                approveStageService.pageApproveAction(claim.getId());

                //审核通过后读取单证配置并且生成证书
                createCertificateApplicationService.loadConfigAndCreateCertificate(claim.getId());
            }
        }
    }


    @SimpleLog
    @Transactional(rollbackFor = Throwable.class)
    public ClaimDTO hangUpClaim(ClaimHangupRequest request) {
        //挂起对应赔案
        Claim claim = claimService.hangUpClaim(request);

        //转化为DTO返回
        return claimConvertor.toDTO(claim);
    }


    @SimpleLog
    @Transactional(rollbackFor = Throwable.class)
    public ClaimDTO rejectClaim(ClaimRejectRequest request) {
        //挂起对应赔案
        Claim claim = claimService.rejectClaim(request);

        //转化为DTO返回
        return claimConvertor.toDTO(claim);
    }


    @SimpleLog
    @Transactional(rollbackFor = Throwable.class)
    public void copyClaim(ClaimCopyRequest request) {
        //分析对应赔案号
        List<Long> claimIdList = claimService.parseClaimNos(request.getClaimNos());

        if (claimIdList.isEmpty()) {
            throw new TpaBizException(BizErrorCode.COPY_NOY_ALLOWED, "未填赔案号");
        }

        //查一下这些赔案是否存在于系统中
        List<Claim> oldClaimList = claimService.getByIdList(claimIdList);
        List<Long> oldClaimIdList = oldClaimList.stream().map(Claim::getId).collect(Collectors.toList());

        List<Long> notExistClaimId = claimIdList.stream()
                                    .filter(num -> !oldClaimIdList.contains(num))
                                    .collect(Collectors.toList());

        if (!notExistClaimId.isEmpty()) {
            throw new TpaBizException(BizErrorCode.NO_RECORD, "赔案号" + notExistClaimId + "不存在");
        }

        //检查每一个赔案是否能够复制
        String sameBizIdentity = oldClaimList.get(0).getBizIdentityCode();
        for (Claim oldClaim : oldClaimList) {
            //不能是已经挂起的
            Boolean hangUpFlag = claimService.checkClaimHangUpStatus(oldClaim);
            if (hangUpFlag) {
                throw new TpaBizException(BizErrorCode.CLAIM_HANG_UP, oldClaim.getClaimNo());
            }

            if (oldClaim.getStatus().equals(ClaimStatusEnum.Finish.getCode()) || oldClaim.getStatus().equals(ClaimStatusEnum.Cancel.getCode())) {
                throw new TpaBizException("赔案" + oldClaim.getClaimNo() +"已撤件或结案");
            }

            //必须得是审核阶段往后的状态
            if (!oldClaim.getStatus().equals(ClaimStatusEnum.Auditing.getCode())
                    && !oldClaim.getStatus().equals(ClaimStatusEnum.COMPLETE_AUDIT.getCode())
                    && !oldClaim.getStatus().equals(ClaimStatusEnum.AuditingReviewIng.getCode())) {
                throw new TpaBizException(BizErrorCode.COPY_NOY_ALLOWED, "赔案" + oldClaim.getClaimNo() + "的状态不为审核中或已审核");
            }

            if (!oldClaim.getBizIdentityCode().equals(sameBizIdentity)) {
                throw new TpaBizException(BizErrorCode.COPY_NOY_ALLOWED, "当前只支持同业务主体的赔案一起复制。请分别操作");
            }
        }


        //查询处理人
        // 调用tpa判断用户是否存在
        String operatorOrgId = null;
        String operatorOrgName = null;
        String operatorUserId = null;
        String operatorUserName = null;

        if (!request.getNewClaimOperator().equals(CopyOperatorTypeEnum.ORIGIN.getCode())) {
            UserCheckRequest userCheckRequest = new UserCheckRequest();
            userCheckRequest.setStage(ClaimStatusEnum.getByCode(request.getNewClaimStatus(), "").getStage().getCode());
            //根据选项填入不同的人
            if (request.getNewClaimOperator().equals(CopyOperatorTypeEnum.SPECIFIC.getCode())) {
                userCheckRequest.setUserName(request.getOperatorName());
            } else {
                userCheckRequest.setUserName(BizContextUtils.getUser());
            }
            ApiResult<UserCheckVO> queryUserGroupInfo = tpaDataSyncFeign.queryUserGroupInfo(userCheckRequest);
            if (!Objects.equals(queryUserGroupInfo.getCode(), 0) || Objects.isNull(queryUserGroupInfo.getData()) || CollectionUtil.isEmpty(queryUserGroupInfo.getData().getGroupList())) {
                throw new TpaBizException("复制赔案查询用户不存在:"+userCheckRequest.getUserName());
            }
            UserCheckVO userCheckVO = queryUserGroupInfo.getData();

            operatorUserId = userCheckVO.getUserId();
            operatorUserName = userCheckVO.getUserName();
            if (!userCheckVO.getGroupList().isEmpty()) {
                operatorOrgId = userCheckVO.getGroupList().get(0).getGroupId().toString();
                operatorOrgName = userCheckVO.getGroupList().get(0).getGroupName();
            }
        }

        //调用新tpa获取新赔案号
        TpaNewClaimNoRequest newClaimNoRequest = new TpaNewClaimNoRequest();
        newClaimNoRequest.setClaimNos(claimIdList);
        newClaimNoRequest.setNeedNewBatchNo(request.getIsBatchAndSignTime()); // 是否有新批次
        newClaimNoRequest.setIsCopySerialNo(request.getIsCopySerialNo()); // 是否复制收单流水号
        newClaimNoRequest.setUserName(BizContextUtils.getUser()); // 当前的操作人
        newClaimNoRequest.setRemark(request.getRemark()); // 备注

        //调用tpa生成新赔案号
        ApiResult<NewClaimNoResponse> newClaimNoResult = tpaDataSyncFeign.generateNewClaimNo4Saas(newClaimNoRequest);
        if(!newClaimNoResult.isSuccess()){
            throw new TpaBizException("生成新赔案号失败:" + newClaimNoResult.getMessage());
        }
        NewClaimNoResponse newClaimNoResponse = newClaimNoResult.getData();
        if( newClaimNoResponse == null){
            throw new TpaBizException("未获取到新赔案号");
        }
        List<Long> newClaimNoList = newClaimNoResponse.getNewClaimNos();
        if (newClaimNoList == null || newClaimNoList.isEmpty()) {
            throw new TpaBizException("未获取到新赔案号");
        }

        List<ClaimCopyLog> claimCopyLogList= new ArrayList<>();

        Date oldSignTime = null;
        Date newSignTime = new Date();

        //以下开始正式复制赔案
        //计算批次号
        Long headerNumber = newClaimNoList.get(0) / 1000;
        //这里偷一个懒，去取得第一个赔案对应的signRecord
        Long oldSignRecordId = oldClaimList.get(0).getRelatedId();

        SignRecord signRecord = signRecordService.getSignRecord(oldSignRecordId);

        if (signRecord.getSignTime() != null) {
            oldSignTime = signRecord.getSignTime();
        } else {
            oldSignTime = signRecord.getCreateTime();
        }

        //首先是签收记录和对应的批次
        if (request.getIsBatchAndSignTime()) {
            //插入赔案批次
            signRecord.setId(headerNumber);
            signRecord.setBatchNo(headerNumber.toString());
            signRecord.setClaimCount(newClaimNoList.size());

            //签收人姓名（中文)
            signRecord.setSignOperator(BizContextUtils.getUser());
            //签收时间
            signRecord.setSignTime(newSignTime);

            //创建和更新时间
            signRecord.setCreateTime(null);
            signRecord.setUpdateTime(null);

            signRecordService.insertNewSignRecord(signRecord);
        }

        //针对每个赔案开始复制
        List<Claim> newClaimList = new ArrayList<>();

        Map<Long, Claim> claimMap = oldClaimList.stream().collect(Collectors.toMap(Claim::getId, t -> t));
        for (int i = 0; i < claimIdList.size(); i++) {
            //针对每个赔案开始复制
            Claim newClaim = claimMap.get(claimIdList.get(i));

            Long oldId = newClaim.getId();
            String oldBatch = newClaim.getBatchNo();

            //更新赔案号
            newClaim.setId(newClaimNoList.get(i));
            newClaim.setClaimNo(String.valueOf(newClaimNoList.get(i)));
            newClaim.setBatchNo(String.valueOf(newClaimNoList.get(i) / 1000));
            newClaim.setClaimDetailUuid(String.valueOf(UUID.randomUUID()));
            if (!request.getIsCopyInsureClaimNo()) {
                //如果不复制保司报案号就把它清掉
                newClaim.setInsurerClaimNo(null);
            }

            //根据操作人选项更改
            if (request.getNewClaimOperator().equals(CopyOperatorTypeEnum.ORIGIN.getCode())) {
                if (newClaim.getAuditingOperatorName() == null || newClaim.getAuditingOperatorName().isBlank()) {
                    //这种情况下，这个赔案应该是正好在审核中，所以我们检查一下
                    if (!newClaim.getStatus().equals(ClaimStatusEnum.Auditing.getCode())) {
                        throw new TpaBizException("赔案无审核操作人员"+newClaim.getClaimNo());
                    }
                    //不是的话不用改
                } else {
                    UserCheckRequest userCheckRequest = new UserCheckRequest();
                    userCheckRequest.setStage(ClaimStatusEnum.getByCode(request.getNewClaimStatus(), "").getStage().getCode());
                    userCheckRequest.setUserName(newClaim.getAuditingOperatorName());
                    ApiResult<UserCheckVO> queryUserGroupInfo = tpaDataSyncFeign.queryUserGroupInfo(userCheckRequest);
                    if (!Objects.equals(queryUserGroupInfo.getCode(), 0) || Objects.isNull(queryUserGroupInfo.getData()) || CollectionUtil.isEmpty(queryUserGroupInfo.getData().getGroupList())) {
                        throw new TpaBizException("复制赔案查询用户不存在:"+request.getOperatorName());
                    }
                    UserCheckVO userCheckVO = queryUserGroupInfo.getData();

                    if (!userCheckVO.getGroupList().isEmpty()) {
                        newClaim.setOperatorOrgId(userCheckVO.getGroupList().get(0).getGroupId().toString());
                        newClaim.setOperatorOrgName(userCheckVO.getGroupList().get(0).getGroupName());
                    }
                    newClaim.setOperatorUserId(userCheckVO.getUserId());
                    newClaim.setOperatorUserName(userCheckVO.getUserName());
                }
            } else {
                newClaim.setOperatorOrgId(operatorOrgId);
                newClaim.setOperatorOrgName(operatorOrgName);
                newClaim.setOperatorUserId(operatorUserId);
                newClaim.setOperatorUserName(operatorUserName);
            }

            //如果需要调整赔付额，就立刻查询理算记录
            Map<Long, List<AdjustmentRecord>> adjustmentRecordMap = new HashMap<>();
            if (request.getIsCompensationAmount() && newClaim.getStatus().equals(ClaimStatusEnum.COMPLETE_AUDIT.getCode())) {
                List<AdjustmentRecord> adjustmentRecordList = adjustmentRecordBasicService.getAdjustmentRecordByClaim(oldId, false);

                adjustmentRecordMap = adjustmentRecordList.stream().collect(Collectors.groupingBy(AdjustmentRecord::getInvoiceId, Collectors.toList()));
            }

            //然后根据返回到哪个阶段，清除对应阶段之后的数据
            if (request.getNewClaimStatus().equals(ClaimStatusEnum.Auditing.getCode())) {
                newClaim.setStatus(ClaimStatusEnum.Auditing.getCode());
                newClaim.setStatusSub(null);
                newClaim.setStage(ClaimStageEnum.AUDITING.getCode());

                newClaim.setAuditingOperatorName(null);
                newClaim.setAuditingPassTime(null);
                newClaim.setReviewingOperatorName(null);
                newClaim.setReviewingPassTime(null);
                newClaim.setLimitHour(null);
            } else {
                throw new TpaBizException(BizErrorCode.NOT_IMPLEMENTED);
            }

            //创建和更新时间
            newClaim.setCreateTime(null);
            newClaim.setUpdateTime(null);

            newClaimList.add(newClaim);

            //复制相关人
            List<ClaimStakeholder> claimStakeholderList = claimStakeholderService.getByClaimId(oldId, newClaim.getBizIdentityCode(), newClaim.getTenantId(), null);
            for (ClaimStakeholder stakeholder : claimStakeholderList) {
                stakeholder.setId(null);
                stakeholder.setRelatedId(newClaimNoList.get(i));
            }
            claimStakeholderService.insertBatch(claimStakeholderList);

            //复制发票
            List<ClaimInvoice> claimInvoiceList = claimInvoiceService.getClaimInvoicesByClaimNumber(oldId);
            Map<String, String> invoiceUuidMap = new HashMap<>();
            for (ClaimInvoice claimInvoice : claimInvoiceList) {
                Long oldInvoiceId = claimInvoice.getId();
                claimInvoice.setId(null);
                claimInvoice.setRelatedId(newClaimNoList.get(i));
                String newUuid = String.valueOf(UUID.randomUUID());
                //存储，用于后续的影像件关联关系
                invoiceUuidMap.put(claimInvoice.getInvoiceUuid(), newUuid);
                claimInvoice.setInvoiceUuid(newUuid);
                //创建和更新时间
                claimInvoice.setCreateTime(null);
                claimInvoice.setUpdateTime(null);

                //这里处理赔付金额
                if (adjustmentRecordMap.get(oldInvoiceId) != null) {
                    //调用对应的方法，去那边处理
                    adjustmentRecordService.processPayOutMoney(claimInvoice, adjustmentRecordMap.get(oldInvoiceId));
                }
            }
            claimInvoiceService.insertBatch(claimInvoiceList);

            //复制影像件
            List<ClaimImage> claimImageList = claimImageService.getListByClaimNumber(oldId);
            List<ClaimImage> newClaimImageList = new ArrayList<>();
            Map<String, String> imageUuidMap = new HashMap<>();
            for (ClaimImage claimImage : claimImageList) {
                //生成的理赔申请书之类的不要
                if (claimImage.getCertificateType() != null) {
                    continue;
                }

                claimImage.setId(null);
                claimImage.setRelatedId(newClaimNoList.get(i));
                String newUuid = String.valueOf(UUID.randomUUID());
                //存储，用于后续的影像件关联关系
                imageUuidMap.put(claimImage.getImageDetailId(), newUuid);
                claimImage.setImageDetailId(newUuid);
                //创建和更新时间
                claimImage.setCreateTime(null);
                claimImage.setUpdateTime(null);

                newClaimImageList.add(claimImage);
            }
            claimImageService.insertBatch(newClaimImageList);

            //复制发票关联影像件信息
            List<InvoiceImageRelation> imageRelationList = invoiceImageRelationService.getListByClaimId(oldId);
            List<InvoiceImageRelation> newImageRelationList = new ArrayList<>();
            for (InvoiceImageRelation imageRelation : imageRelationList) {
                if (invoiceUuidMap.containsKey(imageRelation.getInvoiceUuid()) && imageUuidMap.containsKey(imageRelation.getImageDetailId())) {
                    imageRelation.setId(null);
                    imageRelation.setClaimId(newClaimNoList.get(i));
                    imageRelation.setInvoiceUuid(invoiceUuidMap.get(imageRelation.getInvoiceUuid()));
                    imageRelation.setImageDetailId(imageUuidMap.get(imageRelation.getImageDetailId()));

                    //创建和更新时间
                    imageRelation.setCreateTime(null);
                    imageRelation.setUpdateTime(null);

                    newImageRelationList.add(imageRelation);
                }
            }
            invoiceImageRelationService.insertBatch(newImageRelationList);


            //构造复制记录表
            ClaimCopyLog claimCopyLog = new ClaimCopyLog();
            claimCopyLog.setPolicyNo(newClaim.getPolicyNo());
            claimCopyLog.setOldBatchNo(oldBatch);
            claimCopyLog.setOldClaimId(oldId);
            claimCopyLog.setOldClaimNo(String.valueOf(oldId));
            claimCopyLog.setOldSignTime(oldSignTime);
            claimCopyLog.setNewBatchNo(newClaim.getBatchNo());
            claimCopyLog.setNewClaimId(newClaimNoList.get(i));
            claimCopyLog.setNewClaimNo(String.valueOf(newClaimNoList.get(i)));
            if (request.getIsBatchAndSignTime()) {
                claimCopyLog.setNewSignTime(newSignTime);
            } else {
                claimCopyLog.setNewSignTime(oldSignTime);
            }
            claimCopyLog.setOperator(BizContextUtils.getUser());
            claimCopyLog.setOperateTime(new Date());
            claimCopyLog.setRemark(request.getRemark());
            claimCopyLog.setCopyRequest(JsonUtil.toJson(request));

            claimCopyLogList.add(claimCopyLog);
        }

        //插入赔案表
        claimService.insertBatch(newClaimList);

        //插入赔案复制记录表
        claimCopyLogService.insertBatch(claimCopyLogList);

        //同步给tpa
        for (Long newId : newClaimNoList) {
            SpringContextUtils.getBean(CopyClaimTrigger.class).addJobAndTryFire(String.valueOf(newId),
                    2, 61
            );
        }
    }

    @SimpleLog
    public PageResult<ClaimCopyLogDTO> queryClaimCopyLog(QueryListRequest request) {
        PageResult<ClaimCopyLog> claimCopyLogList = claimCopyLogService.getClaimCopyLog(request);

        List<ClaimCopyLogDTO> claimCopyLogDTOList = new ArrayList<>();
        for (ClaimCopyLog claimCopyLog : claimCopyLogList.getData()) {
            claimCopyLogDTOList.add(claimCopyLogConverter.toDTO(claimCopyLog));
        }

        return new PageResult<>(claimCopyLogDTOList, claimCopyLogList.getCurrPage(), claimCopyLogList.getPageSize(), claimCopyLogList.getTotalCount());
    }


    @SimpleLog
    @Transactional(rollbackFor = Throwable.class)
    public String getPolicySettingInfo(Long claimId, Integer type) {
        Claim claim = claimService.getById(claimId);
        if (claim == null) {
            throw new TpaBizException(BizErrorCode.NO_RECORD, "赔案不存在: " + claimId);
        }

        PolicySettingRemarkQueryRequest request = new PolicySettingRemarkQueryRequest();
        request.setPolicyNo(claim.getPolicyNo());
        if (type != null && type == 1) {
            request.setSettingSign("1");
            //获取出险人
            List<ClaimStakeholder> outInsure = claimStakeholderService.getByClaimId(claimId, claim.getBizIdentityCode(), claim.getTenantId(), Collections.singletonList(PersonTypeEnum.OUT_INSURE.getCode()));
            if (outInsure == null || outInsure.isEmpty()) {
                throw new TpaBizException(BizErrorCode.NO_RECORD, "出险人不存在，赔案为: " + claimId);
            }
            request.setCertId(outInsure.get(0).getIdentityNo());
        } else {
            request.setSettingSign("0");
        }

        ApiResult<PolicySettingRemarkVO> remoteRs = tpaDataSyncFeign.saasCheckPolicySettingRemark(request);
        log.info("saasCheckPolicySettingRemark: {}", JSONObject.toJSONString(remoteRs));
        if(!remoteRs.isSuccess()){
            throw new RuntimeException("特约/特殊信息查询接口调用失败:" + remoteRs.getMessage());
        }

        return remoteRs.getData().getRemark();
    }


    @SimpleLog
    @Transactional(rollbackFor = Throwable.class)
    public ApplyInfo getApplyInfo(Long claimId) {
        ReportingInfoQueryRequest request = new ReportingInfoQueryRequest();
        request.setClaimNumber(claimId.toString());
        ApiResult<GetReportingInfoResponse> remoteRs = tpaDataSyncFeign.getReportingInformation(request);
        log.info("getReportingInformation: {}", JSONObject.toJSONString(remoteRs));
        if(!remoteRs.isSuccess()){
            throw new RuntimeException("报案信息查询接口调用失败:" + remoteRs.getMessage());
        }

        ApplyInfo applyInfo = new ApplyInfo();
        applyInfo.setApplyName(remoteRs.getData().getApplyPersonName());
        applyInfo.setApplyIdentityNo(remoteRs.getData().getApplyPersonCertNo());
        applyInfo.setApplyType(remoteRs.getData().getApplyType());
        applyInfo.setApplyTime(remoteRs.getData().getApplyTime());
        applyInfo.setApplyPhone(remoteRs.getData().getOutTel());

        applyInfo.setOutInsureName(remoteRs.getData().getRealName());
        applyInfo.setOutInsureIdentityNo(remoteRs.getData().getCertNo());
        applyInfo.setOutInsureTime(remoteRs.getData().getCreateTime());
        applyInfo.setPolicyNo(remoteRs.getData().getSlipCode());

        applyInfo.setCollectName(remoteRs.getData().getPayeeCertid());
        applyInfo.setAccountNo(remoteRs.getData().getPayeeBankAmount());
        applyInfo.setBankName(remoteRs.getData().getPayeeBankName());
        applyInfo.setBankAddress(remoteRs.getData().getPayeeBankAddress());

//        Claim claim = claimService.getById(claimId);
//        List<String> personTypeList = new ArrayList<>();
//        personTypeList.add(PersonTypeEnum.OUT_INSURE.getCode());
//        personTypeList.add(PersonTypeEnum.COLLECT.getCode());
//        personTypeList.add(PersonTypeEnum.APPLY.getCode());
//        List<ClaimStakeholder> stakeholderList = claimStakeholderService.getByClaimId(claimId, claim.getBizIdentityCode(), claim.getTenantId(), personTypeList);
//        //相关人类型, 相关人对象
//        Map<String, List<ClaimStakeholder>> stakeholderMap = stakeholderList.stream().collect(Collectors.groupingBy(ClaimStakeholder::getPersonType));
//
//        //构造返回结果
//        ApplyInfo applyInfo = new ApplyInfo();
//        List<ClaimStakeholder> apply = stakeholderMap.get(PersonTypeEnum.APPLY.getCode());
//        if (apply != null && !apply.isEmpty()) {
//            applyInfo.setApplyName(apply.get(0).getName());
//            applyInfo.setApplyIdentityNo(apply.get(0).getIdentityNo());
//            applyInfo.setApplyType(apply.get(0).getApplyTypeCn());
//            applyInfo.setApplyTime(apply.get(0).getApplyTime());
//            applyInfo.setApplyPhone(apply.get(0).getPhone());
//        }
//
//        List<ClaimStakeholder> outInsure = stakeholderMap.get(PersonTypeEnum.OUT_INSURE.getCode());
//        if (outInsure != null && !outInsure.isEmpty()) {
//            applyInfo.setOutInsureName(outInsure.get(0).getName());
//            applyInfo.setOutInsureIdentityNo(outInsure.get(0).getIdentityNo());
//        }
//        applyInfo.setOutInsureTime(claim.getOutInsureTime());
//        applyInfo.setPolicyNo(claim.getPolicyNo());
//
//        List<ClaimStakeholder> collect = stakeholderMap.get(PersonTypeEnum.COLLECT.getCode());
//        if (collect != null && !collect.isEmpty()) {
//            applyInfo.setCollectName(collect.get(0).getName());
//            applyInfo.setAccountNo(collect.get(0).getAccountNo());
//            applyInfo.setBankName(collect.get(0).getBankCodeCn());
//            applyInfo.setBankAddress(collect.get(0).getBankAddress());
//        }

        return applyInfo;
    }


    @SimpleLog
    @Transactional(rollbackFor = Throwable.class)
    public HangupInfo getHangupInfo(Long claimId) {
        Claim claim = claimService.getById(claimId);

        if (claim == null) {
            throw new TpaBizException(BizErrorCode.NO_RECORD, "Claim not found for id: " + claimId);
        }

        if (claim.getHangUpStatus() == null || claim.getHangUpStatus().equals(HangUpStatus.NO_HANG_UP.getCode())) {
            throw new TpaBizException(BizErrorCode.INNER_PARAMETER_ERROR, "该赔案不处于挂起状态");
        }

        ClaimTrackLog hangupLog = claimTrackLogService.queryLatestClaimRecord(claimId, OperationTypeEnum.HANGUP);

        if (hangupLog == null) {
            throw new TpaBizException(BizErrorCode.NO_RECORD, "不存在挂起记录");
        }

        HangupInfo hangupInfo = new HangupInfo();
        hangupInfo.setHangupOperator(hangupLog.getOperator());
        hangupInfo.setHangupTime(hangupLog.getCreateTime());
        hangupInfo.setHangupType(hangupLog.getMessage());
        hangupInfo.setHangupReason(hangupLog.getRemark());

        return hangupInfo;
    }


    @SimpleLog
    @Transactional(rollbackFor = Throwable.class)
    public List<TpaLogDTO> getClaimRecord(Long claimNo) {
        ApiResult<List<TpaLogVO>> remoteRs = tpaDataSyncFeign.getLogs(claimNo);
        log.info("addClaimHangUpReord: {}", JSONObject.toJSONString(remoteRs));
        if(!remoteRs.isSuccess()){
            throw new RuntimeException("操作日志查询接口调用失败:" + remoteRs.getMessage());
        }

        List<TpaLogDTO> logDTOList = new ArrayList<>();
        if (remoteRs.getData() == null || remoteRs.getData().isEmpty()) {
            return logDTOList;
        }

        for (TpaLogVO tpaLogVO : remoteRs.getData()) {
            TpaLogDTO tpaLogDTO = new TpaLogDTO();
            tpaLogDTO.setRemark(tpaLogVO.getRemark());
            tpaLogDTO.setOperation(tpaLogVO.getOperation());
            tpaLogDTO.setObjectId(tpaLogVO.getObjectId());
            tpaLogDTO.setCreateBy(tpaLogVO.getCreateBy());
            tpaLogDTO.setCreateTime( new Date(tpaLogVO.getCreateTime()));

            logDTOList.add(tpaLogDTO);
        }

        return logDTOList;
    }

    @SimpleLog
    @Transactional(rollbackFor = Throwable.class)
    public List<HangUpReordDTO> getHangUpRecord(String claimNo) {
        HangUpReordVO queryRequest = new HangUpReordVO();
        queryRequest.setClaimNo(claimNo);
        ApiResult<List<HangUpReordVO>> remoteRs = tpaDataSyncFeign.saasGetClaimHangUpRecord(queryRequest);
        if(!remoteRs.isSuccess()){
            throw new RuntimeException("获取挂起记录失败:"+remoteRs.getMessage());
        }
        List<HangUpReordVO> logList = remoteRs.getData();
        log.info(logList.toString());
        if(logList == null){
            logList = PkListUtil.newArrayList();
        }
        List<HangUpReordDTO> logDTOList = new ArrayList<>();
        for (HangUpReordVO hangUpReordVO : logList) {
            HangUpReordDTO hangUpReordDTO = new HangUpReordDTO();
            hangUpReordDTO.setHangUpTime(hangUpReordVO.getHangUpTime());
            hangUpReordDTO.setExplanation(hangUpReordVO.getExplanation());
            hangUpReordDTO.setClaimNo(hangUpReordVO.getClaimNo());
            hangUpReordDTO.setReason(hangUpReordVO.getReason());
            hangUpReordDTO.setReasonType(hangUpReordVO.getReasonType());

            logDTOList.add(hangUpReordDTO);
        }

        return logDTOList;
    }


    @SimpleLog
    @Transactional(rollbackFor = Throwable.class)
    public Long checkSameInvoice(Long id) {
        return claimService.checkSameInvoice(id);
    }


    @SimpleLog
    @Transactional(rollbackFor = Throwable.class)
    public String getHint(Long id) {
        return claimService.getHint(id);
    }


    @SimpleLog
    @Transactional(rollbackFor = Throwable.class)
    public ReportCondition getReportClaimCondition(Long id) {
        if (id == null) {
            throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "赔案号不能为空");
        }
        Claim claim = claimService.getById(id);

        if (claim == null) {
            throw new TpaBizException(BizErrorCode.NO_RECORD, "赔案不存在" + id);
        }

        if (claim.getPlanUuid() == null || claim.getPlanUuid().isBlank()) {
            throw new TpaBizException(BizErrorCode.NO_RECORD, "赔案未绑定保单" + id);
        }

        List<String> personTypeList = new ArrayList<>();
        personTypeList.add(PersonTypeEnum.OUT_INSURE.getCode());
        personTypeList.add(PersonTypeEnum.MAIN_INSURE.getCode());
        personTypeList.add(PersonTypeEnum.COLLECT.getCode());
        personTypeList.add(PersonTypeEnum.COLLECT_BUSINESS.getCode());
        List<ClaimStakeholder> stakeholderList = claimStakeholderService.getByClaimId(id, claim.getBizIdentityCode(), claim.getTenantId(), personTypeList);

        ClaimStakeholder outInsure = null;
        ClaimStakeholder mainInsure = null;
        ClaimStakeholder collect = null;
        ClaimStakeholder collectBusiness = null;
        for (ClaimStakeholder stakeholder : stakeholderList) {
            if (stakeholder.getPersonType().equals(PersonTypeEnum.OUT_INSURE.getCode())) {
                outInsure = stakeholder;
            }
            if (stakeholder.getPersonType().equals(PersonTypeEnum.MAIN_INSURE.getCode())) {
                mainInsure = stakeholder;
            }
            if (stakeholder.getPersonType().equals(PersonTypeEnum.COLLECT.getCode()) && collect == null) {
                collect = stakeholder;
            }
            if (stakeholder.getPersonType().equals(PersonTypeEnum.COLLECT_BUSINESS.getCode()) && collectBusiness == null) {
                collectBusiness = stakeholder;
            }
        }

        ReportCondition response = new ReportCondition();
        response.setClaimNo(claim.getClaimNo());
        response.setPolicyNo(claim.getPolicyNo());

        if (outInsure != null) {
            response.setOutInsureName(outInsure.getName());
            response.setOutInsureAge(String.valueOf(AdjustUtil.calculateAge(outInsure.getIdentityNo())));
            response.setOutInsureIdentityNo(outInsure.getIdentityNo());
            response.setOutInsureIdentityType(outInsure.getIdentityTypeCn());
            response.setOutInsureGender(outInsure.getGender());
            response.setOutInsureBirthDay(outInsure.getBirthday());
        }


        if (outInsure != null && mainInsure != null) {
            if (Objects.equals(outInsure.getIdentityNo(), mainInsure.getIdentityNo())) {
                response.setOutInsureIdentity("被保险人");
                response.setRelationType("被保险人");
            } else {
                response.setOutInsureIdentity("附属被保险人");
                response.setRelationType("附属被保险人");
            }
        }

        //报案人与联系人
        if (mainInsure != null) {
            response.setReportName(mainInsure.getName());
            response.setContactName(mainInsure.getName());
        }

        if (collect != null && claim.getCollectType().equals(CollectTypeEnum.PERSON.getCode())) {
            response.setReportPhone(collect.getPhone());
            response.setContactPhone(collect.getPhone());
        } else if (collectBusiness != null && claim.getCollectType().equals(CollectTypeEnum.COMPANY.getCode())) {
            response.setReportPhone(collectBusiness.getPhone());
            response.setContactPhone(collectBusiness.getPhone());
        }

        //查询出这些发票绑定的责任列表
        QueryListRequest queryListRequest = new QueryListRequest();
        queryListRequest.setId(String.valueOf(claim.getId()));
        queryListRequest.setTenantId(String.valueOf(claim.getTenantId()));
        queryListRequest.setBizIdentityCode(claim.getBizIdentityCode());
        List<ClaimInvoice> claimInvoiceList = claimInvoiceService.getClaimInvoices(queryListRequest);

        if (claimInvoiceList == null) {
            throw new TpaBizException(BizErrorCode.NO_RECORD, "未录入发票！");
        }

        ClaimInvoice earliestInvoice = null;
        Date earliestDate = DateParserUtil.parseDate("2999-12-31");

        for (int i = 0; i < claimInvoiceList.size(); i++) {
            ClaimInvoice currentInvoice = claimInvoiceList.get(i);
            Date currentDate = currentInvoice.getVisitDate();
            if (currentDate == null) {
                continue;
            }

            if (!StringUtils.isEmpty(claim.getPolicyStartDate())){
                Date startDate = DateParserUtil.parseDate(claim.getPolicyStartDate());
                if (currentDate.before(startDate)) {
                    continue;
                }
            }

            if (!StringUtils.isEmpty(claim.getPolicyEndDate())) {
                Date endDate = DateParserUtil.parseDate(claim.getPolicyEndDate());
                if (currentDate.after(endDate)) {
                    continue;
                }
            }

            if (earliestDate == null || currentDate.before(earliestDate)) {
                // 当前日期更早，更新最早记录
                earliestInvoice = currentInvoice;
                earliestDate = currentDate;
            } else if (currentDate.equals(earliestDate)) {
                // 日期相同，保留下标较小的（由于我们是顺序遍历，所以最早的已经保留了）
                // 不需要做任何操作，因为 earliestInvoice 已经是下标较小的那个
            }
        }
        if (earliestInvoice == null) {
            throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "发票均没有就诊日期或不在保单范围内！");
        }

        Plan plan = null;
        if (claim.getPlanUuid() != null && !claim.getPlanUuid().isBlank()) {
            plan = liabilityInfoBasicService.getPlan(claim.getPlanUuid());
        }

        //根据责任推送设置，填入数据
        if (earliestInvoice.getRelateLiability() != null && !earliestInvoice.getRelateLiability().isBlank()) {
            String[] uuidList = earliestInvoice.getRelateLiability().split(",");

            List<String> liabilityUuidList = new ArrayList<>();

            for (String uuid : uuidList) {
                if (!liabilityUuidList.contains(uuid)) {
                    liabilityUuidList.add(uuid);
                }
            }

            if (!liabilityUuidList.isEmpty()) {
                LiabilityMapping mapping = liabilityMappingService.getByLiabilityAndVisitType(liabilityUuidList.get(0), VisitTypeEnum.getByValue(earliestInvoice.getVisitTypeCn()).getCode());

                if (mapping != null) {
                    response.setKindCode(mapping.getInsuranceCompanyCoverage());
                    response.setItemCode(mapping.getInsuranceCompanyLiability());
                    response.setSecondaryItemCode(mapping.getInsuranceCompanyLiabilitySub());
                } else {
                    mapping = liabilityMappingService.getByLiabilityAndVisitType(liabilityUuidList.get(0), null);
                    if (mapping != null) {
                        response.setKindCode(mapping.getInsuranceCompanyCoverage());
                        response.setItemCode(mapping.getInsuranceCompanyLiability());
                        response.setSecondaryItemCode(mapping.getInsuranceCompanyLiabilitySub());
                    }
                }
            }
        }

        List<String> liabilityUuidList = new ArrayList<>();
        if (!StringUtils.isEmpty(claim.getPolicyStartDate()) && !StringUtils.isEmpty(claim.getPolicyEndDate())) {
            //收集出住院时间
            List<String> hospitalStartDateList = claimInvoiceList.stream().filter(t -> !StringUtils.isEmpty(t.getHospitalPeriod()))
                    .map(t -> t.getHospitalPeriod().split(",")[0]).collect(Collectors.toList());
            response.setOutInsureTime(DateUtil.getFirstDayInPeriodString(claim.getPolicyStartDate(), claim.getPolicyEndDate(), hospitalStartDateList));

            //收集发票绑定责任列表
            for (ClaimInvoice invoice : claimInvoiceList) {
                if (invoice.getRelateLiability() == null || invoice.getRelateLiability().isBlank()) {
                    continue;
                }

                String[] uuidList = invoice.getRelateLiability().split(",");

                for (String uuid : uuidList) {
                    if (!liabilityUuidList.contains(uuid)) {
                        liabilityUuidList.add(uuid);
                    }
                }
            }
        }

        //出险经过
        if (!claimInvoiceList.isEmpty() && outInsure != null) {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");

            response.setOutInsureDetail(outInsure.getName() + "于" + format.format(earliestInvoice.getVisitDate()) + "在" + earliestInvoice.getHospitalName() + "就诊");
        }

        //出险地点
        response.setOutInsureAddress(claim.getOutInsureAddress());

        List<KindCode> kindCodeTypes = RegistConstant.yc_kindCodeList;
        List<KindCode> itemCodeTypes = RegistConstant.yc_itemCodeList;
        List<KindCode> secondaryItemCodeType = RegistConstant.yc_secondItemCodeList;

        response.setKindCodeTypes(kindCodeTypes);
        response.setItemCodeTypes(itemCodeTypes);
        response.setSecondaryItemCodeTypes(secondaryItemCodeType);

        response.setResponsibilityType("P"); // 普通责任

        //填充责任相关数据
//        if (plan != null) {
//            List<LiabilityConfig> liabilityList = liabilityInfoBasicService.getLiabilityList(plan.getId(), plan.getVersion());
//            for (LiabilityConfig liability : liabilityList) {
//                if (AccountTypeEnum.PUBLIC.getCode().equals(liability.getAccountType())) {
//                    response.setResponsibilityType("C"); // 公账责任
//                    break;
//                }
//            }
//        }
        if (!liabilityUuidList.isEmpty() && plan != null) {
            List<LiabilityConfig> liabilityList = liabilityInfoBasicService.queryLiabilityByUuidAndVersion(liabilityUuidList, plan.getVersion());
            for (LiabilityConfig liability : liabilityList) {
                if (AccountTypeEnum.PUBLIC.getCode().equals(liability.getAccountType())) {
                    response.setResponsibilityType("C"); // 公账责任
                    break;
                }
            }
        }

        //发票金额计算
        Map<String, BigDecimal> amountMap = new HashMap<>();
        BigDecimal totalAmountSum = claimInvoiceList.stream().map(ClaimInvoice::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        amountMap.put(MoneyTypeEnum.TOTAL_AMOUNT.getCode(), totalAmountSum);

        BigDecimal totalPoolingSum = claimInvoiceList.stream().map(ClaimInvoice::getBasicPoolingAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        amountMap.put(MoneyTypeEnum.NO_POOLING_AMOUNT.getCode(), totalAmountSum.subtract(totalPoolingSum));

        amountMap.put(MoneyTypeEnum.CUSTOM.getCode(), BigDecimal.ONE);

        response.setPayoutAmountMap(amountMap);

        return response;
    }


    @SimpleLog
    @Transactional(rollbackFor = Throwable.class)
    public String reportClaim(ReportCondition request) {
        YcRegistEntryRequest registRequest = new YcRegistEntryRequest();

        List<String> nullFields = FieldCheckingUtil.getNullFieldNames(request);
        if (!nullFields.isEmpty()) {
            throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "以下参数为空：" + nullFields);
        }

        Claim claim = claimService.getById(Long.valueOf(request.getClaimNo()));

        if (claim.getInsurerClaimNo() != null && !claim.getInsurerClaimNo().isBlank()) {
            // todo 这里要查询报案号
            throw new TpaBizException(BizErrorCode.INNER_PARAMETER_ERROR, "该赔案已成功报案，请勿重复提交");
        }
        // todo 然后这里要检查永城好管家渠道

        if (claim.getPolicyNo() == null || claim.getPolicyNo().isBlank() || claim.getPlanUuid() == null || claim.getPlanUuid().isBlank()) {
            throw new TpaBizException(BizErrorCode.INNER_PARAMETER_ERROR, "该赔案未绑定保单");
        }

        //查询出这些发票绑定的责任列表
        QueryListRequest queryListRequest = new QueryListRequest();
        queryListRequest.setId(String.valueOf(claim.getId()));
        queryListRequest.setTenantId(String.valueOf(claim.getTenantId()));
        queryListRequest.setBizIdentityCode(claim.getBizIdentityCode());
        List<ClaimInvoice> claimInvoiceList = claimInvoiceService.getClaimInvoices(queryListRequest);

        if (claimInvoiceList == null) {
            throw new TpaBizException(BizErrorCode.NO_RECORD, "未录入发票！");
        }

        for (ClaimInvoice claimInvoice : claimInvoiceList) {
            if (claimInvoice.getRelateLiability() == null || claimInvoice.getRelateLiability().isBlank()) {
                throw new TpaBizException(BizErrorCode.INNER_PARAMETER_ERROR, "发票未全部绑定责任");
            }
        }

        Policy policy = policyBasicService.getPolicyByPolicyNo(request.getPolicyNo());


        registRequest.setClaimNo(request.getClaimNo()); // 赔案号
        registRequest.setPolicyNo(StringUtils.isEmpty(policy.getParentPolicyNo()) ? request.getPolicyNo() : policy.getParentPolicyNo()); // 保单号
        registRequest.setDemageName(request.getOutInsureName()); // 出险人姓名
        registRequest.setDamagePersonCardType(request.getOutInsureIdentityType()); // 出险人证件类型
        registRequest.setDamageCardNo(request.getOutInsureIdentityNo()); // 出险人证件号码
        registRequest.setReportorName(request.getReportName()); // 报案人姓名
        registRequest.setReportorPhone(request.getReportPhone()); // 报案人电话
        registRequest.setLinkerName(request.getContactName()); // 联系人姓名
        registRequest.setLinkerPhone(request.getContactPhone()); // 联系人电话
        registRequest.setDamageAreaCode(request.getOutInsureAddress()); // 出险地点Code todo
        registRequest.setDamageAddress(request.getOutInsureAddress()); // 出险地点名称
        registRequest.setDamageRemark(request.getOutInsureDetail()); // 出险经过
        if ("被保险人".equals(request.getOutInsureIdentity())) { // 出险人身份
            registRequest.setDamagePersonType("0");
        } else if ("附属被保险人".equals(request.getOutInsureIdentity())) {
            registRequest.setDamagePersonType("1");
        } else {
            registRequest.setDamagePersonType("2");
        }
        registRequest.setDamagePersonSex(request.getOutInsureGender()); // 出险人性别
        registRequest.setDamagePersonAge(request.getOutInsureAge()); // 出险人年龄
        registRequest.setDamagePersondBirthday(request.getOutInsureBirthDay()); // 出险人出生日期
        SimpleDateFormat format = new SimpleDateFormat(" HH:mm:ss");
        registRequest.setDamageTime(request.getOutInsureTime() + format.format(new Date()));// 出险时间
        registRequest.setReportorDamagerRelation(request.getRelationType()); // 报案人与出险人关系

        for (KindCode kindCode : request.getKindCodeTypes()) {
            if (kindCode.getCode().equals(request.getKindCode())) {
                registRequest.setKindCode(kindCode.getCode()); // 条款代码
                registRequest.setKindName(kindCode.getName()); // 条款名称
            }
        }

        for (KindCode itemCode : request.getItemCodeTypes()) {
            if (itemCode.getCode().equals(request.getItemCode())) {
                registRequest.setItemCode(itemCode.getCode()); // 一级责任代码
                registRequest.setItemName(itemCode.getName()); // 一级责任名称
            }
        }

        for (KindCode secondItemCode : request.getSecondaryItemCodeTypes()) {
            if (secondItemCode.getCode().equals(request.getSecondaryItemCode())) {
                registRequest.setSecondItemCode(secondItemCode.getCode()); // 二级责任代码
                registRequest.setSecondItemName(secondItemCode.getName()); // 二级责任名称
            }
        }

        registRequest.setEstmtAmt(request.getPayoutAmountMap().get(request.getPayoutAmountType())); // 申请金额

        registRequest.setSicknessCausation(request.getDiseaseReason()); // 疾病原因
        registRequest.setFeeType(request.getResponsibilityType()); // 责任类型
        registRequest.setConsumerSeqNo(request.getResponsibilityType()); // 消费流水号


        //调用报案接口 todo 要做扩展
        CommonRegistResponse registResponse = ycRegistClaim(registRequest);

        //检查调用结果
        int code = registResponse == null ? 3 : registResponse.getCode();


        //暂时偷个懒，用tracklog算了

        if (code == 0) {
            //成功则存储保司报案号
            claim.setInsurerClaimNo(registResponse.getData());

            claimService.updateClaim(claim, false);
            claimTrackLogService.claimRecord(claim, BizContextUtils.getUser(), JsonUtil.toJson(request), JsonUtil.toJson(claim), OperationTypeEnum.REGIST, BizModelEnum.CLAIM_DETAIL, "赔案报案成功 " + claim.getInsurerClaimNo());

            return "赔案报案成功 " + claim.getInsurerClaimNo();
        } else {
            if (code == 3) {
                claimTrackLogService.claimRecord(claim, BizContextUtils.getUser(), JsonUtil.toJson(request), null, OperationTypeEnum.REGIST_FAIL, BizModelEnum.CLAIM_DETAIL, "网络异常等原因，报案失败");

                return "网络异常等原因,报案失败,请稍后再试!";
            } else {
                claimTrackLogService.claimRecord(claim, BizContextUtils.getUser(), JsonUtil.toJson(request), null, OperationTypeEnum.REGIST_FAIL, BizModelEnum.CLAIM_DETAIL, registResponse.getMessage());

                return "永诚报案失败!失败原因:" + registResponse.getMessage();
            }
        }
    }


    public CommonRegistResponse ycRegistClaim(YcRegistEntryRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> httpEntity = new HttpEntity<String>(JsonUtil.toJson(request),headers);
        String s = ycgjMasterUrl + "/yc-api/ycReportCase";
        ResponseEntity<CommonRegistResponse> response = null;
        try {
            log.info("永诚非好管家渠道进行案件报案：" + JsonUtil.toJson(request));
            response = this.restTemplate.postForEntity(s, httpEntity, CommonRegistResponse.class);
            log.info("永诚非好管家渠道进行案件报案请求响应：" + JsonUtil.toJson(response.getBody()));
            return response.getBody();
        } catch (Exception e) {
            log.error("永诚非好管家渠道进行案件报案接口异常：" + JsonUtil.toJson(request), e);
            return null;
        }
    }

    /**
     * 退回节点
     * @param request
     */
    @SimpleLog
    @Transactional(rollbackFor = Throwable.class)
    public void returnNodeBack(@RequestBody ClaimReturnRequest request){
        if( request.getClaimNumber() ==  null ){
            throw new TpaBizException("赔案号不能为空");
        }
        Long claimNumber = request.getClaimNumber();
        Claim claim = claimService.getById(claimNumber);

        if(claim == null){
            throw new TpaBizException("赔案不存在");
        }
        claimService.checkFinish(claim);
        claimService.checkUserId(claim);
        //不能是已经挂起的
        Boolean hangUpFlag = claimService.checkClaimHangUpStatus(claim);
        if (hangUpFlag) {
            throw new TpaBizException(BizErrorCode.CLAIM_HANG_UP, claim.getClaimNo());
        }

        ClaimStatusEnum fromStatus = ClaimStatusEnum.getByCode(claim.getStatus(),claim.getStatusSub());
        if(fromStatus == null){
            throw new TpaBizException("赔案状态不存在");
        }


        if(StringUtils.isBlank(request.getReasonData())){
            throw new TpaBizException("退回原因Data不能为空");
        }
        if(StringUtils.isBlank(request.getReasonCode())){
            throw new TpaBizException("退回原因Code不能为空");
        }
        if(StringUtils.isBlank(request.getDealerStrategy())){
            throw new TpaBizException("退回人策略不能为空");
        }
        if(StringUtils.isBlank(request.getTargetStage())){
            throw new TpaBizException("退回节点不能为空");
        }

        //校验赔案的状态
        if(!ClaimStatusEnum.Auditing.getCode().equals(claim.getStatus())){
            throw new TpaBizException("赔案状态不是审核中，不能退回");
        }

        //目标状态
        String targetStage =  request.getTargetStage();
        ClaimStageEnum toStage =    ClaimStageEnum.getByCode(targetStage);
        if(fromStatus.getStage().getOrder()<= toStage.getOrder()){
            throw new TpaBizException("退回节点不能大于当前节点");
        }
        if(ClaimStageEnum.PRE_EXAM.getCode().equals(targetStage)){
            //退回初审
            returnToPreExam(claim, request);
        }else if(ClaimStageEnum.SUBMITTING.getCode().equals(targetStage)){
            //退回录入
            returnToSubmiting(claim, request);
        }else if(ClaimStageEnum.INSPECTION.getCode().equals(targetStage)){
            //退回质检
            returnToInspect(claim,request);
        }else{
            throw new TpaBizException("退回节点不存在");
        }

        if(fromStatus == ClaimStatusEnum.Auditing){
            //如果是审核中，则清除理算
            SpringContextUtils.getBean(AdjustEngine.class).clearClaimAdjustment(claimNumber);
        }
    }


    private void  returnToInspect(Claim claim,ClaimReturnRequest request){
        ClaimTrackLog trackLog =    claimTrackLogService.queryLatestClaimRecord(claim.getId(),
                OperationTypeEnum.QUALITY_CHECK_COMPLETE  );
        if(trackLog == null){
            throw new TpaBizException("该案件退回阶段，没有经过人工处理，不能退回");
        }

        //操作人
        UserCheckVO returnDealer = getReturnDealer(trackLog, request);

        ClaimStatusEnum targetStatus =   ClaimStatusEnum.INSPECTIONING;

        //退回操作
        Map<String,Object> trackExtra = claimService.storeOperatorInfoJson(claim);
        trackExtra.put("reason",request.getReasonData());
        JSONObject reasonData = JSONObject.parseObject(  request.getReasonData());
        String reasonCn = reasonData.getString("reasonCn");

        String logContent = String.format("%s 将赔案退回质检，退回类型：%s，退回后处理人:%s\n退回原因:%s",BizContextUtils.getUser(),
                reasonCn, returnDealer.getUserName(), request.getRemark());
        claimTrackLogService.addActionRecord(claim, BizContextUtils.getUser(),trackExtra,
                OperationTypeEnum.BACK_NODE, logContent);

        //记录更新赔案状态
        claimService.setStatusField(claim,targetStatus);
        claim.setOperatorUserName(returnDealer.getUserName());
        claim.setOperatorUserId(returnDealer.getUserId());
        UserCheckVO.GroupListBean groupListBean =  returnDealer.getGroupList().get(0);
        claim.setOperatorOrgId(groupListBean.getGroupId().toString());
        claim.setOperatorOrgName(groupListBean.getGroupName());
//        claim.setInspectionOperatorName(returnDealer.getUserName());
        //更新状态
        claimRepository.update(claim);
        //开始通知tpa
        returnBackNotifyTpa(claim,targetStatus,returnDealer,logContent,request.getRemark());
    }


    /**
     * 退回录入
     * @param claim
     * @param request
     */
    private void returnToSubmiting(Claim claim,ClaimReturnRequest request){
        ClaimTrackLog trackLog =    claimTrackLogService.queryLatestClaimRecord(claim.getId(),
                OperationTypeEnum.WAIBAO_INPUT_COMPELTE, OperationTypeEnum.INPUT_MANUAL_COMPELTE );

        if(trackLog == null){
            throw new TpaBizException("该案件退回阶段，没有经过人工处理，不能退回");
        }
        OperationTypeEnum lastMode =    OperationTypeEnum.getByCode( trackLog.getType());
        ClaimStatusEnum targetStatus =null;
                //操作人
        UserCheckVO returnDealer = getReturnDealer(trackLog, request);
        if( lastMode == OperationTypeEnum.INPUT_MANUAL_COMPELTE){
            targetStatus =   ClaimStatusEnum.PUKANG_INPUTING;
        }else{
            targetStatus =   ClaimStatusEnum.ADDING_INPUT;
        }

        //退回操作
        Map<String,Object> trackExtra = claimService.storeOperatorInfoJson(claim);
        trackExtra.put("reason",request.getReasonData());
        JSONObject reasonData = JSONObject.parseObject(  request.getReasonData());
        String reasonCn = reasonData.getString("reasonCn");

        String logContent = String.format("%s 将赔案退回录入，退回类型：%s，退回后处理人:%s\n退回原因:%s",BizContextUtils.getUser(),
                reasonCn, returnDealer.getUserName(), request.getRemark());
        claimTrackLogService.addActionRecord(claim, BizContextUtils.getUser(),trackExtra,

                OperationTypeEnum.BACK_NODE, logContent);

        //记录更新赔案状态
        claimService.setStatusField(claim,targetStatus);
        claim.setOperatorUserName(returnDealer.getUserName());
        claim.setOperatorUserId(returnDealer.getUserId());
        UserCheckVO.GroupListBean groupListBean =  returnDealer.getGroupList().get(0);
        claim.setOperatorOrgId(groupListBean.getGroupId().toString());
        claim.setOperatorOrgName(groupListBean.getGroupName());
//        claim.setSubmittingOperatorName(returnDealer.getUserName());
        //更新状态
        claimRepository.update(claim);
        //开始通知tpa
        returnBackNotifyTpa(claim,targetStatus,returnDealer,logContent,request.getRemark());
    }

    /**
     * 退回初审
     * @param claim
     * @param request
     */
    private void returnToPreExam( Claim claim,ClaimReturnRequest request){
        ClaimTrackLog trackLog =    claimTrackLogService.queryLatestClaimRecord(claim.getId(),
                OperationTypeEnum.PRE_CHECK_MANUAL_COMPLETE  );
        if(trackLog == null){
            throw new TpaBizException("该案件退回阶段，没有经过人工处理，不能退回");
        }

        //操作人
        UserCheckVO returnDealer = getReturnDealer(trackLog, request);

        ClaimStatusEnum targetStatus =   ClaimStatusEnum.PRE_ADUITING;

        //退回操作
        Map<String,Object> trackExtra = claimService.storeOperatorInfoJson(claim);
        trackExtra.put("reason",request.getReasonData());
        JSONObject reasonData = JSONObject.parseObject(  request.getReasonData());
        String reasonCn = reasonData.getString("reasonCn");

        String logContent = String.format("%s 将赔案退回初审，退回类型：%s，退回后处理人:%s\n退回原因:%s",BizContextUtils.getUser(),
                reasonCn, returnDealer.getUserName(), request.getRemark());
        claimTrackLogService.addActionRecord(claim, BizContextUtils.getUser(),trackExtra,
                OperationTypeEnum.BACK_NODE, logContent);

        //记录更新赔案状态
        claimService.setStatusField(claim,targetStatus);
        claim.setOperatorUserName(returnDealer.getUserName());
        claim.setOperatorUserId(returnDealer.getUserId());
        UserCheckVO.GroupListBean groupListBean =  returnDealer.getGroupList().get(0);
        claim.setOperatorOrgId(groupListBean.getGroupId().toString());
        claim.setOperatorOrgName(groupListBean.getGroupName());
//        claim.setPreExamOperatorName(returnDealer.getUserName());
        //更新状态
        claimRepository.update(claim);
         //开始通知tpa
        returnBackNotifyTpa(claim,targetStatus,returnDealer,logContent,request.getRemark());
    }

    private void returnBackNotifyTpa(Claim claim,ClaimStatusEnum targetStatus,
                                     UserCheckVO returnDealer,String logContent, String returnBackRemark){
        UserCheckVO.GroupListBean groupListBean =  returnDealer.getGroupList().get(0);


        Long claimId = claim.getId();
        TpaSubmitClaimResult tpaSubmitClaimRequest = new TpaSubmitClaimResult();
        tpaSubmitClaimRequest.setClaimNumber(claimId);
        tpaSubmitClaimRequest.setEventTime(System.currentTimeMillis());
        tpaSubmitClaimRequest.setEventType(Integer.valueOf(EventType.saas通知tpa同步数据状态和日志.getCode()));
        tpaSubmitClaimRequest.setClaimInfo(toTpaChangeService.toSyncVo(claimId));

        EventOtherRequest eventOtherRequest = new EventOtherRequest();
        tpaSubmitClaimRequest.setEventOtherRequest(eventOtherRequest);

        eventOtherRequest.setClaimStatus(Integer.valueOf(targetStatus.getCode()));

        //不用分配人
        eventOtherRequest.setAssignTag(true);
        eventOtherRequest.setAssignStage(targetStatus.getStage().getCode());
        eventOtherRequest.setAssignStrategy("2");
        eventOtherRequest.setAssignOperatorName(returnDealer.getUserName());
        eventOtherRequest.setAssignOperatorId(returnDealer.getUserId());
        eventOtherRequest.setAssignOperatorGroupId(groupListBean.getGroupId().toString());
        eventOtherRequest.setAssignOperatorGroupName(groupListBean.getGroupName());
        //记录operatorLog
        TpaAddLogRequest tpaAddLogRequest = new TpaAddLogRequest();
        tpaAddLogRequest.setClaimNumber( claim.getId());
        tpaAddLogRequest.setOperation(logContent);
        tpaAddLogRequest.setRemark(returnBackRemark);
        tpaAddLogRequest.setCreateBy(BizContextUtils.getUser());
        tpaAddLogRequest.setCreateTime( System.currentTimeMillis());
        eventOtherRequest.getOperationLogList().add(tpaAddLogRequest);

        claimService.fillReportLog(claim, eventOtherRequest, targetStatus, returnDealer.getUserName(), null, new Date());
        commonLogService.addLogASync(claimId.toString(), CommonLogType.TO_TPA_LOG, "notifyTpaChangeStatus,request:{}",
                JSONObject.toJSONString(tpaSubmitClaimRequest));
        ApiResult<Map<String, Object>> remoteRs = tpaDataSyncFeign.submitClaimResult(tpaSubmitClaimRequest);
        //log.info("saas通知tpa初审完成不自动分配:{}",remoteRs);
        commonLogService.addClaimLogAsync(claimId, CommonLogType.TO_TPA_LOG, "notifyTpaChangeStatus, cremoteRs:{}",
                remoteRs);

        if (!remoteRs.isSuccess()) {
            throw new RuntimeException("returnToPreExam，通知tpa失败:" + remoteRs.getMessage());
        }
    }

    private UserCheckVO getReturnDealer(ClaimTrackLog trackLog,ClaimReturnRequest request){

        String dealerStrategy = request.getDealerStrategy();
        UserCheckVO checkUserVO=null;
        if(StringUtils.equals("1", dealerStrategy)){
            //手工指定
            if(StringUtils.isBlank(request.getAssignDealerName())){
                throw new TpaBizException("指定人员为空");
            }
            UserCheckRequest userCheckRequest = new UserCheckRequest();
            userCheckRequest.setUserName(request.getAssignDealerName());
            userCheckRequest.setStage(request.getTargetStage());
            ApiResult<UserCheckVO>  userCheckVOApiResult =  tpaDataSyncFeign.queryUserGroupInfo(userCheckRequest);
            if(!userCheckVOApiResult.isSuccess()){
                throw new TpaBizException("查询人员信息失败:"+userCheckVOApiResult.getMessage());
            }
            checkUserVO=    userCheckVOApiResult.getData();
            if( checkUserVO == null){
                throw new TpaBizException("指定人员不存在");
            }

        }else{
            String extraStore  =  trackLog.getExtraStore();
            if(StringUtils.isBlank(extraStore)){
                throw new TpaBizException("无法定位操作人人信息");
            }
            JSONObject userInfo =     JSONObject.parseObject(extraStore);
            /**
             *    rs.put("operName",operName);
             *         rs.put("operId",operId);
             *         rs.put("operOrgName",operOrgName);
             *         rs.put("operOrgId",operOrgId);
             */
            String userName = userInfo.getString("operName");
            if(StringUtils.isBlank(userName)){
                throw new TpaBizException("无法定位原处理人信息");
            }
            UserCheckRequest userCheckRequest = new UserCheckRequest();
            userCheckRequest.setUserName(userName);
            userCheckRequest.setStage(request.getTargetStage());
            ApiResult<UserCheckVO>  userCheckVOApiResult =  tpaDataSyncFeign.queryUserGroupInfo(userCheckRequest);
            if(!userCheckVOApiResult.isSuccess()){
                throw new TpaBizException("查询原处理人员信息失败:"+userCheckVOApiResult.getMessage());
            }
            checkUserVO=    userCheckVOApiResult.getData();
            if( checkUserVO == null){
                throw new TpaBizException("处理人员不存在");
            }
        }
        if(PkListUtil.isEmpty(checkUserVO.getGroupList())){
            throw new TpaBizException("处理人员没有分配组");
        }
        return  checkUserVO;

    }



}
