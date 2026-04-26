package com.bone.tpa.claim.domain.service;

import com.alibaba.fastjson.JSONObject;
import com.bone.core.util.JsonUtil;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.core.util.PkListUtil;
import com.bone.tpa.api.enums.HintMsgType;
import com.bone.tpa.sdk.dao.ClaimHintMsgRespository;
import com.bone.tpa.claim.sync.SyncBaseTool;
import com.bone.tpa.sdk.claim.model.ClaimHintMsg;
import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.Map;

@Service
public class HintService {
    @Autowired
    private ClaimHintMsgRespository hintMsgRespository;

    public void saveHint(Long objectId, HintMsgType hintMsgType,
                            Map<String,String> hintMap, Long claimNumber ){
        if(hintMap == null || hintMap.size()==0){
            return;
        }
        Assert.notNull(hintMsgType,"msgType is null");
        Assert.notNull(objectId,"objectId is null");
        Criteria<ClaimHintMsg> criteria = new Criteria();
        criteria.eq(ClaimHintMsg::getRelationId, objectId);
        criteria.eq(ClaimHintMsg::getHintType, hintMsgType.getCode());
        ClaimHintMsg exist = PkListUtil.first( hintMsgRespository.findByCriteria(criteria));
        if( exist == null){
            exist = new ClaimHintMsg();
            exist.setRelationId(objectId);
            exist.setHintType(hintMsgType.getCode());
            exist.setData("{}");
            exist.setClaimNumber(claimNumber);
            exist.setTenantId(SyncBaseTool.TENANT_ID);
        }
        String data = exist .getData();
        if(StringUtils.isBlank(data)){
            data = "{}";
        }
        exist.setData(JSONObject.toJSONString(hintMap));
        hintMsgRespository.save(exist);
    }


    public void appendHint(Long objectId, HintMsgType hintMsgType,
                         Map<String,String> hintMap, Long claimNumber ){
        if(hintMap == null || hintMap.size()==0){
            return;
        }
        Assert.notNull(hintMsgType,"msgType is null");
        Assert.notNull(objectId,"objectId is null");
        Criteria<ClaimHintMsg> criteria = new Criteria();
        criteria.eq(ClaimHintMsg::getRelationId, objectId);
        criteria.eq(ClaimHintMsg::getHintType, hintMsgType.getCode());
        ClaimHintMsg exist = PkListUtil.first( hintMsgRespository.findByCriteria(criteria));
        if( exist == null){
            exist = new ClaimHintMsg();
            exist.setRelationId(objectId);
            exist.setHintType(hintMsgType.getCode());
            exist.setData("{}");
            exist.setClaimNumber(claimNumber);
            exist.setTenantId(SyncBaseTool.TENANT_ID);
        }
        String data = exist .getData();
        if(StringUtils.isBlank(data)){
            data = "{}";
        }
        Map<String,String> oldHintMap = JsonUtil.fromJson(data, new TypeReference<Map<String, String>>(){});
        oldHintMap.putAll(hintMap);

        exist.setData(JSONObject.toJSONString(oldHintMap));
        hintMsgRespository.save(exist);
    }
}
