package com.bone.tpa.test;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.bone.core.result.Result;
import com.bone.tpa.claim.adapter.ClaimController;
import com.bone.tpa.claim.adapter.ClaimFlowController;
import com.bone.tpa.claim.application.request.*;
import com.bone.tpa.sdk.claim.enums.ClaimFlowStatus;
import com.bone.tpa.sdk.claim.model.ClaimFlowConfig;
import com.bone.tpa.sdk.dao.biz.ClaimFlowConfigBiz;
import com.bone.tpa.sdk.vo.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;

import java.util.Map;

public class ClaimFlowControllerTest extends  BaseTest{
    @Autowired
    private ClaimFlowController claimFlowController;
    @Autowired
    private ClaimFlowConfigBiz flowConfigBiz;

    @Autowired
    private ClaimController claimController;
    private String bizIdentityCode="1:12412";
    @Test
    public void testflowConfigPageInit(){
        Assert.notNull(claimFlowController,"claimFlowController is null");
        Result<Map<String,Object>>  rs = claimFlowController.flowConfigPageInit(bizIdentityCode);
        System.out.println(JSONObject.toJSONString(rs, SerializerFeature.DisableCircularReferenceDetect));
    }


    @Test
    public void testSaveFlowNode(){
        Result<Map<String,Object>>  rs = claimFlowController.flowConfigPageInit(bizIdentityCode);
        ClaimFlowConfigVO configVO = (ClaimFlowConfigVO)rs.getData().get("config");
        FlowConfigVO configVO1 = configVO.getFlowConfigVO();
        SaveFlowConfigVORequest request = new SaveFlowConfigVORequest();
        request.setBizIdentityCode(bizIdentityCode);
        request.setConfig(configVO1);
        System.out.println(JSONObject.toJSONString(request, SerializerFeature.DisableCircularReferenceDetect));
        Result<Map<String,Object>> saveRs =  claimFlowController.saveFlowConfig(request);
        Assert.isTrue(saveRs.getSuccess(),"save flow config failed");
        System.out.println(JSONObject.toJSONString(saveRs, SerializerFeature.DisableCircularReferenceDetect));
        ClaimFlowConfig draft =  flowConfigBiz.getByIdentityCodeAndStatus(bizIdentityCode, ClaimFlowStatus.Draft);
        System.out.println(draft.getFlowConfig());
    }
    @Test
    public void testSaasPrecheckConfig(){
        Result<Map<String,Object>>  rs = claimFlowController.flowConfigPageInit(bizIdentityCode);
        ClaimFlowConfigVO configVO = (ClaimFlowConfigVO)rs.getData().get("config");
        PreCheckConfigVO configVO1 = configVO.getPreCheckConfigVO();
        SavePreCheckConfigRequest request = new SavePreCheckConfigRequest();
        request.setBizIdentityCode(bizIdentityCode);
        request.setConfig(configVO1);
        System.out.println(JSONObject.toJSONString(request, SerializerFeature.DisableCircularReferenceDetect));
        Result<Map<String,Object>> saveRs =  claimFlowController.savePreCheckConfigVO(request);
        Assert.isTrue(saveRs.getSuccess(),"save flow config failed");
        System.out.println(JSONObject.toJSONString(saveRs, SerializerFeature.DisableCircularReferenceDetect));
        ClaimFlowConfig draft =  flowConfigBiz.getByIdentityCodeAndStatus(bizIdentityCode, ClaimFlowStatus.Draft);
        System.out.println(draft.getInputConfig());
    }


    @Test
    public void testSaasQualityConfig(){
        Result<Map<String,Object>>  rs = claimFlowController.flowConfigPageInit(bizIdentityCode);
        ClaimFlowConfigVO configVO = (ClaimFlowConfigVO)rs.getData().get("config");
        QualityConfigVO configVO1 = configVO.getQualityConfigVO();
        SaveQualityConfigRequest request = new SaveQualityConfigRequest();
        request.setBizIdentityCode(bizIdentityCode);
        request.setConfig(configVO1);
        System.out.println(JSONObject.toJSONString(request, SerializerFeature.DisableCircularReferenceDetect));
        Result<Map<String,Object>> saveRs =  claimFlowController.saveQualityCheckConfigVO(request);
        Assert.isTrue(saveRs.getSuccess(),"save flow config failed");
        System.out.println(JSONObject.toJSONString(saveRs, SerializerFeature.DisableCircularReferenceDetect));
        ClaimFlowConfig draft =  flowConfigBiz.getByIdentityCodeAndStatus(bizIdentityCode, ClaimFlowStatus.Draft);
        System.out.println(draft.getInputConfig());
    }
    @Test
    public void testSaasApproveConfig(){
        Result<Map<String,Object>>  rs = claimFlowController.flowConfigPageInit(bizIdentityCode);
        ClaimFlowConfigVO configVO = (ClaimFlowConfigVO)rs.getData().get("config");
        ApproveConfigVO configVO1 = configVO.getApproveConfigVO();
        SaveApproveConfigRequest request = new SaveApproveConfigRequest();
        request.setBizIdentityCode(bizIdentityCode);
        request.setConfig(configVO1);
        System.out.println(JSONObject.toJSONString(request, SerializerFeature.DisableCircularReferenceDetect));
        Result<Map<String,Object>> saveRs =  claimFlowController.saveApproveConfigVO(request);
        Assert.isTrue(saveRs.getSuccess(),"save flow config failed");
        System.out.println(JSONObject.toJSONString(saveRs, SerializerFeature.DisableCircularReferenceDetect));
        ClaimFlowConfig draft =  flowConfigBiz.getByIdentityCodeAndStatus(bizIdentityCode, ClaimFlowStatus.Draft);
        System.out.println(draft.getInputConfig());
    }



