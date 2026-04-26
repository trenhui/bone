package com.bone.tpa.push.task;

import com.alibaba.fastjson.JSON;
import com.bone.tpa.core.synctask.AlertRobotManager;
import com.bone.tpa.core.synctask.SyncTaskTemplate;
import com.bone.tpa.push.bean.PushClaimBean;
import com.bone.tpa.push.service.PushClaimInnerService;
import com.bone.tpa.push.util.CommonTool;
import com.bone.tpa.sdk.claim.model.SyncTask;
import com.bone.tpa.sdk.dao.CommonLogRespository;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class PushClaimTrigger extends SyncTaskTemplate {

    @Autowired
    private PushClaimInnerService pushClaimInnerService;

    @Autowired
    private CommonLogRespository logRespository;

    @XxlJob("pushClaim2TuisongJob")
    public void pushClaim2TuisongTriggerXxljob(){
        this.execute();
    }

    /**
     * 所属业务
     *
     * @return
     */
    @Override
    public String getBizType() {
        return "push_claim_to_tuisong";
    }

    @Scheduled(cron = "0 0/2 * * * *")
    public void doRetry(){
        log.info("doRetry start,{}",this.getClass().getName());
        this.execute();
    }

    /**
     * 获取告警url
     *
     * @return
     */
    @Override
    public String getAlertUrl() {
        return dingAlertUrl;
    }

    @Override
    public String getAlertMode() {
        return AlertRobotManager.QIWEI_MODE;
    }

    /**
     * 执行一个任务
     * 在这里加Transaction标记没用
     * 使用 PushClaimTrigger.addJobAndTryFire(e, 3, 10); 发起推送
     * e是PushClaimBean对象,pushType见com.bone.tpa.push.enums.PushTypeEnum
     * @param task
     */
    @Override
    public void syncOneData(SyncTask task) {
        PushClaimBean pushClaimBean = null;
        try {
            log.info("start PushClaim ,taskId : {},claim:{}",task.getId(), task.getData());
            pushClaimBean = JSON.parseObject(task.getData(), PushClaimBean.class);
            pushClaimInnerService.push(pushClaimBean.getClaimNo(), pushClaimBean.getPushType());
        } catch (Throwable e) {
            alertRobotManager.doAlertAsyncDefault(
                    "推送赔案失败, claim="+task.getData()+",taskId:"+task.getId()+ ",traceId:" + MDC.get("traceId"));
            CommonTool.addClaimLog(logRespository, pushClaimBean.getClaimNo(), "PUSH", ExceptionUtils.getStackTrace(e));
            throw e;
        }
        log.info("end PushClaim ,taskId : {},claim:{},traceId:{}",task.getId(),task.getData(),MDC.get("traceId"));
    }
}
