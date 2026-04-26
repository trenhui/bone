package com.bone.tpa.soa.application.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.alibaba.excel.EasyExcel;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bone.core.auth.User;
import com.bone.core.result.PageResult;
import com.bone.core.tenant.context.UserContext;
import com.bone.core.util.BizContextUtils;
import com.bone.core.util.JsonUtil;
import com.bone.core.util.PkListUtil;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.tpa.adjustment.application.AdjustmentApplicationService;
import com.bone.tpa.api.ApiResult;
import com.bone.tpa.api.request.ClaimPushFailRequest;
import com.bone.tpa.api.request.ReturnToManualRequest;
import com.bone.tpa.api.response.ClaimPushFailOperateResponse;
import com.bone.tpa.api.response.ClaimPushFailResponse;
import com.bone.tpa.claim.application.enums.BizModelEnum;
import com.bone.tpa.claim.domain.service.ClaimService;
import com.bone.tpa.claim.domain.service.ClaimTrackLogService;
import com.bone.tpa.claim.sync.SyncBaseTool;
import com.bone.tpa.facade.feign.TpaDataSyncFeign;
import com.bone.tpa.facade.request.UserCheckRequest;
import com.bone.tpa.facade.vo.UserCheckVO;
import com.bone.tpa.push.service.PushClaimService;
import com.bone.tpa.push.util.CommonTool;
import com.bone.tpa.sdk.adjustment.exception.DataNotFoundException;
import com.bone.tpa.sdk.claim.enums.*;
import com.bone.tpa.sdk.claim.model.Claim;
import com.bone.tpa.sdk.claim.model.ClaimPushFailOperateLog;
import com.bone.tpa.sdk.claim.model.ClaimStakeholder;
import com.bone.tpa.sdk.dao.ClaimPushFailOperateLogRepository;
import com.bone.tpa.sdk.dao.ClaimRepository;
import com.bone.tpa.sdk.dao.ClaimStakeholderRepository;
import com.bone.tpa.sdk.dao.ClaimTrackLogRepository;
import com.bone.tpa.sdk.tpasaasdb.mapper.ClaimMapper;
import com.bone.tpa.sdk.tpasaasdb.model.ClaimPushFailDO;
import com.bone.tpa.sdk.tpasaasdb.model.ClaimPushFailOperateDO;
import com.bone.tpa.soa.application.ClaimPushService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.bone.tpa.push.constants.CommonConstant.MAGIC_USER_NAME;

@Component
@Slf4j
public class ClaimPushServiceImpl extends SyncBaseTool implements ClaimPushService {

    @Autowired
    private ClaimRepository claimRepository;

    @Autowired
    private ClaimService claimService;

    @Autowired
    private PushClaimService pushClaimService;

    @Autowired
    private ClaimTrackLogRepository claimTrackLogRepository;

    @Autowired
    private ClaimPushFailOperateLogRepository claimPushFailOperateLogRepository;

    @Autowired
    private ClaimTrackLogService claimTrackLogService;

    @Autowired
    private AdjustmentApplicationService adjustmentApplicationService;

    @Autowired
    private TpaDataSyncFeign tpaDataSyncFeign;

    @Autowired
    private ClaimMapper claimMapper;
    @Autowired
    private ClaimStakeholderRepository claimStakeholderRepository;

