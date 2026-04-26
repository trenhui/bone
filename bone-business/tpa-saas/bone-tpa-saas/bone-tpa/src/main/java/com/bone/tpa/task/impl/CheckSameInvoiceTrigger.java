package com.bone.tpa.task.impl;

import com.alibaba.fastjson.JSONObject;
import com.bone.core.util.SpringContextUtils;
import com.bone.tpa.api.ApiResult;
import com.bone.tpa.api.enums.HintMsgType;
import com.bone.tpa.claim.domain.service.ClaimService;
import com.bone.tpa.claim.domain.service.HintService;
import com.bone.tpa.core.synctask.AlertRobotManager;
import com.bone.tpa.core.synctask.SyncTaskTemplate;
import com.bone.tpa.facade.feign.TpaDataSyncFeign;
import com.bone.tpa.facade.request.TpaCheckSameBillRequest;
import com.bone.tpa.facade.vo.CheckInvoiceTipsData;
import com.bone.tpa.facade.vo.CheckSameBillResponse;
import com.bone.tpa.sdk.claim.model.Claim;
import com.bone.tpa.sdk.claim.model.SyncTask;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * saas发起发票查重，通过异步任务调度
 */
@Slf4j
@Service
public class CheckSameInvoiceTrigger extends SyncTaskTemplate {
    public static final String pukang_source = "pukang";
    public static final String waibao_source = "waibao";

    @Autowired
    private TpaDataSyncFeign tpaDataSyncFeign;

    @Autowired
    private   ClaimService claimService;

    @Autowired
    private HintService hintService;

    @Override
    public String getBizType() {
        return "check-same-invoice";
    }

    @Override
    public String getAlertUrl() {
        return dingAlertUrl;
    }

    @Override
    public String getAlertMode() {
        return AlertRobotManager.QIWEI_MODE;
    }

    @XxlJob("checkSameInvoiceJob")
    public void checkSameInvoiceJobXxljob(){
        this.execute();
    }

    @Override
    public void syncOneData(SyncTask task) {
        String claimNo = task.getData();

        //为啥要这调用，其实为了引发事务控制
        SpringContextUtils.getBean(CheckSameInvoiceTrigger.class).doCheck(claimNo);
    }

    /**
     * 进行查重
     */
    @Transactional(rollbackFor = Exception.class)
    public void doCheck(String claimNo) {
        //调用发票查重接口
        TpaCheckSameBillRequest queryRequest = new TpaCheckSameBillRequest();
        queryRequest.setClaimNumber(claimNo);
        ApiResult<List<CheckSameBillResponse>> remoteRs = tpaDataSyncFeign.saasGetCheckSameBillData(queryRequest);
        if(!remoteRs.isSuccess()){
            alertRobotManager.doAlertAsyncDefault(
                    String.format("赔案号 %s 发票查重失败，接口返回错误:%s",claimNo, JSONObject.toJSONString(remoteRs)));
            throw new RuntimeException("调用tpa发票查重接口失败:"+remoteRs.getMessage());
        }

        StringBuilder tip = new StringBuilder();

        //如果没有查重信息就回复特定文案。
        if (remoteRs.getData().isEmpty()) {
            tip.append("发票查重疑似结果：无重复和疑似");
            tip.append("\n\n\n");
        } else {
            //将获取的数据处理一下，放到hint表里。
            Map<Integer, List<CheckInvoiceTipsData>> tipMap = remoteRs.getData().stream().collect(
                    Collectors.toMap(CheckSameBillResponse::getType, CheckSameBillResponse::getTips));


            //Type = 1 代表查重
            List<CheckInvoiceTipsData> sameTipList = tipMap.get(1);
            if (sameTipList != null && !sameTipList.isEmpty()) {
                tip.append("发票查重：");
                for (CheckInvoiceTipsData tipsData : sameTipList) {
                    tip.append("\n");
                    tip.append(tipsData.getBeforeTips());
                    tip.append("  ");
                    tip.append(tipsData.getNewOrOldTips());
                    tip.append(" ");
                    tip.append(tipsData.getClaimNumber());
                    tip.append(" ");
                    tip.append(tipsData.getAfterTips());
                }
                tip.append("\n\n\n");
            }

            //Type = 3 代表疑似
            List<CheckInvoiceTipsData> susTipList = tipMap.get(3);
            if (susTipList != null && !susTipList.isEmpty()) {
//            if(tipMap.containsKey(1)) {
//                tip.append("\n\n\n");
//            }
                tip.append("发票疑似：");
                for (CheckInvoiceTipsData tipsData : susTipList) {
                    tip.append("\n");
                    tip.append(tipsData.getBeforeTips());
                    tip.append("  ");
                    tip.append(tipsData.getNewOrOldTips());
                    tip.append(" ");
                    tip.append(tipsData.getClaimNumber());
                    tip.append(" ");
                    tip.append(tipsData.getAfterTips());
                }
                tip.append("\n\n\n");
            }


            //Type = 2 代表特殊
            List<CheckInvoiceTipsData> spTipList = tipMap.get(2);
            if (spTipList != null && !spTipList.isEmpty()) {
//            if(tipMap.containsKey(1)) {
//                tip.append("\n\n\n");
//            }
                tip.append("发票特殊：");
                for (CheckInvoiceTipsData tipsData : spTipList) {
                    tip.append("\n");
                    tip.append(tipsData.getBeforeTips());
                    tip.append("  ");
                    tip.append(tipsData.getNewOrOldTips());
                    tip.append(" ");
                    tip.append(tipsData.getClaimNumber());
                    tip.append(" ");
                    tip.append(tipsData.getAfterTips());
                }
                tip.append("\n\n\n");
            }
        }

        //增加本次操作完成的时间
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        tip.append("查重时间：");
        tip.append(sdf.format(new Date()));


        Map<String, String> hintMsg = new HashMap<>();
        hintMsg.put("checkSame", tip.toString());


        //这两个是一样的，懒了
        Claim claim = claimService.getById(Long.valueOf(claimNo));

        //然后插入hint表中
        hintService.appendHint(claim.getId(), HintMsgType.CLAIM_DETAIL, hintMsg, claim.getId());

    }


}
