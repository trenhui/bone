package com.bone.tpa.push.service;

import com.bone.tpa.push.bean.PushClaimContext;
import com.bone.tpa.push.dto.PushClaimRequestDTO;
import lombok.extern.slf4j.Slf4j;

/**
 * @Author feihaiming
 * @create 2025/10/16 17:50
 */
@Slf4j
public abstract class PushClaimAction {
    public abstract boolean support(String pushType);
    public void push(PushClaimRequestDTO pushClaimRequestDTO, PushClaimContext pushClaimContext) {
        prePush(pushClaimRequestDTO, pushClaimContext);
        pushData(pushClaimRequestDTO, pushClaimContext);
        postPush(pushClaimRequestDTO, pushClaimContext);
    }

    public abstract void afterCompletion(PushClaimRequestDTO pushClaimRequestDTO, PushClaimContext pushClaimContext);


    public abstract void prePush(PushClaimRequestDTO pushClaimRequestDTO, PushClaimContext pushClaimContext);

    public abstract void pushData(PushClaimRequestDTO pushClaimRequestDTO, PushClaimContext pushClaimContext);

    public abstract void postPush(PushClaimRequestDTO pushClaimRequestDTO, PushClaimContext pushClaimContext);
}