    @Override
    public ApiResult repush(List<Long> claimNos) {
        ApiResult apiResult = new ApiResult();
        if (CollectionUtil.isEmpty(claimNos)) {
            apiResult.setCode(-1);
            apiResult.setMessage("请选择要重新推送的赔案");
            return apiResult;
        }
        List<Claim> claims = claimRepository.findById(claimNos);
        if (CollectionUtil.isEmpty(claims)) {
            throw new DataNotFoundException("未找到该赔案  claimNos：" + JSON.toJSONString(claimNos));
        }

        boolean allMatch = claims.stream().allMatch(claim -> StringUtils.equals(claim.getStatus(), ClaimStatusEnum.COMPLETE_AUDIT.getCode()));
        if (!allMatch) {
            throw new DataNotFoundException("该批赔案不是已审核状态  claimNos：" + JSON.toJSONString(claimNos));
        }

        claims.forEach(claim -> {
            if (!StringUtils.equals(claim.getPkPushStatus(), PkPushStatusEnum.PUSH_SUCCESS.getName()) ||
                    !(
                            StringUtils.equals(claim.getInsurancePushStatus(), InsurancePushStatusEnum.PUSH_FAILED.getName()) ||
                                    StringUtils.equals(claim.getInsurancePushStatus(), TbPushStatusEnum.Failed.getName()) ||
                                    StringUtils.equals(claim.getInsurancePushStatus(), GyPushStatusEnum.Failed.getName())
                    )
            ) {
                return;
            }

            pushClaimService.push(claim.getId(), null);

            Claim curClaim = new Claim();
            curClaim.setId(claim.getId());
            curClaim.setPkPushStatus(PkPushStatusEnum.WAITING_PUSH.getName());
            curClaim.setInsurancePushStatus(InsurancePushStatusEnum.WAITING_PUSH.getName());
            claimRepository.updateAndClearFields(curClaim, PkListUtil.asList("pk_push_time",
                    "insurance_push_time", "push_back_reason", "insurance_callback_success_time"));

            ClaimPushFailOperateLog claimPushFailOperateLog = new ClaimPushFailOperateLog();
            claimPushFailOperateLog.setTenantId(claim.getTenantId());
            claimPushFailOperateLog.setClaimId(claim.getId());
            claimPushFailOperateLog.setClaimNo(claim.getClaimNo());
            claimPushFailOperateLog.setPushBackReason(claim.getPushBackReason());
            claimPushFailOperateLog.setErrorType(claim.getErrorType());
            claimPushFailOperateLogRepository.insert(claimPushFailOperateLog);
        });

        apiResult.setMessage("已重新推送!");
        return apiResult;
    }

