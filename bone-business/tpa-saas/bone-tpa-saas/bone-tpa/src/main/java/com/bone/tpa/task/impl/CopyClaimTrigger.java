package com.bone.tpa.task.impl;

import com.alibaba.fastjson.JSON;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.core.util.PkListUtil;
import com.bone.core.util.PkStringUtil;
import com.bone.core.util.SpringContextUtils;
import com.bone.tpa.api.ApiResult;
import com.bone.tpa.api.enums.EventType;
import com.bone.tpa.api.enums.PKImageMapEnum;
import com.bone.tpa.api.vo.ClaimDetailSyncVO;
import com.bone.tpa.claim.application.enums.CommonLogType;
import com.bone.tpa.claim.domain.service.ClaimCopyLogService;
import com.bone.tpa.claim.domain.service.ClaimService;
import com.bone.tpa.claim.domain.service.CommonLogService;
import com.bone.tpa.claim.sync.ClaimToTpaChangeService;
import com.bone.tpa.core.synctask.SyncTaskTemplate;
import com.bone.tpa.core.util.localImageTool.request.ClassifyParam;
import com.bone.tpa.core.util.localImageTool.response.ImageToolResponse;
import com.bone.tpa.facade.feign.TpaDataSyncFeign;
import com.bone.tpa.facade.request.EventOtherRequest;
import com.bone.tpa.facade.request.TpaAddLogRequest;
import com.bone.tpa.facade.request.TpaSubmitClaimResult;
import com.bone.tpa.facade.vo.InsuranceCompanyImageVO;
import com.bone.tpa.sdk.claim.enums.ClaimStageEnum;
import com.bone.tpa.sdk.claim.enums.ClaimStatusEnum;
import com.bone.tpa.sdk.claim.model.Claim;
import com.bone.tpa.sdk.claim.model.ClaimCopyLog;
import com.bone.tpa.sdk.claim.model.ClaimImage;
import com.bone.tpa.sdk.claim.model.SyncTask;
import com.bone.tpa.sdk.dao.ClaimRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.text.MessageFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 赔案复制完成后，同步模块
 */
@Slf4j
@Service
public class CopyClaimTrigger extends SyncTaskTemplate {
    @Autowired
    private CommonLogService commonLogService;

    @Autowired
    private TpaDataSyncFeign tpaDataSyncFeign;
    @Autowired
    private ClaimToTpaChangeService toTpaChangeService;

    @Autowired
    private ClaimCopyLogService claimCopyLogService;

    @Autowired
    private ClaimService claimService;

    @Override
    public String getBizType() {
        return "copy-claim-sync";
    }

    @Scheduled(cron = "0 0/15 * * * *")
    public void doRetry(){
        log.info("doRetry start,{}",this.getClass().getName());
        this.execute();
    }


    @Override
    public void syncOneData(SyncTask task) {
        Long claimNumber = null;
        try {
            claimNumber = Long.valueOf(task.getData());
            SpringContextUtils.getBean(CopyClaimTrigger.class).syncDataToTpa(claimNumber);

            TpaAddLogRequest tpaAddLogRequest = new TpaAddLogRequest();
            tpaAddLogRequest.setClaimNumber( claimNumber);
            tpaAddLogRequest.setOperation("复制赔案同步完成");
            tpaAddLogRequest.setRemark("");
            tpaAddLogRequest.setCreateBy("System");
            tpaAddLogRequest.setCreateTime( (new Date()).getTime());
            tpaDataSyncFeign.addLog(tpaAddLogRequest);
        } catch (Exception e) {
            log.error("复制赔案同步完成发生异常,claimNumber:" + claimNumber, e);
            alertRobotManager.doAlertAsyncDefault(
                    "复制赔案失败, claim="+task.getData()+",taskId:"+task.getId()+ ",traceId:" + MDC.get("traceId"));
            throw e;
        }
    }



    @Transactional(rollbackFor = Throwable.class)
    public void syncDataToTpa(Long claimId) {
        TpaSubmitClaimResult request = new   TpaSubmitClaimResult ();
        request.setClaimNumber(claimId);
        request.setEventType(Integer.valueOf(EventType.saas通知tpa复制赔案.getCode()));

        ClaimCopyLog claimCopyLog = claimCopyLogService.getClaimCopyLogByNewId(claimId);
        if (claimCopyLog != null) {
            request.setOldClaimNumber(claimCopyLog.getOldClaimId());
        }

        Claim claim = claimService.getById(claimId);

        EventOtherRequest eventOtherRequest = new EventOtherRequest();
        request.setEventOtherRequest(eventOtherRequest);
        eventOtherRequest.setAssignStage(claim.getStage());
        eventOtherRequest.setClaimStatus(Integer.valueOf(claim.getStatus()));
        eventOtherRequest.setAssignTag(true);
        eventOtherRequest.setAssignStrategy("2");
        eventOtherRequest.setAssignOperatorGroupId(claim.getOperatorOrgId());
        eventOtherRequest.setAssignOperatorGroupName(claim.getOperatorOrgName());
        eventOtherRequest.setAssignOperatorId(claim.getOperatorUserId());
        eventOtherRequest.setAssignOperatorName(claim.getOperatorUserName());

        TpaAddLogRequest tpaAddLogRequest = new TpaAddLogRequest();
        tpaAddLogRequest.setClaimNumber( claimId );
        tpaAddLogRequest.setOperation("saas赔案复制");
        tpaAddLogRequest.setRemark("");
        tpaAddLogRequest.setCreateBy("System");
        tpaAddLogRequest.setCreateTime( (new Date()).getTime());
        eventOtherRequest.getOperationLogList().add(tpaAddLogRequest);

        request.setClaimInfo(toTpaChangeService.toSyncVo(claimId));
        ApiResult<Map<String,Object>>  remoteRs= tpaDataSyncFeign.submitClaimResult(request);
        commonLogService.addClaimLogAsync(claimId, CommonLogType.TO_TPA_LOG, "复制赔案同步至tpa, remoteRs:{}",
                remoteRs);

        if (!remoteRs.isSuccess()) {
            throw new RuntimeException("复制赔案同步至tpa，通知tpa失败:" + remoteRs.getMessage());
        }
    }
}
