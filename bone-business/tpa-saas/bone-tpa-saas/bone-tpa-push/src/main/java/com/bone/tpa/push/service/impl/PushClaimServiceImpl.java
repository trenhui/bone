package com.bone.tpa.push.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.alibaba.fastjson.JSON;
import com.bone.tpa.push.bean.PushClaimBean;
import com.bone.tpa.push.service.PushClaimService;
import com.bone.tpa.push.task.PushClaimTrigger;
import com.bone.tpa.sdk.claim.enums.PkPushStatusEnum;
import com.bone.tpa.sdk.claim.model.Claim;
import com.bone.tpa.sdk.service.ClaimInfoService;
import com.bone.tpa.sdk.util.SpringContextUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static com.bone.tpa.sdk.claim.enums.ClaimStatusEnum.COMPLETE_AUDIT;

@Component
@Slf4j
public class PushClaimServiceImpl implements PushClaimService {

    @Autowired
    private ClaimInfoService claimInfoService;

    @Override
    public void push(Long claimNo, String pushType) {
        Claim claim = claimInfoService.getClaim(claimNo);
        if (ObjectUtil.notEqual(claim.getStatus(), COMPLETE_AUDIT.getCode())) {
            log.warn("claim status error. claimNo: {}, status: {}", claimNo, claim.getStatus());
            return;
        }
        if (ObjectUtil.notEqual(claim.getPkPushStatus(), PkPushStatusEnum.WAITING_PUSH.getName())) {
            log.warn("claim pkPushStatus error. claimNo: {}, pkPushStatus: {}", claimNo, claim.getPkPushStatus());
            return;
        }
        PushClaimBean e = new PushClaimBean();
        e.setClaimNo(claimNo);
        e.setPushType(pushType);
        SpringContextUtils.getBean(PushClaimTrigger.class).addJobAndTryFire(JSON.toJSONString(e), 5, 30);
    }
}
