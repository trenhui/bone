package com.bone.lowcode.infra.application.upload.impl;

import com.alibaba.excel.EasyExcel;
import com.bone.lowcode.infra.application.task.AlertRobotManager;
import com.bone.lowcode.infra.application.upload.BaseExcepDto;
import com.bone.lowcode.infra.application.upload.FileUploadAction;
import com.bone.lowcode.infra.application.upload.OssUtil;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.FileUploadRecord;
import com.bone.lowcode.infra.infrastructure.persistence.mapper.FileUploadRecordMapper;
import com.bone.core.util.PkListUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

@Slf4j
public abstract  class HandlerBase {
    @Resource
    protected OssUtil ossUtil;
    @Resource
    protected FileUploadRecordMapper uploadRecordMapper;

    @Autowired
    protected AlertRobotManager alertRobotManager;

    abstract  Class getModelClass();


    protected boolean uploadErrorFileTooss(FileUploadRecord fileUploadRecord,
                                         List recordList ) throws IOException {
        boolean checkError  = false;
        if(PkListUtil.isEmpty(recordList)){
            return false;
        }
        for(Object record : recordList){
            BaseExcepDto dto = (BaseExcepDto)record;
            if(StringUtils.isNotBlank(dto.getErrorMsg())){
                checkError = true;
                break;
            }
        }
        String errorFileName = fileUploadRecord.getId()+"-出错信息.xlsx";

        if(checkError){
            ByteArrayInputStream errorInputStream = null;
            try(ByteArrayOutputStream outputStream = new ByteArrayOutputStream()){
                EasyExcel.write(outputStream,  getModelClass()).
                        sheet("Sheet1").doWrite(recordList);

                errorInputStream = new ByteArrayInputStream(outputStream.toByteArray());
                String  ossPath =  ossUtil.upload(errorFileName, errorInputStream,
                        "/pageconfig/upload/error");
                failAndOssPath(fileUploadRecord.getId(),
                        "校验失败, 请查看文件:" + errorFileName, ossPath);
            }finally {
                if( errorInputStream != null){
                    errorInputStream.close();
                }
            }


        }
        return checkError;
    }

    private void failAndOssPath(Long id, String msg, String errorOssPath){

        FileUploadRecord updto = new FileUploadRecord();
        updto.setId(id);
        updto.setErrorFileOss(errorOssPath);
        updto.setErrorMsg(msg);
        uploadRecordMapper.updateById(updto);

        alertRobotManager.doAlertAsyncDefault(
                String.format("告警 --- 选项集文件出错, 错误地址 %s", errorOssPath));
    }

}
