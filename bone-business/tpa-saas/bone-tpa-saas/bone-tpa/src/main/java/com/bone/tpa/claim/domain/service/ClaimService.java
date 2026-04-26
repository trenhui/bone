package com.bone.tpa.claim.domain.service;

import cn.hutool.core.date.DateUtil;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bone.core.id.IdGenerator;
import com.bone.core.result.QueryParam;
import com.bone.core.util.JsonUtil;
import com.bone.metadata.sdk.MetadataFetchEngine;
import com.bone.metadata.sdk.SdkPropertyConfig;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.metadata.sdk.dto.MetaFieldDTO;
import com.bone.core.util.PkListUtil;
import com.bone.core.util.SpringContextUtils;
import com.bone.tpa.api.ApiResult;
import com.bone.tpa.api.enums.CollectTypeEnum;
import com.bone.tpa.api.enums.EventType;
import com.bone.tpa.api.enums.HangUpStatus;
import com.bone.tpa.api.enums.HintMsgType;
import com.bone.tpa.api.vo.ClaimDetailSyncVO;
import com.bone.tpa.claim.application.enums.BizModelEnum;
import com.bone.tpa.claim.application.enums.CommonLogType;
import com.bone.tpa.claim.application.request.ClaimHangupRequest;
import com.bone.tpa.claim.application.request.ClaimRejectRequest;
import com.bone.tpa.claim.application.request.QueryListRequest;
import com.bone.tpa.claim.application.response.ClaimDetailObject;
import com.bone.tpa.claim.application.response.CollectInfo;
import com.bone.tpa.claim.application.response.OutInsureInfo;
import com.bone.tpa.claim.domain.ext.strategy.YongChengStrategy;
import com.bone.tpa.core.synctask.ThreadExecutorUtil;
import com.bone.tpa.core.util.ExtraStoreUtil;
import com.bone.tpa.facade.request.*;
import com.bone.tpa.facade.vo.UserCheckVO;
import com.bone.tpa.hook.impl.claim.YongchengPageSaveValidHook;
import com.bone.tpa.hook.vo.ClaimUpdateHookParam;
import com.bone.tpa.sdk.dao.ClaimHintMsgRespository;
import com.bone.tpa.sdk.dao.ClaimInvoiceRepository;
import com.bone.tpa.sdk.dao.ClaimRepository;
import com.bone.tpa.claim.sync.ClaimToTpaChangeService;
import com.bone.tpa.claim.util.BizFieldUtil;
import com.bone.core.util.BizContext;
import com.bone.core.util.BizContextUtils;
import com.bone.tpa.facade.feign.TpaDataSyncFeign;
import com.bone.tpa.hook.ClaimHookUtil;
import com.bone.tpa.hook.inter.ClaimPageSaveAfterHook;
import com.bone.tpa.intelligent.adjustment.model.AdjustConclusion;
import com.bone.tpa.intelligent.adjustment.model.AdjustResult;
import com.bone.tpa.intelligent.adjustment.service.AdjustmentRecordService;
import com.bone.tpa.sdk.adjustment.model.AdjustmentResult;
import com.bone.tpa.sdk.claim.enums.*;
import com.bone.tpa.sdk.claim.exception.TpaBizException;
import com.bone.tpa.sdk.claim.model.*;
import com.bone.tpa.sdk.dao.ClaimTrackLogRepository;
import com.bone.tpa.sdk.dao.biz.ClaimFlowConfigBiz;
import com.bone.tpa.sdk.masterdb.mapper.TpaPersonClaimMapper;
import com.bone.tpa.sdk.masterdb.model.TpaPersonClaim;
import com.bone.tpa.sdk.service.AdjustmentRecordBasicService;
import com.bone.tpa.sdk.service.TimeLogService;
import com.bone.tpa.sdk.vo.ClaimFlowConfigVO;
import com.bone.tpa.sdk.vo.PreCheckConfigVO;
import com.bone.tpa.task.impl.CheckSameInvoiceTrigger;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Period;
import java.util.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.bone.tpa.sdk.constants.BizConstant.MAGIC_CLAIM_STATUS_NAME;

/**
 * ss_claim Service 接口
 *
 * @author 0
 */
@Service
@Slf4j
public class ClaimService {
    @Autowired
    private ClaimRepository claimRepository;

    @Autowired
    private ClaimStakeholderService claimStakeholderService;

    @Autowired
    private ClaimInvoiceService claimInvoiceService;
    @Autowired
    private ClaimTrackLogRepository claimTrackLogRepository;

    @Autowired
    private ClaimTrackLogService claimTrackLogService;

    @Autowired
    private AdjustmentRecordService adjustmentRecordService;

    @Autowired
    private AdjustmentRecordBasicService adjustmentRecordBasicService;

    @Autowired
    private TpaDataSyncFeign tpaDataSyncFeign;

    @Autowired
    private ClaimToTpaChangeService claimToTpaChangeService;

    @Autowired
    private ClaimHintMsgRespository hintMsgRespository;

    @Autowired
    private ClaimToTpaChangeService toTpaChangeService;

    @Autowired
    private CommonLogService commonLogService;

    @Autowired
    private ClaimInvoiceRepository claimInvoiceRepository;

    @Autowired
    private MetadataFetchEngine metadataFetchEngine;

    @Autowired
    private SdkPropertyConfig sdkPropertyConfig;


    @Value("${spring.profiles.active}")
    private String environment;

    @Autowired
    private ClaimTrackLogService trackLogService;
    @Autowired
    private ClaimFlowConfigBiz claimFlowConfigBiz;


    @Autowired
    private TimeLogService timeLogService;

    @Autowired
    private TpaPersonClaimMapper tpaPersonClaimMapper;