    @Override
    @Transactional
    public ApiResult returnToManual(ReturnToManualRequest returnToManualRequest) {
        ApiResult apiResult = new ApiResult();
        if (CollectionUtil.isEmpty(returnToManualRequest.getClaimNos())) {
            apiResult.setCode(-1);
            apiResult.setMessage("请选择要退回的赔案");
            return apiResult;
        }

        if (StringUtils.isBlank(returnToManualRequest.getNode())) {
            throw new RuntimeException("退回环节不能为空!:"+JSON.toJSONString(returnToManualRequest.getClaimNos()));
        }

        List<Claim> claims = claimRepository.findById(returnToManualRequest.getClaimNos());
        if (CollectionUtil.isEmpty(claims)) {
            throw new DataNotFoundException("未找到该赔案  claimNos：" + JSON.toJSONString(returnToManualRequest.getClaimNos()));
        }

        claims.forEach(claim -> {
            if (StringUtils.compare(claim.getStatus(), ClaimStatusEnum.COMPLETE_AUDIT.getCode()) < 0) {
                throw new RuntimeException("该失败记录已退回  claimNo：" + claim.getClaimNo());
            }

            if (!StringUtils.equals(claim.getPkPushStatus(), PkPushStatusEnum.PUSH_SUCCESS.getName()) ||
                    !(
                            StringUtils.equals(claim.getInsurancePushStatus(), InsurancePushStatusEnum.PUSH_FAILED.getName()) ||
                                    StringUtils.equals(claim.getInsurancePushStatus(), TbPushStatusEnum.Failed.getName()) ||
                                    StringUtils.equals(claim.getInsurancePushStatus(), GyPushStatusEnum.Failed.getName())
                    )
            ) {
                throw new RuntimeException("该失败记录已退回/不存在:  claimNo：" + claim.getClaimNo());
            }

            String backAuditUserName = returnToManualRequest.getUserName();
            String stage;
            if ("审核".equals(returnToManualRequest.getNode())) {
                if (StringUtils.isBlank(backAuditUserName)) {
                    backAuditUserName = claim.getAuditingOperatorName();
                }
                stage = ClaimStageEnum.AUDITING.getCode();
            }else if ("录入".equals(returnToManualRequest.getNode())) {
                if (StringUtils.isBlank(backAuditUserName)) {
                    backAuditUserName = claim.getSubmittingOperatorName();
                }
                stage = ClaimStageEnum.SUBMITTING.getCode();
            }else if ("初审".equals(returnToManualRequest.getNode())) {
                if (StringUtils.isBlank(backAuditUserName)) {
                    backAuditUserName = claim.getPreExamOperatorName();
                }
                stage = ClaimStageEnum.PRE_EXAM.getCode();
            }else if ("质检".equals(returnToManualRequest.getNode())) {
                if (StringUtils.isBlank(backAuditUserName)) {
                    backAuditUserName = claim.getInspectionOperatorName();
                }
                stage = ClaimStageEnum.INSPECTION.getCode();
            }else{
                throw new RuntimeException("退回节点不存在:"+claim.getClaimNo());
            }

            // 调用tpa判断用户是否存在
            UserCheckRequest userCheckRequest = new UserCheckRequest();
            userCheckRequest.setStage(stage);
            userCheckRequest.setUserName(backAuditUserName);
            ApiResult<UserCheckVO> queryUserGroupInfo = tpaDataSyncFeign.queryUserGroupInfo(userCheckRequest);
            if (!Objects.equals(queryUserGroupInfo.getCode(), 0) || Objects.isNull(queryUserGroupInfo.getData()) || CollectionUtil.isEmpty(queryUserGroupInfo.getData().getGroupList())) {
                throw new RuntimeException("用户不存在:"+backAuditUserName + ",claimNo:"+claim.getClaimNo()+",request:"+JSON.toJSONString(returnToManualRequest)+",message:"+queryUserGroupInfo.getMessage());
            }
            UserCheckVO userCheckVO = queryUserGroupInfo.getData();

            if (MAGIC_USER_NAME.equals(backAuditUserName)){
                throw new RuntimeException("审核人为system的赔案需选择其他人员进行处理!:"+claim.getClaimNo());
            }


            Claim curClaim = new Claim();
            curClaim.setId(claim.getId());
            curClaim.setPkPushStatus(PkPushStatusEnum.WAITING_PUSH.getName());
            curClaim.setInsurancePushStatus(InsurancePushStatusEnum.WAITING_PUSH.getName());

            String operationMsg = null;
            if ("初审".equals(returnToManualRequest.getNode())) {
                curClaim.setStatus(ClaimStatusEnum.PRE_ADUITING.getCode());
                curClaim.setStage(ClaimStatusEnum.PRE_ADUITING.getStage().getCode());
                curClaim.setStatusSub(ClaimStatusEnum.PRE_ADUITING.getSubStatus());
//                curClaim.setPreExamOperatorName(backAuditUserName);
                operationMsg = "案件撤回初审中,原因:" + returnToManualRequest.getReturnReason();
            }
            if ("录入".equals(returnToManualRequest.getNode())) {
                curClaim.setStatus(ClaimStatusEnum.PUKANG_INPUTING.getCode());
                curClaim.setStage(ClaimStatusEnum.PUKANG_INPUTING.getStage().getCode());
                curClaim.setStatusSub(ClaimStatusEnum.PUKANG_INPUTING.getSubStatus());
//                curClaim.setSubmittingOperatorName(backAuditUserName);
                operationMsg = "案件撤回录入中,原因:" + returnToManualRequest.getReturnReason();
            }
            if ("审核".equals(returnToManualRequest.getNode())) {
                curClaim.setStatus(ClaimStatusEnum.Auditing.getCode());
                curClaim.setStage(ClaimStatusEnum.Auditing.getStage().getCode());
                curClaim.setStatusSub(ClaimStatusEnum.Auditing.getSubStatus());
                operationMsg = "案件撤回审核中,原因:" + returnToManualRequest.getReturnReason();
            }
            if ("质检".equals(returnToManualRequest.getNode())) {
                curClaim.setStatus(ClaimStatusEnum.INSPECTIONING.getCode());
                curClaim.setStage(ClaimStatusEnum.INSPECTIONING.getStage().getCode());
                curClaim.setStatusSub(ClaimStatusEnum.INSPECTIONING.getSubStatus());
//                curClaim.setInspectionOperatorName(backAuditUserName);
                operationMsg = "案件撤回质检中,原因:" + returnToManualRequest.getReturnReason();
            }
            curClaim.setOperatorUserName(backAuditUserName);
            curClaim.setOperatorUserId(userCheckVO.getUserId());
            curClaim.setOperatorOrgId(String.valueOf(userCheckVO.getGroupList().get(0).getGroupId()));
            curClaim.setOperatorOrgName(userCheckVO.getGroupList().get(0).getGroupName());
            claimRepository.updateAndClearFields(curClaim, PkListUtil.asList("pk_push_time",
                    "insurance_push_time", "insurance_callback_success_time", "reviewing_pass_time", "reviewing_operator_name"));

            CommonTool.insertTrackLog(claimTrackLogRepository, claim, curClaim, operationMsg, OperationTypeEnum.RETURN_MANUAL, returnToManualRequest.getReturnReason());

            ClaimPushFailOperateLog claimPushFailOperateLog = new ClaimPushFailOperateLog();
            claimPushFailOperateLog.setTenantId(claim.getTenantId());
            claimPushFailOperateLog.setClaimId(claim.getId());
            claimPushFailOperateLog.setClaimNo(claim.getClaimNo());
            claimPushFailOperateLog.setPushBackReason(claim.getPushBackReason());
            claimPushFailOperateLog.setErrorType(claim.getErrorType());
            claimPushFailOperateLogRepository.insert(claimPushFailOperateLog);


            // 清除理算
            adjustmentApplicationService.clear(claim.getId());
        });

        apiResult.setMessage("已退回!");
        return apiResult;
    }

