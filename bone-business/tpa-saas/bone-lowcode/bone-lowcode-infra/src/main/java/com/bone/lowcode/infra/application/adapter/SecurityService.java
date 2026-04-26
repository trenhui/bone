package com.bone.lowcode.infra.application.adapter;

import com.alibaba.fastjson.JSONObject;
import com.bone.metadata.sdk.SdkPropertyConfig;
import com.bone.metadata.sdk.enums.MetadataResultCode;
import com.bone.core.util.MetadataException;
import com.bone.core.util.SecretUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * @Author fhmdf
 *
 * @create 2024/8/1 21:31
 */
@Component
public class SecurityService {

    @Autowired private SdkPropertyConfig sdkPropertyConfig;

    public String getToken(String appCode){
        String secret =  sdkPropertyConfig.getSecret();
        if(StringUtils.isBlank(secret)){
            throw new MetadataException(MetadataResultCode.SECRET_IS_EMPTY);
        }
        Map<String,Object> mp = new HashMap<>();
        mp.put("appCode",appCode);
        mp.put("time", System.currentTimeMillis());
        String token = null;
        try {
            token = SecretUtil.encrypt(JSONObject.toJSONString(mp),secret);
            return token;
        } catch (Exception e) {
            throw new  MetadataException(MetadataResultCode.ECRETT_ENCRYPT_ERROR);
        }

    }
}