    public void notifyTpaChangeStatus(Claim claim, Date eventTime,
                                         ClaimStatusEnum claimStatusEnum,
                                        List<ReportLogRequest> reportLogRequestList,
                                         String operaotr,
                                         String logContent,String logRemark,
                                      boolean updateDetail){
        Long claimId = claim.getId();
        TpaSubmitClaimResult request = new TpaSubmitClaimResult();
        request.setClaimNumber(claimId);
        request.setEventTime(eventTime.getTime());
        request.setEventType(Integer.valueOf(EventType.saas通知tpa同步数据状态和日志.getCode()));
        EventOtherRequest eventOtherRequest = new EventOtherRequest();
        request.setEventOtherRequest(eventOtherRequest);
        if(updateDetail){
            request.setClaimInfo(toTpaChangeService.toSyncVo(claimId));
        }

        if( claimStatusEnum != null){
            eventOtherRequest.setClaimStatus(Integer.valueOf(claimStatusEnum.getCode()));
        }
        //不用分配人
        eventOtherRequest.setAssignTag(false);
        //传时效
        eventOtherRequest.setLimitHour(claim.getLimitHour());
        //登记log
        if( StringUtils.isBlank(operaotr)){
            operaotr = "System";
        }
        if(StringUtils.isNotBlank(logContent)){
            TpaAddLogRequest tpaAddLogRequest = new TpaAddLogRequest();
            tpaAddLogRequest.setClaimNumber( claim.getId());
            tpaAddLogRequest.setOperation(logContent);
            tpaAddLogRequest.setRemark(logRemark);
            tpaAddLogRequest.setCreateBy(operaotr);
            tpaAddLogRequest.setCreateTime( eventTime.getTime());
            eventOtherRequest.getOperationLogList().add(tpaAddLogRequest);
        }


        fillReportLog(claim, eventOtherRequest, claimStatusEnum, operaotr, reportLogRequestList, eventTime);
        commonLogService.addLogASync(claimId.toString(), CommonLogType.TO_TPA_LOG, "notifyTpaChangeStatus,request:{}",
                JSONObject.toJSONString(request));
        ApiResult<Map<String, Object>> remoteRs = tpaDataSyncFeign.submitClaimResult(request);
        //log.info("saas通知tpa初审完成不自动分配:{}",remoteRs);
        commonLogService.addClaimLogAsync(claimId, CommonLogType.TO_TPA_LOG, "notifyTpaChangeStatus, cremoteRs:{}",
                remoteRs);

        if (!remoteRs.isSuccess()) {
            throw new RuntimeException("notifyTpaChangeStatus，通知tpa失败:" + remoteRs.getMessage());
        }

    }


    public void fillReportLog(Claim claim, EventOtherRequest eventOtherRequest ,
                                ClaimStatusEnum claimStatusEnum,
                                String operaotr,
                                List<ReportLogRequest> reportLogRequestList,
                                Date eventTime){
        if(PkListUtil.isNotEmpty(reportLogRequestList)){
            eventOtherRequest.setReportLogRequest(reportLogRequestList);
        }else{
            if(claimStatusEnum == null){
                return;
            }
            String dateFormate = "yyyy-MM-dd HH:mm:ss";
            ReportLogRequest reportLogRequest = new ReportLogRequest();
            reportLogRequest.setStage( (claimStatusEnum.getStage().getCode()));
            reportLogRequest.setOperatorName(operaotr);

            if( claimStatusEnum == ClaimStatusEnum.PRE_ADUITING){
                //初审中
                reportLogRequest.setStatus("初审中");
                reportLogRequest.setStartTime(DateUtil.format(eventTime,dateFormate));
                reportLogRequest.setEndTime("");
                eventOtherRequest.getReportLogRequest().add(reportLogRequest);

            }
            if( claimStatusEnum == ClaimStatusEnum.COMPLETE_PRE_ADUIT){
                //已初审
                reportLogRequest.setStatus("已初审");
                reportLogRequest.setStartTime(null);
                reportLogRequest.setEndTime(DateUtil.format(eventTime,dateFormate));
                eventOtherRequest.getReportLogRequest().add(reportLogRequest);

            }

            //录入状态
            if( claimStatusEnum == ClaimStatusEnum.PUKANG_INPUTING
                    || claimStatusEnum == ClaimStatusEnum.WAIBAO_INPUTING
                    || claimStatusEnum == ClaimStatusEnum.ORC_INPUTING){
                //普康录入中，外包录入中
                reportLogRequest.setStatus("录入中");
                if(claimStatusEnum == ClaimStatusEnum.ORC_INPUTING){
                    reportLogRequest.setStatus("接口录入中");
                }
                reportLogRequest.setStartTime(DateUtil.format(eventTime,dateFormate));
                reportLogRequest.setEndTime("");
                eventOtherRequest.getReportLogRequest().add(reportLogRequest);
            }
            if( claimStatusEnum == ClaimStatusEnum.INPUT_COMPLETE){
                //录入完成
                reportLogRequest.setStatus("已录入");
                reportLogRequest.setStartTime(null);
                reportLogRequest.setEndTime(DateUtil.format(eventTime,dateFormate));
                eventOtherRequest.getReportLogRequest().add(reportLogRequest);
            }
            //质检状态
            if( claimStatusEnum == ClaimStatusEnum.INSPECTIONING){
                //质检中
                reportLogRequest.setStatus("质检中");
                reportLogRequest.setStartTime(DateUtil.format(eventTime,dateFormate));
                reportLogRequest.setEndTime("");
                eventOtherRequest.getReportLogRequest().add(reportLogRequest);

            }
            if( claimStatusEnum == ClaimStatusEnum.COMPLETE_INSPECTION){
                //质检完成
                reportLogRequest.setStatus("已质检");
                reportLogRequest.setStartTime(null);
                reportLogRequest.setEndTime(DateUtil.format(eventTime,dateFormate));
                eventOtherRequest.getReportLogRequest().add(reportLogRequest);

            }


            //审核状态
            if( claimStatusEnum == ClaimStatusEnum.Auditing){
                //审核中
                reportLogRequest.setStatus("审核中");
                reportLogRequest.setStartTime(DateUtil.format(eventTime,dateFormate));
                reportLogRequest.setEndTime("");
                eventOtherRequest.getReportLogRequest().add(reportLogRequest);

            }


            if( claimStatusEnum == ClaimStatusEnum.COMPLETE_AUDIT){
                //审核完成
                //判断之前的一个节点是啥event

                List<ClaimTrackLog>  trackLogs =   trackLogService.queryClaimRecord(claim.getId(),
                        OperationTypeEnum.APPROVE_MANUAL_COMPLETE,
                        OperationTypeEnum.REVIEW_COMPLETE);
                if(PkListUtil.isNotEmpty(trackLogs)){
                    trackLogs.sort((t1,t2)->t1.getId().compareTo(t2.getId()));
                    ClaimTrackLog lastTrackLog = PkListUtil.last(trackLogs);
                    //最后一个节点是审核完成
                    if(lastTrackLog.getType().equals(OperationTypeEnum.APPROVE_MANUAL_COMPLETE.getCode())){
                        reportLogRequest.setStatus("已审核");
                        reportLogRequest.setStage(ClaimStatusEnum.Auditing.getStage().getCode());
                        reportLogRequest.setStartTime(null);
                        reportLogRequest.setEndTime(DateUtil.format(eventTime,dateFormate));
                        eventOtherRequest.getReportLogRequest().add(reportLogRequest);
                    }
                    if(lastTrackLog.getType().equals(OperationTypeEnum.REVIEW_COMPLETE.getCode())){
                        reportLogRequest.setStatus("已复核");
                        reportLogRequest.setStage(ClaimStatusEnum.AuditingReviewIng.getStage().getCode());
                        reportLogRequest.setStartTime(null);
                        reportLogRequest.setEndTime(DateUtil.format(eventTime,dateFormate));
                        eventOtherRequest.getReportLogRequest().add(reportLogRequest);
                    }
                }


            }

        }

    }