    @Override
    public String syncClaimPushStatus(String claimNo, Integer status, String pushBackReason, String errorType) {
        Long claimNoLong = Long.valueOf(claimNo);
        Claim claim = claimRepository.findById(claimNoLong);
        if (Objects.isNull(claim)) {
            log.error("claim not exist. claimNo: {}, status: {}, pushBackReason: {}", claimNo, status, pushBackReason);
            throw new RuntimeException("赔案不存在");
        }
        String claimStatus = claim.getStatus();
        if (!ClaimStatusEnum.COMPLETE_AUDIT.getCode().equals(claimStatus)) {
            log.error("claim status error. claimNo: {}, status: {}", claimNo, claimStatus);
            throw new RuntimeException("赔案状态非法");
        }
        String pkPushStatus = claim.getPkPushStatus();
        if (!PkPushStatusEnum.PUSH_SUCCESS.getName().equals(pkPushStatus)) {
            log.error("clam pkPushStatus error. claimNumber: {}, pkPushStatus: {}", claimNo, pkPushStatus);
            throw new RuntimeException("赔案普康推送状态非法");
        }

        Date date = new Date();
        Claim updateClaim = new Claim();
        updateClaim.setId(claim.getId());
        updateClaim.setUpdateTime(date);
        String remark = "";
        if (Objects.equals(-1, status)) { // 保司推送失败
            updateClaim.setInsurancePushStatus(InsurancePushStatusEnum.PUSH_FAILED.getName());
            updateClaim.setPushBackReason(pushBackReason);
            updateClaim.setErrorType(errorType);
            remark = InsurancePushStatusEnum.PUSH_FAILED.getName();
        }  else if (Objects.equals(0, status)) { // 保司已推送
            updateClaim.setInsurancePushStatus(InsurancePushStatusEnum.PUSH_SUCCESS.getName());
            updateClaim.setInsurancePushTime(date);
            remark = InsurancePushStatusEnum.PUSH_SUCCESS.getName();
        } else if (Objects.equals(1, status)) { // 保司已完成
            updateClaim.setInsurancePushStatus(InsurancePushStatusEnum.COMPLETED.getName());
            updateClaim.setInsuranceCallbackSuccessTime(date);
            remark = InsurancePushStatusEnum.COMPLETED.getName();
        }
        claimTrackLogService.claimRecord(claim, BizContextUtils.getUser(), null, JsonUtil.toJson(updateClaim), OperationTypeEnum.UPDATE, BizModelEnum.CLAIM_DETAIL, remark);
        claimRepository.save(updateClaim);
        return "success";
    }

