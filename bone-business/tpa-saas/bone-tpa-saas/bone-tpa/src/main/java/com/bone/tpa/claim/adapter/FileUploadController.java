package com.bone.tpa.claim.adapter;

import com.bone.core.result.PageResult;
import com.bone.core.result.Result;
import com.bone.tpa.claim.application.FileUploadApplicationService;
import com.bone.tpa.claim.application.dto.FileUploadRecordDTO;
import com.bone.tpa.claim.application.request.FileUploadRequest;
import com.bone.tpa.claim.application.request.QueryListRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文件上传控制器
 */
@RestController
@RequestMapping("/tpa/file")
public class FileUploadController {
    @Autowired
    private FileUploadApplicationService fileUploadApplicationService;

    /**
     * 上传文件
     *
     * 只支持上传以 签收记录/批次号 为父结构的文件
     */
    @PostMapping("/upload")
    public Result<Boolean> uploadFile(@ModelAttribute FileUploadRequest request, @RequestParam("file") MultipartFile file) {
        return Result.ok(fileUploadApplicationService.uploadFile(request, file));
    }

    /**
     * 批量上传文件
     *
     * 只支持上传以 赔案 为父结构的文件
     */
    @PostMapping("/uploadbatch")
    public Result<Boolean> batchUploadFile(@RequestBody FileUploadRequest request) {
        return Result.ok(fileUploadApplicationService.batchUploadFile(request));
    }

    /**
     * 下载上传文件配置的模板文件
     * 仅支持excel
     */
    @GetMapping("/template")
    public Result<byte[]> downloadTemplate(@RequestParam("id") Long configId) {
        return Result.ok(fileUploadApplicationService.downloadTemplate(configId));
    }

    /**
     * 获取文件上传记录
     */
    @PostMapping("/query")
    public Result<PageResult<FileUploadRecordDTO>> getFileUploadRecord(@RequestBody QueryListRequest request) {
        return Result.ok(fileUploadApplicationService.getFileUploadRecord(request));
    }

    /**
     * 获取文件上传记录
     * 限定特定的文件类型
     */
    @GetMapping("/getRecord")
    public Result<List<FileUploadRecordDTO>> getFileUploadRecordForEvent(@RequestParam("id") String id, @RequestParam("type") String type, @RequestParam("tenantId") String tenantId) {
        return Result.ok(fileUploadApplicationService.getFileUploadRecordForEvent(id, type, tenantId));
    }

}
