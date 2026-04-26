package com.bone.tpa.claim.application.transfer;

import com.alibaba.fastjson.JSONObject;
import com.bone.core.domain.extension.ExtensibleObject;
import com.bone.tpa.claim.application.response.MainInsurePerson;
import com.bone.tpa.sdk.claim.model.ClaimStakeholder;
import com.bone.tpa.sdk.claim.model.ExtraStoreBase;
import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.Field;
import java.util.Map;

public class PersonFieldTransfer {

    public static String  getPersonPageFieldCode( String holderFieldCode, Class clz) {
        Field[]  fieldList =   clz.getDeclaredFields();
        for(Field field : fieldList){
            PersonFieldMapping mappingAnno = field.getAnnotation(PersonFieldMapping.class);
            if( mappingAnno == null){
                continue;
            }
            if(mappingAnno.value().equals(holderFieldCode)){
                return field.getName();
            }
        }
        return null;
    }

    static public void transferToBizDto(ClaimStakeholder source,Object targetObject) {
        if (source == null || targetObject == null) {
            return;
        }

        Field[] targetFields = targetObject.getClass().getDeclaredFields();
        for (Field targetField : targetFields) {

            String fieldName =   targetField.getName();

            PersonFieldMapping mappingAnno = targetField.getAnnotation(PersonFieldMapping.class);
            if (mappingAnno == null) {
                continue;
            }
            String sourceFieldName = mappingAnno.value();
            try {
                Field sourceField = source.getClass().getDeclaredField(sourceFieldName);
                sourceField.setAccessible(true);
                targetField.setAccessible(true);
                Object sourceFieldValue = sourceField.get(source);
                targetField.set(targetObject, sourceFieldValue);
            } catch (Exception e) {
                throw new RuntimeException("field not match sourceFieldName:" + sourceFieldName + ", targetFieldName:" + targetField.getName());
            }


        }

    }

    static public void transferToEntity(ClaimStakeholder targetObj, Object sourceObject){
        if (sourceObject == null || targetObj == null) {
            return;
        }

        Field[] sourceFields =  sourceObject.getClass().getDeclaredFields();
        for(Field sourceField : sourceFields){
            PersonFieldMapping mappingAnno = sourceField.getAnnotation(PersonFieldMapping.class);
            if( mappingAnno == null){
                continue;
            }
            String targgetFieldName = mappingAnno.value();
            try{
                Field targetField = targetObj.getClass().getDeclaredField(targgetFieldName);
                targetField.setAccessible(true);
                sourceField.setAccessible(true);
                Object sourceFieldValue = sourceField.get(sourceObject);
                targetField.set(targetObj,sourceFieldValue);
            }catch (Exception e){
                throw new RuntimeException("field not match sourceFieldName:"+sourceField.getName()+", targetFieldName:"+targgetFieldName);
            }


        }
        
        if (sourceObject instanceof ExtensibleObject) {
            ExtensibleObject extensibleObject = (ExtensibleObject) sourceObject;
            targetObj.setId((Long) extensibleObject.getId());
            targetObj.setBizIdentityCode(extensibleObject.getBizIdentityCode());
            targetObj.setTenantId((Long) extensibleObject.getTenantId());
            targetObj.getExtraProperties().putAll(extensibleObject.getExtraProperties());
        }

    }



    public static void main(String[] args){
        MainInsurePerson source = new MainInsurePerson();
        source.setMainInsureName("1");
        ClaimStakeholder target = new ClaimStakeholder();
        PersonFieldTransfer.transferToEntity(target,source);
        System.out.println(target.getName());


        ClaimStakeholder backSource = new ClaimStakeholder();
        backSource.setName("hello");
        MainInsurePerson backTarget = new MainInsurePerson();
        PersonFieldTransfer.transferToBizDto(backSource,backTarget);
        System.out.println(backTarget.getMainInsureName());


    }




}