    @Override
    public PageResult<ClaimPushFailResponse> pageClaimPushFail(ClaimPushFailRequest request) {

        String batchNo = request.getBatchNo();
        String claimNo = request.getClaimNo();
        String policyNo = request.getPolicyNo();
        String outInsureName = request.getOutInsureName();
        String outInsureIdentityNo = request.getOutInsureIdentityNo();
        String insureName = request.getInsureName();
        String insuranceName = request.getInsuranceName();
        String branchName = request.getBranchName();
        String errorType = request.getErrorType();
        String auditingOperatorName = request.getAuditingOperatorName();
        String vipSign = request.getVipSign();

        Integer pageNumber = request.getPageNumber();
        Integer pageSize = request.getPageSize();
        Page<ClaimPushFailDO> page = new Page<>(pageNumber, pageSize);
        Page<ClaimPushFailDO> claimPushFailDOPage = claimMapper.pageClaimPushFailDO(page, batchNo, claimNo, policyNo,
                outInsureName, outInsureIdentityNo, insureName, insuranceName, branchName, errorType,
                auditingOperatorName, vipSign,"已推送", "保司推送失败");

        List<ClaimPushFailDO> records = claimPushFailDOPage.getRecords();
        Map<Long, ClaimStakeholder> stackHolderMap = null;
        if (!CollectionUtils.isEmpty(records)) {
            // 查询主被人信息
            List<Long> claimIdList = records.stream().map(ClaimPushFailDO::getId).toList();
//            LambdaQueryWrapper<ClaimStakeholder> mainInsureQuery = new LambdaQueryWrapper<>();
//            mainInsureQuery.in(ClaimStakeholder::getRelatedId, claimIdList)
//                    .eq(ClaimStakeholder::getDeleted, 0);
            Criteria<ClaimStakeholder> criteria = new Criteria<>();

            criteria.in(ClaimStakeholder::getRelatedId, claimIdList)
                    .eq(ClaimStakeholder::getPersonType, "MAIN_INSURE")
                    .eq(ClaimStakeholder::getDeleted, 0);
            List<ClaimStakeholder> stakeholderList = claimStakeholderRepository.findByCriteria(criteria);
            stackHolderMap = stakeholderList.stream().collect(Collectors.toMap(ClaimStakeholder::getRelatedId, Function.identity(), (k1, k2) -> k1));
        }

        List<ClaimPushFailResponse> claimPushFailResponseList = convertClaimPushFailResponse(records, stackHolderMap);
        PageResult<ClaimPushFailResponse> pageResult = new PageResult<>();
        pageResult.setCurrPage(pageNumber);
        pageResult.setTotalCount(claimPushFailDOPage.getTotal());
        pageResult.setPageSize(pageSize);
        pageResult.setData(claimPushFailResponseList);
        return pageResult;

    }

    private List<ClaimPushFailResponse> convertClaimPushFailResponse(List<ClaimPushFailDO> records, Map<Long, ClaimStakeholder> stackHolderMap) {
        List<ClaimPushFailResponse> claimPushFailResponseList = new ArrayList<>();
        if (CollectionUtils.isEmpty(records)) {
            return claimPushFailResponseList;
        }
        for (ClaimPushFailDO claimPushFailDO : records) {
            ClaimPushFailResponse response = new ClaimPushFailResponse();

            response.setBizIdentityCode(claimPushFailDO.getBizIdentityCode());
            response.setTenantId(claimPushFailDO.getTenantId());
            response.setErrorType(claimPushFailDO.getErrorType());
            response.setVipSign(claimPushFailDO.getVipSign());
            response.setPolicyNo(claimPushFailDO.getPolicyNo());
            response.setBatchNo(claimPushFailDO.getBatchNo());
            response.setClaimNo(claimPushFailDO.getClaimNo());
            response.setFailTime(claimPushFailDO.getFailTime());
            response.setPushBackReason(claimPushFailDO.getPushBackReason());
            response.setOutInsureName(claimPushFailDO.getOutInsureName());
            response.setOutInsureIdentityTypeCn(claimPushFailDO.getOutInsureIdentityTypeCn());
            response.setOutInsureIdentityNo(claimPushFailDO.getOutInsureIdentityNo());
            if (stackHolderMap != null) {
                ClaimStakeholder claimStakeholder = stackHolderMap.get(claimPushFailDO.getId());
                if (Objects.nonNull(claimStakeholder)) {
                    response.setMainInsureName(claimStakeholder.getName());
                    response.setMainInsureIdentityNo(claimStakeholder.getIdentityNo());
                    response.setMainInsureIdentityTypeCn(claimStakeholder.getIdentityTypeCn());
                }
            }
            response.setInsureName(claimPushFailDO.getInsureName());
            response.setInsuranceName(claimPushFailDO.getInsuranceName());
            response.setBranchName(claimPushFailDO.getBranchName());
            response.setAuditingOperatorName(claimPushFailDO.getAuditingOperatorName());
            claimPushFailResponseList.add(response);
        }
        return claimPushFailResponseList;

    }


