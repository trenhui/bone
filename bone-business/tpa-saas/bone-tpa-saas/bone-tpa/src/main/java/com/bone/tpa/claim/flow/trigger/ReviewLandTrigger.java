package com.bone.tpa.claim.flow.trigger;

import com.alibaba.fastjson.JSONObject;
import com.bone.tpa.claim.flow.jump.ApproveReviewFlowFireService;
import com.bone.tpa.core.synctask.SyncTaskTemplate;
import com.bone.tpa.sdk.claim.model.Claim;
import com.bone.tpa.sdk.claim.model.SyncTask;
import com.bone.tpa.sdk.dao.ClaimRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ReviewLandTrigger  extends SyncTaskTemplate {
    @Autowired
    private  ApproveReviewFlowFireService reviewFlowFireService;
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
        return "ReviewLandTrigger";
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
        Claim claim =  claimRepository.findById(jsonObject.getLong("claimNumber"));


        reviewFlowFireService.doLandEvent(claim);
    }
}
