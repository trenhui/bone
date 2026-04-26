package com.bone.tpa.claim.flow.trigger;

import com.alibaba.fastjson.JSONObject;
import com.bone.tpa.claim.flow.stage.QualityStageService;
import com.bone.tpa.core.synctask.SyncTaskTemplate;
import com.bone.tpa.sdk.claim.model.SyncTask;
import com.bone.tpa.sdk.dao.ClaimRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class QualityCompleteTrigger extends SyncTaskTemplate {
    @Autowired
    private QualityStageService qualityStageService;
    @Autowired
    private ClaimRepository claimRepository;
    @Scheduled(cron = "0 0/15 * * * *")
    public void doRetry(){
        log.info("doRetry start,{}",this.getClass().getName());
        this.execute();
    }

    /**
     * 所属业务
     *
     * @return
     */
    @Override
    public String getBizType() {
        return "QualityCompleteTrigger";
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
        Long claimNumber = jsonObject.getLong("claimNumber");
        String operator = jsonObject.getString("operator");
        qualityStageService.qualityCompleteTriggerAction(claimNumber, operator,task.getCreateTime());
    }
}