    @Override
    public void exportClaimPushFail(ClaimPushFailRequest request, HttpServletResponse response) {
        String batchNo = request.getBatchNo();
        String claimNo = request.getClaimNo();
        String policyNo = request.getPolicyNo();
        String outInsureName = request.getOutInsureName();
        String outInsureIdentityNo = request.getOutInsureIdentityNo();
        String insureName = request.getInsureName();
        String insuranceName = request.getInsuranceName();
        String branchName = request.getBranchName();
        String errorType = request.getErrorType();
        String auditingOperatorName = request.getAuditingOperatorName();
        String vipSign = request.getVipSign();

        Integer pageNumber = request.getPageNumber();
        Integer pageSize = request.getPageSize();
        Page<ClaimPushFailDO> page = new Page<>(1, 10000L);
        Page<ClaimPushFailDO> claimPushFailDOPage = claimMapper.pageClaimPushFailDO(page, batchNo, claimNo, policyNo,
                outInsureName, outInsureIdentityNo, insureName, insuranceName, branchName, errorType,
                auditingOperatorName, vipSign,"已推送", "保司推送失败");

        List<ClaimPushFailDO> records = claimPushFailDOPage.getRecords();
        Map<Long, ClaimStakeholder> stackHolderMap = null;
        if (!CollectionUtils.isEmpty(records)) {
            // 查询主被人信息
            List<Long> claimIdList = records.stream().map(ClaimPushFailDO::getId).toList();
//            LambdaQueryWrapper<ClaimStakeholder> mainInsureQuery = new LambdaQueryWrapper<>();
//            mainInsureQuery.in(ClaimStakeholder::getRelatedId, claimIdList)
//                    .eq(ClaimStakeholder::getDeleted, 0);
            Criteria<ClaimStakeholder> criteria = new Criteria<>();

            criteria.in(ClaimStakeholder::getRelatedId, claimIdList)
                    .eq(ClaimStakeholder::getPersonType, "MAIN_INSURE")
                    .eq(ClaimStakeholder::getDeleted, 0);
            List<ClaimStakeholder> stakeholderList = claimStakeholderRepository.findByCriteria(criteria);
            stackHolderMap = stakeholderList.stream().collect(Collectors.toMap(ClaimStakeholder::getRelatedId, Function.identity(), (k1, k2) -> k1));
        }

        List<ClaimPushFailResponse> claimPushFailResponseList = convertClaimPushFailResponse(records, stackHolderMap);
        // 设置响应头（文件名要编码防止中文乱码）
        try {
            Date date = new Date();
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            String fileName = simpleDateFormat.format(date);
            User currentUser = UserContext.getCurrentUser();
            if (Objects.nonNull(currentUser)) {
                fileName += "-" + currentUser.getLoginName();
            }
            fileName = URLEncoder.encode(fileName, "UTF-8").replaceAll("\\+", "%20");
            response.setContentType("application/vnd.ms-excel");
            response.setCharacterEncoding("utf-8");
            // response.setHeader("Content-Disposition", "attachment;filename*=UTF-8''" + fileName + ".xlsx");
            response.setHeader("Content-Disposition",
                    "attachment; filename=" + fileName + ".xlsx; filename*=UTF-8''" + fileName + ".xlsx");


            // 使用 EasyExcel 写出
            EasyExcel.write(response.getOutputStream(), ClaimPushFailResponse.class)
                    .sheet("Sheet1")
                    .doWrite(claimPushFailResponseList); // 一次性写完
        } catch (Exception e) {
            log.error("", e);
        }

    }

