package com.bone.tpa.claim.domain.service;


import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.bone.core.id.IdGenerator;
import com.bone.core.result.QueryParam;
import com.bone.core.result.SortingField;
import com.bone.core.util.JsonUtil;
import com.bone.tpa.api.enums.HangUpStatus;
import com.bone.tpa.api.enums.SourceType;
import com.bone.tpa.api.enums.YesOrNoEnum;
import com.bone.tpa.claim.application.enums.BizModelEnum;
import com.bone.tpa.claim.application.request.QueryListRequest;
import com.bone.tpa.claim.flow.stage.PreCheckStageService;
import com.bone.tpa.sdk.dao.SignRecordRepository;
import com.bone.tpa.sdk.adjustment.enums.PolicyType;
import com.bone.tpa.sdk.claim.enums.*;
import com.bone.tpa.sdk.claim.exception.TpaBizException;
import com.bone.tpa.sdk.claim.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * ss_sign_record Service 接口
 *
 * @author 0
 */
@Service
@Slf4j
public class SignRecordService {
    @Autowired
    private SignRecordRepository signRecordRepository;

    @Autowired
    private SignRecordTrackLogService signRecordTrackLogService;

    @Autowired
    private FileUploadService fileUploadService;

    @Autowired
    private ClaimService claimService;

    @Autowired
    private ClaimStakeholderService claimStakeholderService;

    @Autowired
    private ClaimImageService claimImageService;

    @Autowired
    private PreCheckStageService preCheckStageService;

    @Transactional
    public SignRecord createSignRecord(SignRecord signRecord) {

        //1、校验
        signOffValidate(signRecord);
//        if (BizIdentityCodeEnum.getByValue(signRecord.getInsuranceCompany()) == null) {
//            throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "该保险公司暂不支持: " + signRecord.getInsuranceCompany());
//        }

        //2、生成普康批次号
        String prefix = "F999";
        String batchNo = IdGenerator.generateSequenceNo(prefix, true, 4);
        signRecord.setBatchNo(batchNo);

        log.info("生成批次号：" + batchNo);

        //3、进行一定的规则检测
        //signExt.validate(signRecord);

        //TODO: 这里应该要找保单，暂时用这种逻辑保证测试
        signRecord.setTenantId(123456L);
        signRecord.setImageUploadFlag("false");
        signRecord.setSignStatus(SignStatusEnum.WAIT_FOR_PARTICIPANT.getCode());

        //4、签收记录持久化
        Long id = signRecordRepository.insert(signRecord);

        signRecord.setId(id);

        //5、增加操作记录
        signRecordTrackLogService.operationRecord(signRecord, OperationTypeEnum.CREATE);

        log.info("createSignRecord成功：" + JsonUtil.toJson(signRecord));

        return signRecord;
    }

