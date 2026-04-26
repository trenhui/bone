package com.bone.tpa.sdk.dao.biz;

import com.alibaba.fastjson.JSONObject;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.tpa.sdk.claim.enums.ClaimFlowStatus;
import com.bone.tpa.sdk.claim.model.ClaimFlowConfig;
import com.bone.tpa.sdk.dao.ClaimFlowConfigRepository;
import com.bone.tpa.sdk.vo.*;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

@Service
public class ClaimFlowConfigBiz {
    @Autowired
    ClaimFlowConfigRepository flowConfigRepository;

    /**
     * 获取当前有效的配置
     * @param bizIdentityCode
     * @return
     */
    public ClaimFlowConfig getActiveByIdentityCode(String bizIdentityCode){

        return getByIdentityCodeAndStatus(bizIdentityCode,ClaimFlowStatus.Active);
    }



    /**
     * 根据状态获取配置
     * @param bizIdentityCode
     * @param status
     * @return
     */
    public ClaimFlowConfig getByIdentityCodeAndStatus(String bizIdentityCode,
                                                            ClaimFlowStatus status){
        Assert.notNull(bizIdentityCode,"bizIdentityCode must not be null");
        Assert.notNull(status,"status must not be null");
        Criteria<ClaimFlowConfig> criteria = Criteria.create();
        criteria.eq(ClaimFlowConfig::getBizIdentityCode,bizIdentityCode);
        criteria.eq(ClaimFlowConfig::getStatus,status.getCode());

        ClaimFlowConfig config =   flowConfigRepository.findOneByCriteria(criteria);

        return config;
    }

    /**
     * 根据id加载
     * @param id
     * @return
     */
    public ClaimFlowConfigVO convertById(Long id){
        return convert(getById(id));
    }



    public ClaimFlowConfigVO convert(ClaimFlowConfig config){
        ClaimFlowConfigVO rs = new ClaimFlowConfigVO();
        if( config == null){
            config = new ClaimFlowConfig();
        }

        if(StringUtils.isBlank(config.getFlowConfig() )){
            config.setFlowConfig("{}");
        }

        if(StringUtils.isBlank(config.getPreCheckConfig() )){
            config.setPreCheckConfig("{}");
        }
        if(StringUtils.isBlank(config.getInputConfig() )){
            config.setInputConfig("{}");
        }
        if(StringUtils.isBlank(config.getQualityConfig() )){
            config.setQualityConfig("{}");
        }
        if(StringUtils.isBlank(config.getApproveConfig() )){
            config.setApproveConfig("{}");
        }
        if(StringUtils.isBlank(config.getApproveCheckConfig() )){
            config.setApproveCheckConfig("{}");
        }
        if(StringUtils.isBlank(config.getPushConfig() )){
            config.setPushConfig("{}");
        }

        rs.setBizIdentityCode(config.getBizIdentityCode());
        rs.setStatus(config.getStatus());
        rs.setFlowConfigVO(JSONObject.parseObject(config.getFlowConfig(),
                FlowConfigVO.class));
        rs.setPreCheckConfigVO(JSONObject.parseObject(config.getPreCheckConfig(),
                PreCheckConfigVO.class));
        rs.setInputConfigVO(JSONObject.parseObject(config.getInputConfig(),
                InputConfigVO.class));
        rs.setQualityConfigVO(JSONObject.parseObject(config.getQualityConfig(),
                QualityConfigVO.class));
        rs.setApproveConfigVO(JSONObject.parseObject(config.getApproveConfig(),
                ApproveConfigVO.class));
        rs.setApproveCheckConfigVO(JSONObject.parseObject(config.getApproveCheckConfig(),
                ApproveCheckConfigVO.class));

        return rs;

    }


    /**
     * 根据id获取配置
     * @param id
     * @return
     */
    public ClaimFlowConfig getById(Long id) {
        if( id == null){
            return  null;
        }
        return flowConfigRepository.findById(id);
    }


    /**
     * 发布配置
     * @param bizIdentityCode
     */
    @Transactional(rollbackFor = Throwable.class)
    public void deploy(String bizIdentityCode) {
        Assert.notNull(bizIdentityCode,"bizIdentityCode is null");
        ClaimFlowConfig draftConfig =   getByIdentityCodeAndStatus(bizIdentityCode,ClaimFlowStatus.Draft);
        if( draftConfig == null){
            throw new IllegalArgumentException("no draft config found");
        }
        ClaimFlowConfig activeConfig =   getActiveByIdentityCode(bizIdentityCode);
        if( activeConfig != null){
            activeConfig.setStatus(ClaimFlowStatus.Inactive.getCode());
            flowConfigRepository.save(activeConfig);
        }
        draftConfig.setStatus(ClaimFlowStatus.Active.getCode());
        flowConfigRepository.save(draftConfig);
        draftConfig.setId(null);
        draftConfig.setStatus(ClaimFlowStatus.Draft.getCode());
        flowConfigRepository.save(draftConfig);

    }

    public ClaimFlowConfig save(ClaimFlowConfig flowConfig) {
        if(flowConfig.getId()==null){

            if(StringUtils.isBlank(flowConfig.getBizIdentityCode())){
                throw new RuntimeException("bizIdentityCode must not be null");
            }
            if(StringUtils.isBlank(flowConfig.getFlowConfig())){
                flowConfig.setFlowConfig("{}");
            }

            if(StringUtils.isBlank(flowConfig.getPreCheckConfig())){
                flowConfig.setPreCheckConfig("{}");
            }
            if(StringUtils.isBlank(flowConfig.getInputConfig())){
                flowConfig.setInputConfig("{}");
            }
            if(StringUtils.isBlank(flowConfig.getQualityConfig())){
                flowConfig.setQualityConfig("{}");
            }
            if(StringUtils.isBlank(flowConfig.getApproveConfig())){
                flowConfig.setApproveConfig("{}");
            }
            if(StringUtils.isBlank(flowConfig.getApproveCheckConfig())){
                flowConfig.setApproveCheckConfig("{}");
            }
            if(StringUtils.isBlank(flowConfig.getPushConfig())){
                flowConfig.setPushConfig("{}");
            }
            if(flowConfig.getStatus() == null){
                flowConfig.setStatus(ClaimFlowStatus.Draft.getCode());
            }
        }else{
            flowConfig.setBizIdentityCode(null);
        }
        Long id =  flowConfigRepository.save(flowConfig);
        return getById(id);
    }




}