    @Override
    public PageResult<ClaimPushFailOperateResponse> pageClaimPushFailOperate(ClaimPushFailRequest request) {

        String batchNo = request.getBatchNo();
        String claimNo = request.getClaimNo();
        String policyNo = request.getPolicyNo();
        String outInsureName = request.getOutInsureName();
        String outInsureIdentityNo = request.getOutInsureIdentityNo();
        String insureName = request.getInsureName();
        String insuranceName = request.getInsuranceName();
        String branchName = request.getBranchName();
        String errorType = request.getErrorType();
        String auditingOperatorName = request.getAuditingOperatorName();
        String vipSign = request.getVipSign();

        Integer pageNumber = request.getPageNumber();
        Integer pageSize = request.getPageSize();
        Page<ClaimPushFailOperateDO> page = new Page<>(pageNumber, pageSize);
        Page<ClaimPushFailOperateDO> claimPushFailDOPage = claimMapper.pageClaimPushFailOperateDO(page, batchNo, claimNo, policyNo,
                outInsureName, outInsureIdentityNo, insureName, insuranceName, branchName, errorType,
                auditingOperatorName, vipSign,"已推送", "保司推送失败");

        List<ClaimPushFailOperateDO> records = claimPushFailDOPage.getRecords();
        Map<Long, ClaimStakeholder> stackHolderMap = null;
        if (!CollectionUtils.isEmpty(records)) {
            // 查询主被人信息
            List<Long> claimIdList = records.stream().map(ClaimPushFailOperateDO::getId).toList();
            Criteria<ClaimStakeholder> criteria = new Criteria<>();

            criteria.in(ClaimStakeholder::getRelatedId, claimIdList)
                    .eq(ClaimStakeholder::getPersonType, "MAIN_INSURE")
                    .eq(ClaimStakeholder::getDeleted, 0);
            List<ClaimStakeholder> stakeholderList = claimStakeholderRepository.findByCriteria(criteria);
            stackHolderMap = stakeholderList.stream().collect(Collectors.toMap(ClaimStakeholder::getRelatedId, Function.identity(), (k1, k2) -> k1));
        }

        List<ClaimPushFailOperateResponse> claimPushFailResponseList = convertClaimPushFailOperateResponse(records, stackHolderMap);
        PageResult<ClaimPushFailOperateResponse> pageResult = new PageResult<>();
        pageResult.setCurrPage(pageNumber);
        pageResult.setTotalCount(claimPushFailDOPage.getTotal());
        pageResult.setPageSize(pageSize);
        pageResult.setData(claimPushFailResponseList);
        return pageResult;

    }

    private List<ClaimPushFailOperateResponse> convertClaimPushFailOperateResponse(List<ClaimPushFailOperateDO> records, Map<Long, ClaimStakeholder> stackHolderMap) {
        List<ClaimPushFailOperateResponse> claimPushFailResponseList = new ArrayList<>();
        if (CollectionUtils.isEmpty(records)) {
            return claimPushFailResponseList;
        }
        for (ClaimPushFailOperateDO claimPushFailOperateDO : records) {
            ClaimPushFailOperateResponse response = new ClaimPushFailOperateResponse();

            response.setErrorType(claimPushFailOperateDO.getErrorType());
            response.setVipSign(claimPushFailOperateDO.getVipSign());
            response.setPolicyNo(claimPushFailOperateDO.getPolicyNo());
            response.setBatchNo(claimPushFailOperateDO.getBatchNo());
            response.setClaimNo(claimPushFailOperateDO.getClaimNo());
            response.setFailTime(claimPushFailOperateDO.getFailTime());
            response.setCreateTime(claimPushFailOperateDO.getCreateTime());
            response.setPushBackReason(claimPushFailOperateDO.getPushBackReason());
            response.setOutInsureName(claimPushFailOperateDO.getOutInsureName());
            response.setOutInsureIdentityTypeCn(claimPushFailOperateDO.getOutInsureIdentityTypeCn());
            response.setOutInsureIdentityNo(claimPushFailOperateDO.getOutInsureIdentityNo());
            if (stackHolderMap != null) {
                ClaimStakeholder claimStakeholder = stackHolderMap.get(claimPushFailOperateDO.getId());
                if (Objects.nonNull(claimStakeholder)) {
                    response.setMainInsureName(claimStakeholder.getName());
                    response.setMainInsureIdentityNo(claimStakeholder.getIdentityNo());
                    response.setMainInsureIdentityTypeCn(claimStakeholder.getIdentityTypeCn());
                }
            }
            response.setInsureName(claimPushFailOperateDO.getInsureName());
            response.setInsuranceName(claimPushFailOperateDO.getInsuranceName());
            response.setBranchName(claimPushFailOperateDO.getBranchName());
            response.setAuditingOperatorName(claimPushFailOperateDO.getAuditingOperatorName());
            claimPushFailResponseList.add(response);
        }
        return claimPushFailResponseList;

    }