    /**
     * 校验签收记录的一些正确性
     *
     * @param signRecord
     */
    private void signOffValidate(SignRecord signRecord) {
        //首先既然是签收，那么就不能有id
        if (signRecord == null) {
            throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "上传信息为空!");
        }
        if (signRecord.getId() != null) {
            throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "Id不为空!");
        }

    }


    /**
     * 根据id去获取已经上传的文件并且获取分析结果
     *
     * @param signId
     */
    public void uploadParticipant(Long signId) {
        //先要获取签收记录，签收记录至少得存在而且状态必须是等待上传人员
        SignRecord signRecord = signRecordRepository.findById(signId);
        if (signRecord == null) {
            throw new TpaBizException(BizErrorCode.NO_RECORD, "Sign record not found for id: " + signId);
        }
        if (!SignStatusEnum.WAIT_FOR_PARTICIPANT.getCode().equals(signRecord.getSignStatus())) {
            throw new TpaBizException(BizErrorCode.STATUS_ERROR, "Sign record status error for id: " + signId);
        }

        //去获取已经上传了的文件内容
        QueryListRequest queryListRequest = new QueryListRequest();
        queryListRequest.setId(String.valueOf(signId));
        queryListRequest.setTenantId(String.valueOf(signRecord.getTenantId()));

        //查询的是签收记录
        queryListRequest.getQueryParams().add(new QueryParam("relatedModel", BizModelEnum.SIGN_RECORD.getCode()));
        //在查询字段方面，文件类型要做出限制
        queryListRequest.getQueryParams().add(new QueryParam("fileType", FileTypeEnum.EXCEL.getCode()));
        //然后要检查当前状态，是等待解析的
        queryListRequest.getQueryParams().add(new QueryParam("status", FileStatusEnum.EXCEL_WAITING.getCode()));

        //根据上传时间排序
        SortingField sortingField = new SortingField();
        sortingField.setField("createTime");
        sortingField.setOrder("asc");
        queryListRequest.setSortingFields(new ArrayList<>());
        queryListRequest.getSortingFields().add(sortingField);

        //如果这个时候没有
        List<FileUploadRecord> fileUploadRecordList = fileUploadService.getFileUploadRecord(queryListRequest).getData();

        if (fileUploadRecordList.isEmpty()) {
            throw new TpaBizException(BizErrorCode.STATUS_ERROR, "没有上传新的数据文件: " + signId);
        }

        //更新状态
        signRecord.setSignStatus(SignStatusEnum.WAIT_FOR_IMAGE.getCode());

        Integer claimCount = signRecord.getClaimCount();
        if (claimCount == null) {
            claimCount = 0;
        }

        for (FileUploadRecord fileUploadRecord : fileUploadRecordList) {
            JSONArray jsonArray = JSONUtil.parseArray(fileUploadRecord.getRemark());
            List<String> fileValue = jsonArray.toList(String.class);

            //生成赔案
            createClaim(fileValue, signRecord);

            claimCount += fileValue.size();

            //留下操作记录
            signRecordTrackLogService.operationRecord(signRecord, OperationTypeEnum.UPDATE);

            log.info("createSignRecord成功：" + JsonUtil.toJson(signRecord));
        }
        signRecord.setClaimCount(claimCount);

        signRecordRepository.update(signRecord);
    }


    private void createClaim(List<String> mapList, SignRecord signRecord) {
        for (String mapString : mapList) {

            // 将 JSON 字符串转换为 JSONObject
            JSONObject jsonObject = JSONUtil.parseObj(mapString);

            // 将 JSONObject 转换为 Map<String, String>
            Map<String, String> map = jsonObject.toBean(Map.class);

            // 构造赔案相关内容
            Claim claim = new Claim();
            claim.setRelatedId(signRecord.getId());
            claim.setBatchNo(signRecord.getBatchNo());
            claim.setPolicyNo(""); //TODO
            claim.setStatus(ClaimStatusEnum.DRAFT.getCode());
            claim.setStage(ClaimStageEnum.SIGNING.getCode());
            claim.setHangUpStatus(HangUpStatus.NO_HANG_UP.getCode());
            claim.setEmergency(signRecord.getEmergency());
            claim.setSource(SourceType.线下.getCode());
            claim.setBizType(PolicyType.GROUP.getCode().toString());
            claim.setProcessType(signRecord.getProcessType());
            claim.setCountingResult(BigDecimal.valueOf(0));
            claim.setImageUploadFlag("false");
            claim.setImageCount(BigDecimal.valueOf(0));
            claim.setImageUploadCount(BigDecimal.valueOf(0));
            claim.setCfgImageClassify(YesOrNoEnum.YES.getCode());

            //claim.setBizIdentityCode(BizIdentityCodeEnum.getByValue(signRecord.getInsuranceCompany()).getCode());
            claim.setTenantId(signRecord.getTenantId());


            claim = claimService.createClaim(claim);

            //出险人
            ClaimStakeholder claimStakeholder = new ClaimStakeholder();
            claimStakeholder.setRelatedId(claim.getId());
            claimStakeholder.setPersonType(PersonTypeEnum.OUT_INSURE.getCode());
            claimStakeholder.setName(map.get("出险人姓名"));
            claimStakeholder.setIdentityType(map.get("出险人证件类型"));
            claimStakeholder.setIdentityNo(map.get("出险人证件号"));

            //claimStakeholder.setBizIdentityCode(BizIdentityCodeEnum.getByValue(signRecord.getInsuranceCompany()).getCode());
            claimStakeholder.setTenantId(signRecord.getTenantId());

            claimStakeholderService.newStakeHolder(claimStakeholder);
        }
    }


    /**
     * 根据查询选项获取表格信息
     * @param request
     * @return
     */
    public List<SignRecord> getByQueryParam(QueryListRequest request) {
        request.getQueryParams().add(new QueryParam("tenantId", request.getTenantId()));

        return signRecordRepository.queryByCondition(request.getQueryParams(), request.getSortingFields(), (request.getPageNo() - 1) * request.getPageSize(), request.getPageSize(),
                "ss_invoice_project", request.getBizIdentityCode()).getData();
    }


    /**
     * 获取签收记录
     *
     * @param signId
     * @return
     */
    public SignRecord getSignRecord(Long signId) {
        // 1. 获取签收记录
        SignRecord signRecord = signRecordRepository.findById(signId);
        if (signRecord == null) {
            log.error("SignRecord not found for id: {}", signId);
            throw new TpaBizException(BizErrorCode.NO_RECORD ,"SignRecord not found for id: " + signId);
        }

        return signRecord;
    }


    public void confirmSign(Long signId) {
        // 1. 获取签收记录
        SignRecord signRecord = getSignRecord(signId);

        // 2. 检查签收记录是不是已经完成了
        if (signRecord.getSignStatus().equals(SignStatusEnum.FINISHED.getCode())) {
            throw new TpaBizException(BizErrorCode.SIGN_CONFIRM_ERROR, "该批次已完成签收，无需重复签收。");
        }

        // 3. 检查是否所有的赔案都已经上传了影像件
        if (!signRecord.getSignStatus().equals(SignStatusEnum.WAIT_FOR_CONFIRMATION.getCode())) {
            throw new TpaBizException(BizErrorCode.SIGN_CONFIRM_ERROR, "批次完成签收需满足：所有赔案均已完成案件上传。");
        }

        // 6. 将所有下属赔案都更新到初审阶段
        QueryListRequest queryListRequest = new QueryListRequest();
        queryListRequest.setId(String.valueOf(signId));
        queryListRequest.setTenantId(String.valueOf(signRecord.getTenantId()));
        List<Claim> claimList = claimService.getByQueryParam(queryListRequest);
        for (Claim claim : claimList) {
            if (!claim.getStage().equals(ClaimStageEnum.SIGNING.getCode())) {
                log.error("赔案阶段错误: " + claim.getClaimNo() + claim.getStage());
            } else {
                claim.setStage(ClaimStageEnum.PRE_EXAM.getCode());
            }
        }
        claimService.batchUpdateClaim(claimList, OperationTypeEnum.CREATE);

        // 5. 更新
        signRecord.setSignStatus(SignStatusEnum.FINISHED.getCode());
        signRecordRepository.update(signRecord);
    }


    public Long insertNewSignRecord(SignRecord signRecord) {
        if (signRecord.getId() == null) {
            throw new TpaBizException(BizErrorCode.INNER_PARAMETER_ERROR, "创建新签收记录失败");
        }

        return signRecordRepository.insert(signRecord);
    }


    public void passToNextStage(Long claimId) {
        // 1. 获取签收记录
        Claim claim = claimService.getById(claimId);
        if (HangUpStatus.HANG_UP.getCode().equals(claim.getHangUpStatus())) {
            throw new TpaBizException(BizErrorCode.CLAIM_HANG_UP, claim.getClaimNo());
        }

        // 2. 检查赔案是不是初审阶段
        if (!claim.getStage().equals(ClaimStageEnum.PRE_EXAM.getCode())) {
            throw new TpaBizException(BizErrorCode.BIZ_STAGE_ERROR, "该赔案不是初审阶段");
        }

        // 3. 检查权限。若赔案有处理人，则当前人需要一致。
        claimService.checkFinish(claim);
        claimService.checkUserId(claim);

        // 4. 检查赔案需要影像件分类
        //     if (YesOrNoEnum.getByCode(claim.getCfgImageClassify()) == YesOrNoEnum.YES)  {
        //
        if (Objects.equals(YesOrNoEnum.YES.getCode(), claim.getCfgImageClassify()))  {
            QueryListRequest queryListRequest = new QueryListRequest();
            queryListRequest.setTenantId(String.valueOf(claim.getTenantId()));
            queryListRequest.setId(String.valueOf(claim.getId()));

            List<ClaimImage> claimImageList = claimImageService.getByQueryParam(queryListRequest);

            int index = 0;
            for (ClaimImage claimImage : claimImageList) {
                if (claimImage.getImageIndex() > index) {
                    index = claimImage.getImageIndex();
                } else {
                    claimImage.setImageIndex(index+1);
                    index ++;
                }
                if (claimImage.getClearType() == null) {
                    throw new TpaBizException(BizErrorCode.IMAGE_NOT_CLASSIFIED, "部分影像件的清晰类型为空");
                }
            }
        }

        // 5. 更新

        //claimService.updateClaim(claim, "通过初审阶段");

        preCheckStageService.pageSubmit(claimService.getById(claimId));
        // 6. 触发初审阶段通过的核心方法
        //claimActionService.completePreExame(claimId);
    }
}