    public  Map<String,Object>  storeOperatorInfoJson(Claim claim){
        String operName = claim.getOperatorUserName();
        String operId = claim.getOperatorUserId();
        String operOrgName = claim.getOperatorOrgName();
        String operOrgId = claim.getOperatorOrgId();

        Map<String,Object> rs = new HashMap<>();
        rs.put("operName",operName);
        rs.put("operId",operId);
        rs.put("operOrgName",operOrgName);
        rs.put("operOrgId",operOrgId);

        return rs;
    }
    public void setStatusField(Claim upDto,ClaimStatusEnum statusEnum) {
        upDto.setStatus(statusEnum.getCode());
        upDto.setStatusSub(statusEnum.getSubStatus());
        upDto.setStage(statusEnum.getStage().getCode());
    }
    public void clearOperator(Claim upDto){
        upDto.setOperatorUserName("");
        upDto.setOperatorUserId("");
        upDto.setOperatorOrgId("");
        upDto.setOperatorOrgName("");
    }

    @Transactional
    public Claim createClaim(Claim claim) {
        try {
            //1、校验赔案
//            claimValidationExt.validate(claim);
//
            //2、生成赔案编号
            String claimNumber = IdGenerator.generateSequenceNo(claim.getBatchNo(), false, 4);
            claim.setClaimNo(claimNumber);

            log.info("生成赔案号：" + claimNumber);

            //3、赔案创建前扩散
            //claim = beforeCreateClaimExt.beforeCreate(claim);

            //4、赔案持久化
            claimRepository.save(claim);
            log.info("createClaim成功：" + JsonUtil.toJson(claim));

            //5、发送通知
            //claimCreatedNotificationExt.notifyClaimCreated(claim);
            log.info("理赔案件发送通知成功，ID: {}", claim.getId());

            //6、同步到外部系统
            //claimSyncExternalSystemExt.syncClaimToExternalSystem(claim);
            log.info("理赔案件同步到外部系统成功，ID: {}", claim.getId());

            //7、增加操作记录
            claimTrackLogService.claimRecord(claim,BizContextUtils.getUser(), null, JsonUtil.toJson(claim),
                    OperationTypeEnum.CREATE, BizModelEnum.CLAIM_DETAIL);

            return claim;
        } catch (Exception e) {
            log.error("理赔案件创建失败", e);
            throw new TpaBizException(BizErrorCode.CREATE_CLAIM_ERROR);
        }
    }

    @Transactional(rollbackFor = Throwable.class)
    public void updateClaim(Claim claim, Boolean needOld, String... remark) {
        if (needOld) {
            Claim oldClaim = claimRepository.findById(claim.getId());
            claimRepository.update(claim);
            claimTrackLogService.claimRecord(claim,BizContextUtils.getUser(), JsonUtil.toJson(oldClaim),
                    JsonUtil.toJson(claim), OperationTypeEnum.UPDATE, BizModelEnum.CLAIM_DETAIL , remark);
        } else {
            claimRepository.update(claim);
            claimTrackLogService.claimRecord(claim,BizContextUtils.getUser(), null,
                    JsonUtil.toJson(claim), OperationTypeEnum.UPDATE, BizModelEnum.CLAIM_DETAIL , remark);
        }

    }


    public void batchUpdateClaim(List<Claim> claimList, OperationTypeEnum operationType) {
        List<Long> idList = claimList.stream().map(Claim::getId).toList();
        List<Claim> oldClaimList = claimRepository.findById(idList);
        Map<Long, Claim> oldClaimMap = oldClaimList.stream().collect(Collectors.toMap(Claim::getId, t -> t));

        claimRepository.saveBatch(claimList);
        for (Claim claim : claimList) {
            claimTrackLogService.claimRecord(claim,BizContextUtils.getUser(), JsonUtil.toJson(oldClaimMap.get(claim.getId())),
                    JsonUtil.toJson(claim), operationType, BizModelEnum.CLAIM_DETAIL , claim.getClaimNo());
        }
    }

    /**
     * 提交赔案的更新
     * 包括赔案表的更新和子表的更新
     *
     * @param claimDetailObject
     */