    @Override
    public void exportClaimPushFailOperate(ClaimPushFailRequest request, HttpServletResponse response) {

        String batchNo = request.getBatchNo();
        String claimNo = request.getClaimNo();
        String policyNo = request.getPolicyNo();
        String outInsureName = request.getOutInsureName();
        String outInsureIdentityNo = request.getOutInsureIdentityNo();
        String insureName = request.getInsureName();
        String insuranceName = request.getInsuranceName();
        String branchName = request.getBranchName();
        String errorType = request.getErrorType();
        String auditingOperatorName = request.getAuditingOperatorName();
        String vipSign = request.getVipSign();

        Page<ClaimPushFailOperateDO> page = new Page<>(1, 10000L);
        Page<ClaimPushFailOperateDO> claimPushFailOperateDOPage = claimMapper.pageClaimPushFailOperateDO(page, batchNo, claimNo, policyNo,
                outInsureName, outInsureIdentityNo, insureName, insuranceName, branchName, errorType,
                auditingOperatorName, vipSign,"已推送", "保司推送失败");

        List<ClaimPushFailOperateDO> records = claimPushFailOperateDOPage.getRecords();
        Map<Long, ClaimStakeholder> stackHolderMap = null;
        if (!CollectionUtils.isEmpty(records)) {
            // 查询主被人信息
            List<Long> claimIdList = records.stream().map(ClaimPushFailOperateDO::getId).toList();
            Criteria<ClaimStakeholder> criteria = new Criteria<>();

            criteria.in(ClaimStakeholder::getRelatedId, claimIdList)
                    .eq(ClaimStakeholder::getPersonType, "MAIN_INSURE")
                    .eq(ClaimStakeholder::getDeleted, 0);
            List<ClaimStakeholder> stakeholderList = claimStakeholderRepository.findByCriteria(criteria);
            stackHolderMap = stakeholderList.stream().collect(Collectors.toMap(ClaimStakeholder::getRelatedId, Function.identity(), (k1, k2) -> k1));
        }

        List<ClaimPushFailOperateResponse> claimPushFailResponseList = convertClaimPushFailOperateResponse(records, stackHolderMap);
        // 设置响应头（文件名要编码防止中文乱码）
        try {
            Date date = new Date();
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            String fileName = simpleDateFormat.format(date);
            User currentUser = UserContext.getCurrentUser();
            if (Objects.nonNull(currentUser)) {
                fileName += "-" + currentUser.getLoginName();
            }
            fileName = URLEncoder.encode(fileName, "UTF-8").replaceAll("\\+", "%20");
            response.setContentType("application/vnd.ms-excel");
            response.setCharacterEncoding("utf-8");
            response.setHeader("Content-Disposition",
                    "attachment; filename=" + fileName + ".xlsx; filename*=UTF-8''" + fileName + ".xlsx");

            // 使用 EasyExcel 写出
            EasyExcel.write(response.getOutputStream(), ClaimPushFailOperateResponse.class)
                    .sheet("Sheet1")
                    .doWrite(claimPushFailResponseList); // 一次性写完
        } catch (Exception e) {
            log.error("", e);
        }
    }
}
