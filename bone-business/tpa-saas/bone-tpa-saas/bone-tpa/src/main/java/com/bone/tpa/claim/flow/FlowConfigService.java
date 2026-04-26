package com.bone.tpa.claim.flow;

import com.alibaba.fastjson.JSONObject;
import com.bone.tpa.sdk.claim.model.ClaimFlowConfig;
import com.bone.tpa.sdk.dao.biz.ClaimFlowConfigBiz;
import com.bone.tpa.sdk.vo.*;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

@Service
public class FlowConfigService {
    @Autowired
    ClaimFlowConfigBiz flowConfigBiz;

    public ClaimFlowConfigVO queryCurrentConfig(String bizIdentityCode) {
        Assert.notNull(bizIdentityCode, "bizIdentityCode is null");
        ClaimFlowConfig  config =  flowConfigBiz.getActiveByIdentityCode(bizIdentityCode);

        if( config != null){
            return convert(config);
        }

        return  null;
    }

    private ClaimFlowConfigVO convert(ClaimFlowConfig config){
        ClaimFlowConfigVO rs = new ClaimFlowConfigVO();

        rs.setBizIdentityCode(config.getBizIdentityCode());
        rs.setStatus(config.getStatus());
        if(StringUtils.isBlank(config.getFlowConfig())){
            config.setFlowConfig("{}");
        }
        if(StringUtils.isBlank(config.getPreCheckConfig())){
            config.setPreCheckConfig("{}");
        }

        if(StringUtils.isBlank(config.getInputConfig())){
            config.setInputConfig("{}");
        }
        if(StringUtils.isBlank(config.getQualityConfig())){
            config.setQualityConfig("{}");
        }
        if(StringUtils.isBlank(config.getApproveConfig())){
            config.setApproveConfig("{}");
        }
        if(StringUtils.isBlank(config.getApproveCheckConfig())){
            config.setApproveCheckConfig("{}");
        }
        if(StringUtils.isBlank(config.getPushConfig())){
            config.setPushConfig("{}");
        }

        rs.setFlowConfigVO(JSONObject.parseObject(config.getFlowConfig(),FlowConfigVO.class));
        rs.setPreCheckConfigVO(JSONObject.parseObject(config.getPreCheckConfig(), PreCheckConfigVO.class));
        rs.setInputConfigVO(JSONObject.parseObject(config.getInputConfig(), InputConfigVO.class));
        rs.setQualityConfigVO(JSONObject.parseObject(config.getQualityConfig(), QualityConfigVO.class));
        rs.setApproveConfigVO(JSONObject.parseObject(config.getApproveConfig(),ApproveConfigVO.class));
        rs.setApproveCheckConfigVO(JSONObject.parseObject(config.getApproveCheckConfig(),ApproveCheckConfigVO.class));


        return rs;
    }
}
