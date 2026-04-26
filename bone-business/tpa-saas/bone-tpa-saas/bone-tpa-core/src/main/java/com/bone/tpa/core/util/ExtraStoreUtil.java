package com.bone.tpa.core.util;

import cn.hutool.core.map.MapUtil;
import com.alibaba.fastjson.JSONObject;
import com.bone.core.domain.extension.ExtensibleObject;
import com.bone.core.util.PkListUtil;
import com.bone.tpa.facade.vo.OptionSetDTO;
import com.bone.tpa.sdk.claim.model.ExtraStoreBase;
import com.bone.tpa.sdk.util.OptionSetObject;
import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ExtraStoreUtil {



    static public  Map<String,Object> jsonToMap(String extraStore){
        if(StringUtils.isBlank(extraStore)){
            return new HashMap<>();
        }
        return JSONObject.parseObject(extraStore,Map.class);
    }



    /**
     * 把扩展属性拆成多个对象，因为一个赔案里有多个模块
     *
     * @param object
     * @param extraConfigExist
     * @return
     */
    static  public String fillExtraConfigForModel(ExtensibleObject object, String extraConfigExist){
        String rs= "{}";
        if(StringUtils.isBlank(extraConfigExist)){
            return rs;
        }
        if( object == null){
            return rs;
        }
        Map<String,Object> returnMap = new HashMap<>();

        JSONObject existMap  = JSONObject.parseObject(extraConfigExist);

        Class clz =  object.getClass();
        Field[] fieldsArr =   clz.getDeclaredFields();
        for(Field field : fieldsArr){
            field.setAccessible(true);
            String fieldName = field.getName();
            if(!existMap.containsKey(fieldName)){
                continue;
            }
            Object obj =  existMap.get(fieldName);
            returnMap.put(fieldName, obj);
        }
        Map<String,Object> extproperties =  object.getExtraProperties();
        if(extproperties!=null){
            List<String> allKeyList =    extproperties.entrySet().stream().map(t->t.getKey()).collect(Collectors.toList());
            for(String extKey : allKeyList){
                Object extValue  = extproperties.get(extKey);
                if( extValue == null){
                    continue;
                }

                Object existValue =   existMap.get(extKey);
                if( existValue == null){
                    continue;
                }
                returnMap.put(extKey,existValue);

            }
        }

        return  JSONObject.toJSONString(returnMap);
    }

    public static String mergeExtraStore(Map<String,Object> update,String origin){

        if(StringUtils.isBlank(origin)){
            origin = "{}";
        }

        if(update == null){
            update = new HashMap<>();
        }

        update.entrySet().stream().forEach(t->{
            String checkKey = t.getKey();
            Object checkValue = t.getValue();
            if(checkValue!= null && checkValue instanceof  String){
                throw new RuntimeException(checkKey+" value can not be string");
            }

        });
        JSONObject originObject =  JSONObject.parseObject(origin);

        JSONObject updateObject =  JSONObject.parseObject(JSONObject.toJSONString(update));

        originObject.putAll(updateObject);

        return originObject.toJSONString();
    }

    public static String mergeExtraStore(String update,String origin){

        if(StringUtils.isBlank(origin)){
            origin = "{}";
        }

        if(StringUtils.isBlank(update)){
            update = "{}";
        }

        JSONObject originObject =  JSONObject.parseObject(origin);

        JSONObject updateObject =  JSONObject.parseObject(update);

        originObject.putAll(updateObject);

        return originObject.toJSONString();
    }


    public  static void  fillExtraStoreWithOrigin(ExtraStoreBase dto, String key   , OptionSetDTO config){
        String originValue = dto.getExtraStore();
        if(StringUtils.isBlank(originValue)){
            originValue = "{}";
        }
        if(StringUtils.isBlank(key)){
            dto.setExtraStore(originValue);
            return  ;
        }
        JSONObject orginObject = JSONObject.parseObject(originValue);

        if(config == null){
            orginObject.remove(key);
            dto.setExtraStore(orginObject.toJSONString());
            return  ;
        }
        String code = config.getCode();
        String name = config.getName();



        if(StringUtils.isBlank(code)){
            //没匹配到
            OptionSetObject store =new OptionSetObject();
            store.setName(name);
            orginObject.put(key,store);
        }else {
            //需要把之前的otherContent 取出来，免得被覆盖
            JSONObject existStore = null;
            try {
                existStore = orginObject.getJSONObject(key);
            } catch (Exception e) {

            }
            String otherValueOrigin = "";
            if(existStore!= null ){
                Boolean otherFlag =   existStore.getBoolean("otherFlag");
                if(otherFlag!= null &&  otherFlag ){
                    String otherContent = existStore.getString("otherContent");
                    if(StringUtils.isBlank(otherContent)){
                        otherContent = "";
                    }
                    otherValueOrigin =otherContent;
                }
            }
            OptionSetObject store =new OptionSetObject();
            store.setName(name);
            store.setCode(code);
            if(config.getOtherFlag() && StringUtils.equals(config.getOtherMatchValue(), code)){
                store.setOtherFlag(true);
                store.setOtherContent(otherValueOrigin);
            }
            orginObject.put(key,store);
        }
        dto.setExtraStore(orginObject.toJSONString());

    }


    /**
     * 获取选项集的属性对象
     * @param key
     * @param extraStore
     * @return
     */
    public static OptionSetObject getOptionSetValue(String key ,String extraStore){
        if( StringUtils.isBlank(extraStore)){
            return  null;
        }
        JSONObject extraStoreObject =  JSONObject.parseObject(extraStore);
        if(!extraStoreObject.containsKey(key)){
            return  null;
        }
        OptionSetObject  rs = JSONObject.parseObject(extraStoreObject.getString(key),OptionSetObject.class);

        if(rs.getName() == null){
            rs.setName("");
        }
        return  rs;

    }



}
