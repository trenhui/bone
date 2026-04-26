package com.bone.lowcode.infra.interfaces.rest;

import com.bone.core.result.Result;
import com.bone.lowcode.infra.application.upload.FileUploadCommonService;
import com.bone.lowcode.infra.application.upload.UploadCommonDto;
import com.bone.lowcode.infra.application.upload.impl.OptionSetImportHandler;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.FileUploadRecord;
import com.bone.lowcode.infra.infrastructure.persistence.mapper.FileUploadRecordMapper;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/uploadcommon")
public class UploadCommonController {

    @Autowired
    private FileUploadCommonService fileUploadCommonAction;

    @Autowired
    private OptionSetImportHandler optionSetImportHandler;

    @Resource
    private FileUploadRecordMapper uploadRecordMapper;
    @PostMapping("/createUploadTask")
    public Result<FileUploadRecord> createUploadTask(@ModelAttribute UploadCommonDto request,
                                                       @RequestParam("file") MultipartFile file) throws IOException {

        FileUploadRecord fileUploadRecord = new FileUploadRecord();
        fileUploadRecord.setBizType(request.getBizType());
        fileUploadRecord.setFileName(request.getFileName());
        fileUploadRecord.setData(request.getData());

        if( file == null || file.getBytes() == null || file.getBytes().length == 0){
            return Result.error("file is empty");
        }
        FileUploadRecord rsDto =  fileUploadCommonAction.apply(fileUploadRecord,
                file.getBytes() ,true);
        return Result.ok(rsDto);
    }


    @GetMapping("/getById")
    public  Result<FileUploadRecord> getById(@RequestParam("id")Long id){
        FileUploadRecord rsDto =uploadRecordMapper.selectById(id);
        return Result.ok(rsDto);
    }

    /**
     * 下载上传文件配置的模板文件
     * 仅支持excel
     */
    @GetMapping("/getTemplate")
    public Result<byte[]> downloadTemplate(@RequestParam("id") Long optionSetId) {
        return Result.ok(optionSetImportHandler.downloadTemplate(optionSetId));
    }


}
