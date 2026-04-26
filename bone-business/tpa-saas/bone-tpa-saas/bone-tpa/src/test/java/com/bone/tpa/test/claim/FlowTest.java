package com.bone.tpa.test.claim;

import com.bone.tpa.claim.flow.stage.InputStageService;
import com.bone.tpa.claim.flow.jump.InputFlowFireService;
import com.bone.tpa.claim.flow.jump.QualityFlowFireService;
import com.bone.tpa.sdk.dao.ClaimRepository;
import com.bone.tpa.test.BaseTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;

import java.util.Date;

public class FlowTest extends BaseTest {
    @Autowired
    private InputFlowFireService inputFlowFireService;

    @Autowired
    private InputStageService inputStageService;

    @Autowired
    private QualityFlowFireService qualityFlowFireService;
    @Autowired
    private ClaimRepository claimRepository;
    @Test
    public void testJump(){

        Long claimNumber = 255100469001L;
        String bizIdentityCode = "4:yccc:yccc-zj:awdas112";
        Assert.notNull(inputFlowFireService, "inputFlowFireService is null");
        boolean ret = qualityFlowFireService.canJoin(claimRepository.findById(claimNumber));
        System.out.println(ret);
    }


    @Test
    public void testInterface(){
        Long claimNumber = 255100469001L;
        inputStageService.inputDealerApply(claimRepository.findById(claimNumber),new Date());
    }
}