    /**
     * 根据查询选项获取表格信息
     * @param request
     * @return
     */
    public List<Claim> getByQueryParam(QueryListRequest request) {
        request.getQueryParams().add(new QueryParam("tenantId", request.getTenantId()));
        request.getQueryParams().add(new QueryParam("relatedId", request.getId()));

        return claimRepository.queryByCondition(request.getQueryParams(), request.getSortingFields(), (request.getPageNo() - 1) * request.getPageSize(), request.getPageSize(),
                "ss_claim", request.getBizIdentityCode()).getData();
    }


    /**
     * 根据id获取赔案表的信息
     * @param claimId
     * @return
     */
    public Claim getById(Long claimId) {
        return claimRepository.findById(claimId);
    }

    /**
     * 根据id获取赔案表的信息
     * @param claimId
     * @return
     */
    public List<Claim> getByIdList(List<Long> claimId) {
        return claimRepository.findById(claimId);
    }

    /**
     * 组合赔案对象
     *
     * @param claim           赔案对象
     * @param stakeholderList 相关人列表
     */
    public ClaimDetailObject processClaimDetail(Claim claim, List<ClaimStakeholder> stakeholderList) {
        BizContext bizContext = BizContextUtils.get();
        log.warn(bizContext.getBizIdentity());

        ClaimDetailObject claimDetailObject = new ClaimDetailObject();

        //赔案基础信息
        claimDetailObject.setId(claim.getId());
        claimDetailObject.setClaimNo(claim.getClaimNo());
        claimDetailObject.setClaimDetailUuid(claim.getClaimDetailUuid());
        claimDetailObject.setBatchNo(claim.getBatchNo());
        claimDetailObject.setSlipPersonPsc(claim.getSlipPersonPsc());
        claimDetailObject.setSerialNumber(claim.getSerialNumber());
        claimDetailObject.setSignSystemSource(String.valueOf(claim.getSignSystemSource()));
        claimDetailObject.setStatus(ClaimStatusEnum.getByCode(claim.getStatus(), claim.getStatusSub()).getValue());
        claimDetailObject.setStage(ClaimStageEnum.getByCode(claim.getStage()).getValue());
//        claimDetailObject.setEmergency(claim.getEmergency());
        claimDetailObject.setSource(claim.getSource());
        claimDetailObject.setBizIdentityCode(claim.getBizIdentityCode());
        claimDetailObject.setTenantId(claim.getTenantId());
        claimDetailObject.setCreateTime(claim.getCreateTime());
        claimDetailObject.setUpdateTime(claim.getUpdateTime());

        claimDetailObject.setHangUpStatus(claim.getHangUpStatus());
        claimDetailObject.setHangUpType(claim.getHangUpType());
        claimDetailObject.setVipSign(claim.getVipSign());
        claimDetailObject.setVipSignCn(claim.getVipSignCn());
        claimDetailObject.setSourceCode(claim.getSourceCode());
        claimDetailObject.setSourceCodeCn(claim.getSourceCodeCn());

        //保单信息
        claimDetailObject.setPolicyNo(claim.getPolicyNo());
        claimDetailObject.setPolicyStartDate(claim.getPolicyStartDate());
        claimDetailObject.setPolicyEndDate(claim.getPolicyEndDate());
        claimDetailObject.setPolicyAttribute(claim.getPolicyAttribute());
        claimDetailObject.setPlanUuid(claim.getPlanUuid());

        //赔案签收信息
        claimDetailObject.setBizType(claim.getBizType());
        claimDetailObject.setProcessType(claim.getProcessType());
        claimDetailObject.setSeverenessLevel(claim.getSeverenessLevel());
        claimDetailObject.setCountingResult(claim.getCountingResult());
        claimDetailObject.setImageUploadFlag(claim.getImageUploadFlag());
        claimDetailObject.setImageCount(claim.getImageCount());
        claimDetailObject.setImageUploadCount(claim.getImageUploadCount());
        claimDetailObject.setImageUploadTime(claim.getImageUploadTime());
        claimDetailObject.setImageUploader(claim.getImageUploader());

        //赔案三方信息
        claimDetailObject.setInsuranceName(claim.getInsuranceName());
        claimDetailObject.setBranchName(claim.getBranchName());
        claimDetailObject.setInsureName(claim.getInsureName());
        claimDetailObject.setInsurerClaimNo(claim.getInsurerClaimNo());
        claimDetailObject.setInsurerRequestNo(claim.getInsurerRequestNo());
        claimDetailObject.setInsurerBatchNo(claim.getInsurerBatchNo());
        claimDetailObject.setInsurerPolicyNo(claim.getInsurerPolicyNo());
        claimDetailObject.setInsurerReceiptNo(claim.getInsurerReceiptNo());

        //操作人信息
        claimDetailObject.setOperatorOrgId(claim.getOperatorOrgId());
        claimDetailObject.setOperatorOrgName(claim.getOperatorOrgName());
        claimDetailObject.setOperatorUserId(claim.getOperatorUserId());
        claimDetailObject.setOperatorUserName(claim.getOperatorUserName());


        ClaimHintMsg claimHintMsg = querySyncHint(claim.getId(), HintMsgType.CLAIM_DETAIL, claim.getId());
        if (claimHintMsg != null) {
            claimDetailObject.setSyncHintMsg(JsonUtil.fromJson(claimHintMsg.getData(), new TypeReference<Map<String, String>>(){}));
        }

        //出险信息
        OutInsureInfo outInsureInfo = new OutInsureInfo();
        outInsureInfo.setOutInsureType(claim.getOutInsureType());
        outInsureInfo.setOutInsureTypeCn(claim.getOutInsureTypeCn());
        outInsureInfo.setOutInsureTime(claim.getOutInsureTime());
        outInsureInfo.setOutInsureRegion(claim.getOutInsureRegion());
        outInsureInfo.setOutInsureAddress(claim.getOutInsureAddress());
        claimHintMsg = querySyncHint(claim.getId(), HintMsgType.OUT_INSURE_INFO, claim.getId());
        if (claimHintMsg != null) {
            outInsureInfo.setSyncHintMsg(JsonUtil.fromJson(claimHintMsg.getData(), new TypeReference<Map<String, String>>(){}));
        }
        claimDetailObject.setOutInsureInfo(outInsureInfo);

        //领款人类型
        CollectInfo collectInfo = new CollectInfo();
        collectInfo.setCollectType(claim.getCollectType());
        claimDetailObject.setCollectInfo(collectInfo);

        //获取全部的专属字段
        List<MetaFieldDTO> bizFieldList = metadataFetchEngine.getAllBizIdentityField(sdkPropertyConfig.getAppcode(),
                BizModelEnum.CLAIM_DETAIL.getTableName(), claim.getBizIdentityCode());

        //处理成 模型名, 字段名 的形式
        if (bizFieldList != null && !bizFieldList.isEmpty()) {
            Map<String, List<String>> bizFieldMap = bizFieldList.stream()
                    .collect(Collectors.groupingBy(
                            dto -> Optional.ofNullable(dto.getFieldModelCode()).orElse("DEFAULT_KEY"),
                            Collectors.mapping(MetaFieldDTO::getFieldName, Collectors.toList())
                    ));
            //赔案专属字段
            claimDetailObject.getExtraProperties().putAll(BizFieldUtil.fetchExtraBizField(claim.getExtraProperties(), bizFieldMap, BizModelEnum.CLAIM_DETAIL));
            outInsureInfo.getExtraProperties().putAll(BizFieldUtil.fetchExtraBizField(claim.getExtraProperties(), bizFieldMap, BizModelEnum.OUT_INSURE_INFO));
            collectInfo.getExtraProperties().putAll(BizFieldUtil.fetchExtraBizField(claim.getExtraProperties(), bizFieldMap, BizModelEnum.COLLECT_INFO));
        }
        //加载claim Object 的选项集复杂字段
        String claimObjectExtraStore =  ExtraStoreUtil.fillExtraConfigForModel(claimDetailObject, claim.getExtraStore());
        claimDetailObject.setExtraStore(ExtraStoreUtil.jsonToMap(claimObjectExtraStore));
        String outInsureInfoExtraStore=  ExtraStoreUtil.fillExtraConfigForModel(outInsureInfo, claim.getExtraStore());
        outInsureInfo.setExtraStore(ExtraStoreUtil.jsonToMap(outInsureInfoExtraStore));

        String collectInfoExtrStore =  ExtraStoreUtil.fillExtraConfigForModel(collectInfo, claim.getExtraStore());
        collectInfo.setExtraStore(ExtraStoreUtil.jsonToMap(collectInfoExtrStore));
        //增加相关人信息
        claimStakeholderService.processClaimStakeHolder(claimDetailObject, stakeholderList,claim);

        //增加保单和理算理算信息，当且仅当赔案状态在审核或更后面
        if (claim.getStage().equals(ClaimStageEnum.AUDITING.getCode()) || claim.getStage().equals(ClaimStageEnum.REVIEWING.getCode())) {

            List<AdjustmentResult> adjustmentResultList = adjustmentRecordBasicService.getAdjustmentResultByClaim(claim.getId(), true);

            if (adjustmentResultList == null || adjustmentResultList.isEmpty()) {
                AdjustResult adjustResult = new AdjustResult();
                adjustResult.setPublicAmount(BigDecimal.ZERO);
                adjustResult.setPrivateAmount(BigDecimal.ZERO);
                adjustResult.setPayOutAmount(BigDecimal.ZERO);
                claimDetailObject.setAdjustmentResult(adjustResult);

                AdjustConclusion adjustConclusion = new AdjustConclusion();
                adjustConclusion.setPayOutConclusion(null);
                adjustConclusion.setConclusionDetail(null);
                claimDetailObject.setAdjustConclusion(adjustConclusion);
            } else {
                AdjustResult adjustResult = new AdjustResult();
                adjustResult.setPublicAmount(adjustmentResultList.get(0).getPublicAmount());
                adjustResult.setPrivateAmount(adjustmentResultList.get(0).getIndividualAmount());
                adjustResult.setPayOutAmount(adjustmentResultList.get(0).getPayoutAmount());
                claimDetailObject.setAdjustmentResult(adjustResult);

                AdjustConclusion adjustConclusion = new AdjustConclusion();
                adjustConclusion.setPayOutConclusion(adjustmentResultList.get(0).getResult());
                adjustConclusion.setConclusionDetail(adjustmentResultList.get(0).getResultDetail());
                claimDetailObject.setAdjustConclusion(adjustConclusion);
            }
        }

        /**
         * 添加新展示的内容 DLXMDD-3855
         */
        ClaimTrackLog rejectLog = claimTrackLogService.queryLatestClaimRecord(claim.getId(), OperationTypeEnum.REJECT);
        ClaimTrackLog returnLog = claimTrackLogService.queryLatestClaimRecord(claim.getId(), OperationTypeEnum.RETURN_MANUAL, OperationTypeEnum.BACK_NODE);
        claimDetailObject.setLoginAccount(BizContextUtils.getUser());
        claimDetailObject.setRejectReason(Objects.isNull(rejectLog) ? null : rejectLog.getRemark());
        claimDetailObject.setReturnReason(Objects.isNull(returnLog) ? null : returnLog.getRemark());
        if (org.apache.commons.lang3.StringUtils.equals(claim.getStatus(), ClaimStatusEnum.COMPLETE_AUDIT.getCode()) || org.apache.commons.lang3.StringUtils.equals(claim.getStatus(), ClaimStatusEnum.Cancel.getCode())) {
            claimDetailObject.setCurrentOperatorName(MAGIC_CLAIM_STATUS_NAME);
        } else {
            claimDetailObject.setCurrentOperatorName(claim.getOperatorUserName());
        }
        claimDetailObject.setOperatorNames(Arrays.asList(claim.getPreExamOperatorName(), claim.getSubmittingOperatorName(),
                claim.getInspectionOperatorName(), claim.getAuditingOperatorName(), claim.getReviewingOperatorName()));
        claimDetailObject.setLimitHour(claim.getLimitHour());
        claimDetailObject.setHangUpTotalHour(timeLogService.timePeriodCalculator(claim, OperationTypeEnum.HANGUP, OperationTypeEnum.RELEASE_HANGUP));

        return claimDetailObject;
    }