    @Test
    public void testSaasApproveCheckConfig(){
        Result<Map<String,Object>>  rs = claimFlowController.flowConfigPageInit(bizIdentityCode);
        ClaimFlowConfigVO configVO = (ClaimFlowConfigVO)rs.getData().get("config");
        ApproveCheckConfigVO configVO1 = configVO.getApproveCheckConfigVO();
        SaveApproveCheckConfigRequest request = new SaveApproveCheckConfigRequest();
        request.setBizIdentityCode(bizIdentityCode);
        request.setConfig(configVO1);
        System.out.println(JSONObject.toJSONString(request, SerializerFeature.DisableCircularReferenceDetect));
        Result<Map<String,Object>> saveRs =  claimFlowController.saveApproveCheckConfigVO(request);
        Assert.isTrue(saveRs.getSuccess(),"save flow config failed");
        System.out.println(JSONObject.toJSONString(saveRs, SerializerFeature.DisableCircularReferenceDetect));
        ClaimFlowConfig draft =  flowConfigBiz.getByIdentityCodeAndStatus(bizIdentityCode, ClaimFlowStatus.Draft);
        System.out.println(draft.getInputConfig());
    }



    @Test
    public void testDeployConfig(){
        Result<Map<String,Object>>  rs = claimFlowController.flowConfigPageInit(bizIdentityCode);
        ClaimFlowConfigVO configVO = (ClaimFlowConfigVO)rs.getData().get("config");
        ApproveCheckConfigVO configVO1 = configVO.getApproveCheckConfigVO();
        SaveApproveCheckConfigRequest request = new SaveApproveCheckConfigRequest();
        request.setBizIdentityCode(bizIdentityCode);
        request.setConfig(configVO1);
        System.out.println(JSONObject.toJSONString(request, SerializerFeature.DisableCircularReferenceDetect));
        Result<Map<String,Object>> saveRs =  claimFlowController.saveApproveCheckConfigVO(request);
        Assert.isTrue(saveRs.getSuccess(),"save flow config failed");
        System.out.println(JSONObject.toJSONString(saveRs, SerializerFeature.DisableCircularReferenceDetect));
        ClaimFlowConfig draft =  flowConfigBiz.getByIdentityCodeAndStatus(bizIdentityCode, ClaimFlowStatus.Draft);
        System.out.println(draft.getInputConfig());

        Result<Map<String,Object>> deployRs =  claimFlowController.deployFlowConfig(bizIdentityCode);
        System.out.println(JSONObject.toJSONString(deployRs, SerializerFeature.DisableCircularReferenceDetect));
    }



    @Test
    public void testSaveInutConfig(){
        Result<Map<String,Object>>  rs = claimFlowController.flowConfigPageInit(bizIdentityCode);
        ClaimFlowConfigVO configVO = (ClaimFlowConfigVO)rs.getData().get("config");
        InputConfigVO configVO1 = configVO.getInputConfigVO();
        SaveInputConfigRequest request = new SaveInputConfigRequest();
        request.setBizIdentityCode(bizIdentityCode);
        request.setConfig(configVO1);
        System.out.println(JSONObject.toJSONString(request, SerializerFeature.DisableCircularReferenceDetect));
        Result<Map<String,Object>> saveRs =  claimFlowController.saveInputCheckConfigVO(request);
        Assert.isTrue(saveRs.getSuccess(),"save flow config failed");
        System.out.println(JSONObject.toJSONString(saveRs, SerializerFeature.DisableCircularReferenceDetect));
        ClaimFlowConfig draft =  flowConfigBiz.getByIdentityCodeAndStatus(bizIdentityCode, ClaimFlowStatus.Draft);
        System.out.println(draft.getInputConfig());
    }



    @Test
    public void testBackNodePageInit(){
        Result<Map<String,Object>>  rs =  claimController.returnNodePageInit(254700556014L);

        System.out.println(JSONObject.toJSONString(rs));
    }

}
