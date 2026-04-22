package com.bone.masterdata.adapter.web.controller;

import com.bone.masterdata.application.command.cmd.ImportMasterDataRecordsCmd;
import com.bone.masterdata.application.command.handler.ImportMasterDataRecordsHandler;
import com.bone.masterdata.application.command.handler.PublishMasterDataRecordHandler;
import com.bone.masterdata.application.query.dto.MasterDataRecordDTO;
import com.bone.masterdata.application.query.handler.MasterDataRecordListQueryHandler;
import com.bone.masterdata.application.query.qry.MasterDataRecordListQry;
import com.bone.core.result.ApiResponse;
import com.bone.core.result.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/masterdata/records")
@RequiredArgsConstructor
public class MasterDataRecordController {
    private final ImportMasterDataRecordsHandler importHandler;
    private final MasterDataRecordListQueryHandler listQueryHandler;
    private final PublishMasterDataRecordHandler publishHandler;

    @PostMapping
    public ApiResponse<List<Long>> importRecords(@RequestParam Long masterDataEntityId, @RequestParam MultipartFile file) {
        ImportMasterDataRecordsCmd cmd = new ImportMasterDataRecordsCmd();
        cmd.setMasterDataEntityId(masterDataEntityId);
        cmd.setFile(file);
        List<Long> ids = importHandler.handle(cmd);
        return ApiResponse.success(ids);
    }

    @GetMapping
    public ApiResponse<PageResult<MasterDataRecordDTO>> list(MasterDataRecordListQry qry) {
        PageResult<MasterDataRecordDTO> result = listQueryHandler.handle(qry);
        return ApiResponse.success(result);
    }

    @PostMapping("/{id}/publish")
    public ApiResponse<Void> publish(@PathVariable Long id) {
        publishHandler.handle(id);
        return ApiResponse.success();
    }

    @GetMapping("/export")
    public ApiResponse<String> export(@RequestParam Long masterDataEntityId) {
        // TODO: 实现导出主数据记录的逻辑
        return ApiResponse.success("导出成功");
    }
}