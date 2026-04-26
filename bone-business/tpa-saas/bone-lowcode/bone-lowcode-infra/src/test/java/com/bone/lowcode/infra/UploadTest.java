package com.bone.lowcode.infra;

import com.bone.lowcode.infra.application.upload.FileUploadCommonService;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.FileUploadRecord;
import com.bone.lowcode.infra.infrastructure.persistence.mapper.FileUploadRecordMapper;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;

public class UploadTest extends  BaseTest{
    @Resource
    private FileUploadRecordMapper uploadRecordMapper;

    @Autowired
    private FileUploadCommonService fileUploadCommonAction;


    @Test
    public void testUpload(){
        Assert.notNull(uploadRecordMapper, "uploadRecordMapper is null");
    }


    @Test
    public void testInsert(){
        FileUploadRecord fileUploadRecord = new FileUploadRecord();
        fileUploadRecord.setBizType("bizType");
        fileUploadRecord.setFileName("fileName");
        fileUploadRecord.setData("data");
        fileUploadRecord.setStatus(0);

        uploadRecordMapper.insert(fileUploadRecord);
        Assert.notNull(fileUploadRecord.getId(), "id is null");
    }
}