    /**
     * 获取赔案详情
     *
     * @param claimId 赔案表id
     * @return 赔案详情
     */
    public Claim getClaimDetail(Long claimId) {
        // 1. 获取赔案基本信息
        Claim claim = claimRepository.findById(claimId);
        if (claim == null) {
            log.error("Claim not found for id: {}", claimId);
            throw new TpaBizException(BizErrorCode.NO_RECORD ,"Claim not found for id: " + claimId);
        }

        return claim;
    }


    /**
     * 挂起赔案
     */
    public Claim hangUpClaim(ClaimHangupRequest request) {
        // 1. 获取赔案基本信息
        Claim claim = claimRepository.findById(request.getClaimId());
        if (claim == null) {
            log.error("Claim not found for id: {}", request.getClaimId());
            throw new TpaBizException(BizErrorCode.NO_RECORD ,"Claim not found for id: " + request.getClaimId());
        }
        checkFinish(claim);
        checkUserId(claim);

        // 检查是否挂起。
        Boolean hangUpFlag = checkClaimHangUpStatus(claim);
        if (hangUpFlag) {
            throw new TpaBizException(BizErrorCode.CLAIM_HANG_UP, claim.getClaimNo());
        }

        // 检查挂起原因
        TpaHangupReason hangupReason = TpaHangupReason.getByCode(request.getHangupType());

        if (hangupReason == null) {
            throw new TpaBizException(BizErrorCode.PARAMETER_ERROR ,"不接受的挂起类型: " + request.getHangupType());
        }

        // 2. 将赔案状态改为挂起
        claim.setHangUpStatus(HangUpStatus.HANG_UP.getCode());
        claim.setHangUpType(hangupReason.getDesc());
        claimRepository.save(claim);

        // 4、同步给新tpa
        TpaHandupRequest tpaHandupRequest = new TpaHandupRequest();
        tpaHandupRequest.setClaimNo(claim.getClaimNo());
        tpaHandupRequest.setReason(request.getReason());
        tpaHandupRequest.setReasonType(hangupReason.getCode());
        tpaHandupRequest.setHangUpUser(BizContextUtils.getUser());
        tpaHandupRequest.setNode(ClaimStageEnum.getByCode(claim.getStage()).getValue());
        if (hangupReason.equals(TpaHangupReason.资料不齐挂给客户)) {
            ClaimDetailSyncVO syncVO = claimDetailSyncVOBuilder(claim);
            tpaHandupRequest.setClaimInfo(syncVO);
        }
        log.info("request: {}", tpaHandupRequest);
        ApiResult<Map<String,Object>>  remoteRs = tpaDataSyncFeign.saasAddClaimHangUpReord(tpaHandupRequest);
        log.info("addClaimHangUpReord: {}", JSONObject.toJSONString(remoteRs));
        if(!remoteRs.isSuccess()){
            throw new RuntimeException("挂起接口调用失败:" + remoteRs.getMessage());
        }

        // 3、增加操作记录
        claimTrackLogService.exceptionRecord(claim, OperationTypeEnum.HANGUP,
                TpaHangupReason.getByCode(request.getHangupType()) == null ? null :
                TpaHangupReason.getByCode(request.getHangupType()).getDesc(), request.getReason());
        timeLogService.addTimeLog(claim, OperationTypeEnum.HANGUP, claim.getOperatorUserName());

        return claim;
    }


