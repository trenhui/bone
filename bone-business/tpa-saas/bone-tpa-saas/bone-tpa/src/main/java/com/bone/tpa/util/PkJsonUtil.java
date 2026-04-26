package com.bone.tpa.util;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import org.apache.xmlbeans.impl.jam.mutable.MPackage;

import java.util.HashMap;
import java.util.Map;

public class PkJsonUtil {

    static public String buildPkJson(String... valArr ) {
        Integer length = valArr.length;
        if(length % 2 == 1){
            throw new RuntimeException("不是key 的数组格式,valArr 必须是偶数长度");
        }
        Map<String,String> rs = new HashMap<>();
        for (int i = 0; i < length; i+=2) {
            rs.put(valArr[i],valArr[i+1]);
        }
        return JSONObject.toJSONString(rs, SerializerFeature.DisableCircularReferenceDetect);
    }

    static public Map<String,Object> buildStringMap(String... valArr ) {
        Integer length = valArr.length;
        if(length % 2 == 1){
            throw new RuntimeException("不是key 的数组格式,valArr 必须是偶数长度");
        }
        Map<String,Object> rs = new HashMap<>();
        for (int i = 0; i < length; i+=2) {
            rs.put(valArr[i],valArr[i+1]);
        }
        return rs;
    }
}
