package com.bone.tpa.sdk.dao.biz;

import com.alibaba.fastjson.JSONObject;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.core.util.PkListUtil;
import com.bone.tpa.sdk.claim.model.ClaimHookConfig;
import com.bone.tpa.sdk.dao.ClaimHookConfigRepository;
import com.bone.tpa.sdk.vo.HookPageconfigVO;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ClaimHookConfigBiz {
    @Autowired
    private ClaimHookConfigRepository repository;


    /**
     * 获取配置
     * @param bizIdentityCode
     * @return
     */
    public ClaimHookConfig getByBizIdentityCode(String bizIdentityCode) {
        if(StringUtils.isBlank(bizIdentityCode)){
            throw new IllegalArgumentException("bizIdentityCode is blank");
        }
        Criteria<ClaimHookConfig> criteria = Criteria.create();
        criteria.eq("deleted", 0).eq("bizIdentityCode", bizIdentityCode);
        List<ClaimHookConfig> list = repository.findByCriteria(criteria);
        return list != null && !list.isEmpty() ? list.get(0) : null;
    }


    public List<HookPageconfigVO> getHookConfigList(String bizIdentityCode) {
        ClaimHookConfig config =  getByBizIdentityCode(bizIdentityCode);
        if( config == null){
            return new ArrayList<>();
        }
        String configJson = config.getHookConfig();
        if(StringUtils.isBlank(configJson)){
            configJson="[]";
        }
        List<HookPageconfigVO> existList =  JSONObject.parseArray(configJson, HookPageconfigVO.class);
        return  existList;
    }

    public Map<String,HookPageconfigVO> getHookConfigByMap(String bizIdentityCode ) {

        List<HookPageconfigVO> existList =  getHookConfigList(bizIdentityCode);
        return existList.stream().collect(Collectors.toMap(HookPageconfigVO::getBeanName, vo->vo));
    }

    /**
     * 保存设置
     * @param config
     * @return
     */
    public ClaimHookConfig save(ClaimHookConfig config){
        ClaimHookConfig exist =  getByBizIdentityCode(config.getBizIdentityCode());
        if(exist != null){
            config.setId(exist.getId());
            repository.update(config);
            return repository.findById(config.getId());
        }else{
            repository.insert(config);
            return repository.findById(config.getId());
        }
    }
}
