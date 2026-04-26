package com.bone.tpa.intelligent.adjustment.service;

import com.bone.tpa.sdk.adjustment.request.ClaimAdjustmentRequest;
import com.bone.tpa.sdk.adjustment.response.ClaimAdjustmentResponse;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    /**
     * 发送理赔结果通知，告知相关人员处理结果。
     * @param request 理赔请求
     * @param response 理算结果
     */
    public void sendNotification(ClaimAdjustmentRequest request, ClaimAdjustmentResponse response) {
        // 实现通知逻辑，例如发送邮件或推送通知 todo
        System.out.println("发送理赔结果通知，保单号: " + request.getPolicyNo() + ", 理赔金额: " + response.getPayoutAmount());
    }
}