    /**
     * 驳回赔案
     */
    public Claim rejectClaim(ClaimRejectRequest request) {

        // 1. 获取赔案基本信息
        Claim claim = claimRepository.findById(request.getClaimId());

        if (claim == null) {
            log.error("Claim not found for id: {}", request.getClaimId());
            throw new TpaBizException(BizErrorCode.NO_RECORD ,"Claim not found for id: " + request.getClaimId());
        }
        Map<String,Object>   operatorStoreMap =  storeOperatorInfoJson(claim);

        if (!claim.getStage().equals(ClaimStageEnum.REVIEWING.getCode())) {
            throw new TpaBizException(BizErrorCode.NO_RECORD ,"Claim not in Reviewing: " + request.getClaimId());
        }
        checkFinish(claim);
        checkUserId(claim);

        // 检查是否挂起。
        Boolean hangUpFlag = checkClaimHangUpStatus(claim);
        if (hangUpFlag) {
            throw new TpaBizException(BizErrorCode.CLAIM_HANG_UP, claim.getClaimNo());
        }

        claim.setRejectType(request.getRejectType());
        claim.setStage(ClaimStageEnum.AUDITING.getCode());
        claim.setStatus(ClaimStatusEnum.Auditing.getCode());

        // 增加操作记录
        Map<String,Object> trackExtra = storeOperatorInfoJson(claim);
        trackExtra.put("reason",request.getReason());
        trackExtra.put("type",request.getRejectType());
        trackExtra.putAll(operatorStoreMap);

        // 这里去获取审核阶段的操作人员并且赋值。
        ClaimTrackLog trackLog =  claimTrackLogService.queryLatestClaimRecord(claim.getId(),
                OperationTypeEnum.APPROVE_MANUAL_COMPLETE  );
        if(trackLog == null){
            throw new TpaBizException("该案件审核阶段为自动，没有经过人工处理，不能驳回");
        }
        String extraStore =  trackLog.getExtraStore();
        if(org.apache.commons.lang.StringUtils.isBlank(extraStore)){
            throw new TpaBizException("无法定位操作人人信息");
        }
        JSONObject userInfo =     JSONObject.parseObject(extraStore);

        claim.setOperatorOrgId(userInfo.getString("operOrgId"));
        claim.setOperatorOrgName(userInfo.getString("operOrgName"));
        claim.setOperatorUserId(userInfo.getString("operId"));
        claim.setOperatorUserName(userInfo.getString("operName"));

        //增加操作记录
        String logContent = String.format("%s 将赔案驳回给%s，驳回类型：%s\n驳回原因:%s",BizContextUtils.getUser(),
                claim.getOperatorUserName(), request.getRejectType(), request.getReason());
        claimTrackLogService.addActionRecord(claim, BizContextUtils.getUser(), trackExtra,
                OperationTypeEnum.REJECT, logContent);

        claimRepository.save(claim);
        timeLogService.addTimeLog(claim, OperationTypeEnum.REJECT, claim.getOperatorUserName());

        rejectNotifyTpa(claim, ClaimStatusEnum.Auditing, logContent, request.getReason());

        return claim;
    }

