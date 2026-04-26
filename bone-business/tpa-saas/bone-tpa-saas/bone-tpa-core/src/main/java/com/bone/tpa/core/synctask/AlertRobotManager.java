package com.bone.tpa.core.synctask;

import com.alibaba.fastjson.JSONObject;
import com.dingtalk.api.DefaultDingTalkClient;
import com.dingtalk.api.DingTalkClient;
import com.dingtalk.api.request.OapiRobotSendRequest;
import com.dingtalk.api.response.OapiRobotSendResponse;
import com.taobao.api.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class AlertRobotManager {

    public static final String FEISHU_MODE="feishu";
    public static final String QIWEI_MODE="qiwei";
    public static final String DINGDING_MODE="dingding";


    @Value("${ding.alert.url:https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=32795269-c1ec-4fd6-9382-1e9eb6a9544d}")
    protected String defaultAlertUrl;


    @Value("${ding.alert.mode:qiwei}")
    protected  String defaultAlertMode;




    @Async
    public void doAlertAsyncDefault( String msg){
        if(QIWEI_MODE.equals(defaultAlertMode)){
            doAlert(QIWEI_MODE,defaultAlertUrl,msg);
        }
        if(DINGDING_MODE.equals(defaultAlertMode)){
            doAlert(DINGDING_MODE,defaultAlertUrl,msg);
        }
        if(FEISHU_MODE.equals(defaultAlertMode)){
            doAlert(FEISHU_MODE,defaultAlertUrl,msg);
        }
    }
    public void doAlert(String alertMode,String alertUrl,String msg) {

        if (StringUtils.isBlank(alertMode)) {
            alertMode = DINGDING_MODE;
        }
        if (StringUtils.isBlank(alertUrl)) {
            return;
        }
        if (StringUtils.isBlank(msg)) {
            return;
        }
        if(!msg.contains("saas")){
            msg = "saas-"+msg;
        }
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String dateStr = "";
        try {
            dateStr = sdf.format(new Date());
        } catch (Exception e) {
        }
        String content = String.format("%s-告警-------- %s", dateStr, msg);

        if (alertMode.equals(FEISHU_MODE)) {

            if (!StringUtils.startsWith(alertUrl, "https://open.feishu.cn/open-apis/bot/v2/hook")) {
                return;
            }
            Map<String, Object> sendMap = new HashMap<>();
            sendMap.put("msg_type", "text");
            Map<String, Object> contentMap = new HashMap<>();
            contentMap.put("text", content);
            sendMap.put("content", contentMap);
            try {
                HttpClientUtils.doPost(alertUrl, JSONObject.toJSONString(sendMap));
                // HttpClientUtils.doPostJson(alertUrl, JSONObject.toJSONString(sendMap));
            } catch (Exception e) {
                log.error("FeishuAlertManagerImpl.send error :", e);
            }
        }
        if (alertMode.equals(QIWEI_MODE)) {

            if (!StringUtils.startsWith(alertUrl, "https://qyapi.weixin.qq.com/cgi-bin/webhook")) {
                return;
            }


            Map<String, Object> sendMap = new HashMap<>();
            sendMap.put("msgtype", "text");
            Map<String, Object> contentMap = new HashMap<>();
            contentMap.put("content", content);
            sendMap.put("text", contentMap);
            try {
                HttpClientUtils.doPost(alertUrl, JSONObject.toJSONString(sendMap));
            } catch (Exception e) {
                log.error("qiwei.send error :", e);
            }
        }
        if (alertMode.equals(DINGDING_MODE)) {

            if (!StringUtils.startsWith(alertUrl, "https://oapi.dingtalk.com/robot")) {
                return;
            }


            DingTalkClient client = new DefaultDingTalkClient(alertUrl);
            OapiRobotSendRequest request = new OapiRobotSendRequest();
            request.setMsgtype("text");
            OapiRobotSendRequest.Text text = new OapiRobotSendRequest.Text();
            text.setContent(content);
            request.setText(text);
            try {
                OapiRobotSendResponse response = client.execute(request);
                log.info("{}", response);
            } catch (ApiException e) {
                e.printStackTrace();
            }
        }
    }

}
