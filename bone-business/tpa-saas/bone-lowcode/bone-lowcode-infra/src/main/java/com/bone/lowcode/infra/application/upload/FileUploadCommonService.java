package com.bone.lowcode.infra.application.upload;

import com.alibaba.excel.EasyExcel;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.FileUploadRecord;
import com.bone.lowcode.infra.infrastructure.persistence.mapper.FileUploadRecordMapper;
import com.bone.metadata.sdk.repository.BeforeInserHandler;
import com.bone.core.util.SpringContextUtils;
import com.esotericsoftware.kryo.io.Input;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
public class FileUploadCommonService {
    public static final ThreadPoolExecutor EXECUTOR = new ThreadPoolExecutor(1,
            1, 1000L, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());

    @Resource
    private OssUtil ossUtil;
    @Resource
    private FileUploadRecordMapper uploadRecordMapper;

    public FileUploadRecord apply(FileUploadRecord fileUploadRecord,
                                  byte[] fileBytes,Boolean isAysnc)  {

        if( fileUploadRecord .getBizType() == null){
            throw new RuntimeException("bizType is null");
        }
        if(StringUtils.isBlank(fileUploadRecord.getFileName())){
            throw  new RuntimeException("fileName is blank");
        }
        if(fileBytes == null){
            throw  new RuntimeException("file is null");
        }
        if(fileUploadRecord.getOperatorType() == null){
            fileUploadRecord.setOperatorType(0);
        }
        FileUploadAction handler = getHandler(fileUploadRecord.getBizType());
        if(handler == null){
            throw new RuntimeException("handler is null");
        }

        try(InputStream is = new ByteArrayInputStream(fileBytes)){
                String originPath =      ossUtil.upload(fileUploadRecord.getFileName(),is,"/pageconfig/upload/origin");
                fileUploadRecord.setOriginFileOss(originPath);
        }catch (Throwable e){
            throw new RuntimeException(e.getMessage());
        }
        //保存原来的oss 地址

        fileUploadRecord.setPercent(BigDecimal.ZERO);
        fileUploadRecord.setStatus(0);
        fileUploadRecord.setErrorMsg("");
        uploadRecordMapper.insert(fileUploadRecord);
        String traceId = MDC.get("trace_id");

        if(isAysnc){
            //异步线程池去执行
            EXECUTOR.submit(()->{

                MDC.put("trace_id",StringUtils.isBlank(traceId)? UUID.randomUUID().toString():traceId);
                doEvent(fileUploadRecord,fileBytes,handler);

            });
        }else {
            doEvent(fileUploadRecord,fileBytes,handler);
        }

        return  fileUploadRecord;
    }



    private void doEvent(FileUploadRecord fileUploadRecord,
                         byte[] fileBytes,FileUploadAction handler){
        try {
            log.info("start to prepare upload file:"+fileUploadRecord.getId());
            handler.prepare(fileUploadRecord);
        } catch (Throwable e) {
            log.error("prepairError",e);
            fail(fileUploadRecord.getId(),"prepair fail:"+e.getMessage());
            return;
        }
        List recordList = new ArrayList<>();
        try(InputStream is = new ByteArrayInputStream(fileBytes)){
            log.info("start to readrecord upload file:"+fileUploadRecord.getId());

            recordList =    handler.readToRecord(fileUploadRecord,is );

        }catch (Throwable e){
            log.error("readrecord error ",e);
            fail(fileUploadRecord.getId(),"readRecord fail:"+e.getMessage());
            return;
        }

        try {
            log.info("start to checkrecord upload file:"+fileUploadRecord.getId());

            boolean ret  =   handler.checkRecord(fileUploadRecord,recordList);
            if(!ret){
                fail(fileUploadRecord.getId(),"校验失败");
                return;
            }

        } catch (Exception e) {
            log.error("checkrecord error ",e);

            fail(fileUploadRecord.getId(),"checkRecord fail:"+e.getMessage());
            return;
        }

        try {
            log.info("start saveupload file:"+fileUploadRecord.getId());
            boolean ret  =   handler.doSave(fileUploadRecord,recordList);
            if(!ret){
                fail(fileUploadRecord.getId(),"保存失败");
                return;
            }

        } catch (Exception e) {
            log.error("saverecord error ",e);
            fail(fileUploadRecord.getId(),"保存数据失败, fail:"+e.getMessage());
            return;
        }

        log.info("end saveupload file:"+fileUploadRecord.getId());

        success(fileUploadRecord.getId());
    }



    private void success(Long id){
        FileUploadRecord updto = new FileUploadRecord();
        updto.setId(id);
        updto.setStatus(10);
        uploadRecordMapper.updateById(updto);
    }


    private void fail(Long id,String msg){

        FileUploadRecord updto = new FileUploadRecord();
        updto.setId(id);
        updto.setErrorMsg(msg);
        updto.setStatus(11);
        uploadRecordMapper.updateById(updto);
    }


    private FileUploadAction getHandler(String bizType ){
        Map<String,FileUploadAction> beanMap =  SpringContextUtils.getBeansOfType(FileUploadAction.class);
        List<Map.Entry<String, FileUploadAction>> handlerList = beanMap.entrySet().stream().collect(Collectors.toList());
        for (Map.Entry<String, FileUploadAction> childEntry : handlerList) {
            FileUploadAction action = childEntry.getValue();
            if(action != null && StringUtils.equals( action .getBizType(),bizType)){
                return action;
            }
        }

       return  null;
    }

}