    private void rejectNotifyTpa(Claim claim, ClaimStatusEnum targetStatus,
                                 String logContent, String returnBackRemark){
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
        eventOtherRequest.setAssignOperatorName(claim.getOperatorUserName());
        eventOtherRequest.setAssignOperatorId(claim.getOperatorUserId());
        eventOtherRequest.setAssignOperatorGroupId(claim.getOperatorOrgId());
        eventOtherRequest.setAssignOperatorGroupName(claim.getOperatorOrgName());
        //记录operatorLog
        TpaAddLogRequest tpaAddLogRequest = new TpaAddLogRequest();
        tpaAddLogRequest.setClaimNumber( claim.getId());
        tpaAddLogRequest.setOperation(logContent);
        tpaAddLogRequest.setRemark(returnBackRemark);
        tpaAddLogRequest.setCreateBy(BizContextUtils.getUser());
        tpaAddLogRequest.setCreateTime( System.currentTimeMillis());
        eventOtherRequest.getOperationLogList().add(tpaAddLogRequest);

        fillReportLog(claim, eventOtherRequest, targetStatus, claim.getOperatorUserName(), null, new Date());
        commonLogService.addLogASync(claimId.toString(), CommonLogType.TO_TPA_LOG, "notifyTpaChangeStatus,request:{}",
                JSONObject.toJSONString(tpaSubmitClaimRequest));
        ApiResult<Map<String, Object>> remoteRs = tpaDataSyncFeign.submitClaimResult(tpaSubmitClaimRequest);
        //log.info("saas通知tpa初审完成不自动分配:{}",remoteRs);
        commonLogService.addClaimLogAsync(claimId, CommonLogType.TO_TPA_LOG, "notifyTpaChangeStatus, cremoteRs:{}",
                remoteRs);

        if (!remoteRs.isSuccess()) {
            throw new RuntimeException("rejectClaim，通知tpa失败:" + remoteRs.getMessage());
        }
    }


    /**
     * 检查当前登录用户是否有权限操作
     * 在写操作上都需要判定
     *
     * @param claim
     */
    public void checkUserId(Claim claim) {
        if (BizContextUtils.getUser() == null) {
            throw new TpaBizException(BizErrorCode.UNAUTHORIZED);
        }

        //做一个后门，admin可以绕过所有判定
        if (BizContextUtils.getUser().equals("admin")) {
            return;
        }

        //特殊后门：如果当前赔案是录入状态之前，跳过判定从而可以修改数据，防止卡死只能数修
        if (claim.getStage().equals(ClaimStageEnum.INIT.getCode()) || claim.getStage().equals(ClaimStageEnum.SIGNING.getCode())
                || claim.getStage().equals(ClaimStageEnum.PRE_EXAM.getCode())) {
            return;
        }

        ClaimStatusEnum currentStatus =  ClaimStatusEnum.getByCode(claim.getStatus(),claim.getStatusSub());

        if (claim.getOperatorUserName() == null || claim.getOperatorUserName().isBlank()) {
            throw new TpaBizException(BizErrorCode.FORBIDDEN, "赔案" + claim.getClaimNo() + "操作人员未分配");
        }

        if (!claim.getOperatorUserName().equals(BizContextUtils.getUser())) {
            throw new TpaBizException(BizErrorCode.FORBIDDEN, "赔案" + claim.getClaimNo() + "分配用户为" + claim.getOperatorUserName() + ", 当前登录用户为" + BizContextUtils.getUser());
        }
    }


    /**
     * 查询同步提示信息
     *
     * 不穿后两个参数将会查出全部类型的，传入的话查询特定类型
     */
    private ClaimHintMsg querySyncHint(Long claimId, HintMsgType type, Long objectId) {
        Criteria<ClaimHintMsg> criteria = new Criteria();
        criteria.eq(ClaimHintMsg::getClaimNumber, claimId);
        if (type != null) {
            criteria.eq(ClaimHintMsg::getRelationId, objectId);
            criteria.eq(ClaimHintMsg::getHintType, type.getCode());
        }

        ClaimHintMsg hintMsg = PkListUtil.first(hintMsgRespository.findByCriteria(criteria));

        return hintMsg;
    }


    /**
     * 如果赔案已经挂起，禁止几乎一切操作
     *
     * @return
     */
    public Boolean checkClaimHangUpStatus(Claim claim) {
        if (HangUpStatus.HANG_UP.getCode().equals(claim.getHangUpStatus())) {
            return true;
        }
        return false;
    }

    /**
     * 检查赔案是否到达终态，终止几乎一切操作
     *
     */
    public Boolean checkFinish(Claim claim) {
        if (claim.getStatus().equals(ClaimStatusEnum.COMPLETE_AUDIT.getCode()) || claim.getStatus().equals(ClaimStatusEnum.Finish.getCode())
                || claim.getStatus().equals(ClaimStatusEnum.Cancel.getCode())) {
            throw new TpaBizException("赔案" + claim.getClaimNo() +"已达终态");
        }

        return true;
    }

    /**
     * 使用赔案构造赔案同步信息
     * ClaimToTpaChangeService
     * @return
     */
    public ClaimDetailSyncVO claimDetailSyncVOBuilder(Claim claim) {

        return claimToTpaChangeService.toSyncVo(claim.getId());
    }

    /**
     * 发票异步查重
     * @param claimNumber
     */
    public void checkSameInvoiceAsync(Long claimNumber){
        ThreadExecutorUtil.SCHDEULED_THREAD_POOL.schedule(new Runnable() {

            @Override
            public void run() {
                checkSameInvoice(claimNumber);
            }
        }, 3, TimeUnit.SECONDS);
    }

