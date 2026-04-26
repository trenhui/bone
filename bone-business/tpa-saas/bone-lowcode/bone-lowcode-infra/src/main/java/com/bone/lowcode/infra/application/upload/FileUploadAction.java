package com.bone.lowcode.infra.application.upload;

import com.bone.lowcode.infra.infrastructure.persistence.dataobject.FileUploadRecord;

import java.io.InputStream;
import java.util.List;

public interface FileUploadAction {


    String getBizType();

    /**
     * 可以设置一些上下文什么的
     *
     */
    void prepare(FileUploadRecord record);

    /**
     * 将excel 文件读到记录中
     * @return
     */
    List readToRecord(FileUploadRecord record,InputStream fileInputStream);

    /**
     * 检查数据合法性
     * @param list
     * @return
     */
    boolean checkRecord(FileUploadRecord record,List list);

    /**
     * 保存数据
     * 注意可以更新进度
     * @param list
     */
    boolean doSave(FileUploadRecord record ,List list);

}
