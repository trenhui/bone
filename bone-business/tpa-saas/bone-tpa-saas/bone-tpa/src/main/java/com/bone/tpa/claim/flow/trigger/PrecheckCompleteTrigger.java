package com.bone.tpa.claim.flow.trigger;

import com.alibaba.fastjson.JSONObject;
import com.bone.core.util.BizContextUtils;
import com.bone.core.util.PkListUtil;
import com.bone.tpa.claim.domain.service.ClaimService;
import com.bone.tpa.claim.flow.jump.InputFlowFireService;
import com.bone.tpa.core.synctask.AlertRobotManager;
import com.bone.tpa.core.synctask.SyncTaskTemplate;
import com.bone.tpa.sdk.claim.enums.ClaimStatusEnum;
import com.bone.tpa.sdk.claim.model.Claim;
import com.bone.tpa.sdk.claim.model.SyncTask;
import com.bone.tpa.sdk.dao.ClaimRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
@Slf4j
public class PrecheckCompleteTrigger extends SyncTaskTemplate {
    @Autowired
     private InputFlowFireService inputFlowFireService;
    @Autowired
    private ClaimRepository claimRepository;
    @Autowired
    private ClaimService claimService;
    /**
     * 所属业务
     *
     * @return
     */
    @Override
    public String getBizType() {
        return "PrecheckCompleteTrigger";
    }
    @Scheduled(cron = "0 0/15 * * * *")
    public void doRetry(){
        log.info("doRetry start,{}",this.getClass().getName());
        this.execute();
    }
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
     *
     * @param task
     */
    @Override
    public void syncOneData(SyncTask task) {
        String data = task.getData();
        JSONObject jsonObject =   JSONObject.parseObject(data);
        Long claimId = jsonObject.getLong("claimNumber");
        Claim claim = claimRepository.findById(claimId);

        inputFlowFireService.fire(claimRepository.findById(claimId));

        //这个就不要加tpa日志了吧
        claimService.notifyTpaChangeStatus(claim,new Date(),
                ClaimStatusEnum.COMPLETE_PRE_ADUIT, PkListUtil.newArrayList(),
                BizContextUtils.getUser(), null
                ,"初审完成",true);
    }
}