    /**
     * 使用赔案构造赔案同步信息
     * ClaimToTpaChangeService
     * @return
     */
    public Long checkSameInvoice(Long id) {
        //如果该赔案没有发票，直接拦截
        Criteria<ClaimInvoice> invoiceCriteria = new Criteria<>();
        invoiceCriteria.eq(ClaimInvoice::getRelatedId, id);
        List<ClaimInvoice> invoiceList = claimInvoiceRepository.findByCriteria(invoiceCriteria);
        if (invoiceList == null || invoiceList.isEmpty()) {
            throw new TpaBizException(BizErrorCode.NO_RECORD, "该赔案没有发票信息。");
        }

        //通知tpa当前的发票
        TpaSubmitClaimResult request = new TpaSubmitClaimResult();
        request.setClaimNumber(id);
        request.setClaimInfo(toTpaChangeService.toSyncVo(id));
        request.setEventType(Integer.valueOf(EventType.saas发票查重.getCode()));
        ApiResult<Map<String, Object>> remoteRs = tpaDataSyncFeign.submitClaimResult(request);
        commonLogService.addClaimLogAsync(id, CommonLogType.TO_TPA_LOG, "saas同步当前当前发票至tpa,\nrequest:{},\nremoteRs:{}", request, JSONObject.toJSONString(remoteRs));
        if (!remoteRs.isSuccess()) {
            log.error("saas同步当前当前发票至tpa,claimId:{},remoteRs:{}", id, JSONObject.toJSONString(remoteRs));
            throw new RuntimeException("saas同步当前当前发票至tpa失败:" + JSONObject.toJSONString(remoteRs));
        }

        //发起异步任务，调用tpa查重接口
        SyncTask syncTask = SpringContextUtils.getBean(CheckSameInvoiceTrigger.class).addJobAndTryFire(String.valueOf(id),
                2, 61
        );

        return syncTask.getId();
    }

    /**
     * 将一串赔案号转化为一个赔案号List
     */
    public List<Long> parseClaimNos(String claimNos) {
        if (claimNos == null) {
            return Collections.emptyList();
        }

        // 预处理：将换行符统一为逗号，去除多余空格
        String processed = claimNos
                .replace("\n", ",")     // 换行符转逗号
                .replace("\t", ",")     // 制表符转逗号
                .replace(" ", ",")     // 空格转逗号
                .replace(";", ",")      // 分号转逗号
                .replace("，", ",")     // 中文逗号转英文逗号
                .replace("；", ",")    // 中文分号转英文逗号
                .replace("、", ",");    // 中文顿号转英文逗号

        // 分割并处理
        List<String> claimNoList = Arrays.stream(processed.split(","))
                                    .map(String::trim)      // 去除首尾空格
                                    .filter(s -> !s.isEmpty())  // 过滤空字符串
                                    .distinct()             // 去重（如果需要）
                                    .collect(Collectors.toList());

        List<Long> result = new ArrayList<>(claimNoList.size());
        for (int i = 0; i < claimNoList.size(); i++) {
            String claimNo = claimNoList.get(i);

            try {
                result.add(Long.parseLong(claimNo));
            } catch (NumberFormatException e) {
                throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, claimNo + "不是有效的赔案号");
            }
        }

        //升序排序
        Collections.sort(result);
        return result;
    }

    /**
     * 获取查重结果
     *
     * @param id
     * @return
     */
    public String getHint(Long id) {
        ClaimHintMsg claimHintMsg = querySyncHint(id, HintMsgType.CLAIM_DETAIL, id);
        if (claimHintMsg != null) {
            Map<String, String> hintMap = JsonUtil.fromJson(claimHintMsg.getData(), new TypeReference<Map<String, String>>(){});

            return hintMap.get("checkSame");
        }
        return null;
    }

    public void insertBatch(List<Claim> claimList, String... remark) {
        claimRepository.insertBatch(claimList);

        for (Claim claim : claimList) {
            claimTrackLogService.claimRecord(claim,BizContextUtils.getUser(), null,
                    JsonUtil.toJson(claim), OperationTypeEnum.UPDATE, BizModelEnum.CLAIM_DETAIL, remark);
        }
    }

    /**
     * 赔案时效计算器
     *
     * 目前只减去挂起时间
     */
    public BigDecimal limitHourCalculator(Claim claim) {

        //查询整个的时间
        BigDecimal limitHourRaw = timeLogService.getLimitHourRaw(claim);

        //查询所有挂起和解挂的区间
        BigDecimal hangupHour = timeLogService.timePeriodCalculator(claim, OperationTypeEnum.HANGUP, OperationTypeEnum.RELEASE_HANGUP);

        return limitHourRaw.subtract(hangupHour);
    }

    public void cancelClaim(Claim claim) {
        claim.setStage(ClaimStageEnum.FINISH.getCode());
        claim.setStatus(ClaimStatusEnum.Cancel.getCode());
        claim.setStatusSub(ClaimStatusEnum.Cancel.getSubStatus());
        updateClaim(claim, false);

        if (StringUtils.isNotBlank(claim.getInsurerClaimNo())) {
            List<AdjustmentResult> adjustmentResultList = adjustmentRecordBasicService.getAdjustmentResultByClaim(claim.getId(), true);
            TpaPersonClaim tpaPersonClaim = new TpaPersonClaim();
            tpaPersonClaim.setPclaimStatus(-4);

            if (adjustmentResultList == null || adjustmentResultList.isEmpty()) {
                tpaPersonClaim.setPclaimMemo(null);
            } else {
                tpaPersonClaim.setPclaimMemo(adjustmentResultList.get(0).getResultDetail());
            }
            tpaPersonClaim.setPclaimUpdatetime(new Date());
            LambdaQueryWrapper<TpaPersonClaim> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(TpaPersonClaim::getPclaimCode, claim.getInsurerClaimNo());
            tpaPersonClaimMapper.update(tpaPersonClaim, queryWrapper);
        }
    }
}
