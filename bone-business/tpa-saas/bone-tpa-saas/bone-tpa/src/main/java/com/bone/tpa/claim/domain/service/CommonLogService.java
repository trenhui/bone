package com.bone.tpa.claim.domain.service;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.tpa.claim.application.enums.CommonLogType;
import com.bone.tpa.sdk.dao.CommonLogRespository;
import com.bone.tpa.sdk.claim.model.CommonLog;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CommonLogService {
    public static final ThreadPoolExecutor LOG_THREAD = new ThreadPoolExecutor(1, 1, 1L, TimeUnit.MINUTES,
            new LinkedBlockingQueue<>(10000) );


    @Autowired
    CommonLogRespository logRespository;

    public void addLogASync(String  objectId, CommonLogType bizType,
                           String msg , Object... args){
        String traceId = MDC.get("traceId");
        log.info(msg,args);
        LOG_THREAD.submit(()->{
            CommonLog dto = new CommonLog();
            dto.setObjectId(objectId);
            dto.setRemark(format(msg,args));
            dto.setBizType(bizType.name());
            dto.setCreateTime(new Date());
            dto.setTraceId(traceId);
            logRespository.insert(dto);
        });

    }


    public void addLogASyncNoReplace(String  objectId, CommonLogType bizType,
                            String msg){
        String traceId = MDC.get("traceId");
        LOG_THREAD.submit(()->{
            CommonLog dto = new CommonLog();
            dto.setObjectId(objectId);
            dto.setRemark(msg);
            dto.setBizType(bizType.name());
            dto.setCreateTime(new Date());
            dto.setTraceId(traceId);
            logRespository.insert(dto);
        });

    }

    public void addLog(String  objectId, CommonLogType bizType,
                            String msg ,String traceId, Object... args){
        CommonLog dto = new CommonLog();
        dto.setObjectId(objectId);
        dto.setRemark(format(msg,args));
        dto.setBizType(bizType.name());
        dto.setCreateTime(new Date());
        dto.setTraceId(traceId);
        logRespository.insert(dto);
        System.out.println(dto.getId());
        CommonLog checkExist =   logRespository.findById(dto.getId());
        System.out.println(checkExist);

    }

    public void addClaimLogAsync(Long claimId,  CommonLogType logType,String msg , Object... args){
        String traceId = MDC.get("traceId");
        log.info(msg,args);
        LOG_THREAD.submit(()->{
            try {
                CommonLog dto = new CommonLog();
                dto.setObjectId(claimId.toString());
                dto.setRemark(format(msg,args));
                dto.setBizType(logType.name());
                dto.setCreateTime(new Date());
                dto.setTraceId(traceId);
                logRespository.insert(dto);
            } catch (Exception e) {
                log.error("addClaimLogAsync error ",e);
            }
        });

    }

    public List<CommonLog> getByObjectId(String objectId,CommonLogType logType){
        Criteria<CommonLog> criteria = new Criteria<>();

        criteria.eq(CommonLog::getObjectId,objectId);
        criteria.eq(CommonLog::getBizType,logType.getCode());
       return logRespository.findByCriteria(criteria);
    }

    private  String format(String  text,Object... args){
        if( text == null){
            return "";
        }
         String replaceRegx = "{#5434@!}";
        String content =  text.replaceAll("\\{\\}",replaceRegx);
        if(args != null){
            List<Object> objList =    Arrays.stream(args).collect(Collectors.toList());
            for(Object obj : objList){
                String replace = "null";
                if(obj!= null){
                    if(obj instanceof  String){
                        replace = (String)obj;
                    }else{
                        try {
                            replace = JSONObject.toJSONString(obj, SerializerFeature.DisableCircularReferenceDetect);
                        } catch (Exception e){
                            replace =obj.toString();
                        }
                    }


                }
                content = StringUtils.replace(content,replaceRegx,replace,1);
            }
        }
        return content;

    }
}
