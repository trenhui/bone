package com.bone.masterdata.adapter.web.controller;

import com.bone.masterdata.application.command.cmd.ImportMasterDataRecordsCommand;
import com.bone.masterdata.application.command.handler.ImportMasterDataRecordsHandler;
import com.bone.masterdata.application.command.handler.PublishMasterDataRecordHandler;
import com.bone.masterdata.application.query.handler.MasterDataRecordListQueryHandler;
import com.bone.masterdata.application.query.dto.MasterDataRecordDTO;
import com.bone.masterdata.application.query.qry.MasterDataRecordListQuery;
import com.bone.core.result.ApiResponse;
import com.bone.core.web.PlatformApiPaths;
import com.bone.core.result.PageResult;
import com.bone.masterdata.application.query.handler.ExportMasterDataRecordsQueryHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping(PlatformApiPaths.MASTERDATA_V1 + "/records")
@RequiredArgsConstructor
public class MasterDataRecordController {
    private final ImportMasterDataRecordsHandler importMasterDataRecordsHandler;
    private final MasterDataRecordListQueryHandler masterDataRecordListQueryHandler;
    private final PublishMasterDataRecordHandler publishMasterDataRecordHandler;
    private final ExportMasterDataRecordsQueryHandler exportMasterDataRecordsQueryHandler;

    @PostMapping
    public ApiResponse<List<Long>> importRecords(@RequestParam Long masterDataEntityId, @RequestParam MultipartFile file) {
        ImportMasterDataRecordsCommand cmd = new ImportMasterDataRecordsCommand();
        cmd.setMasterDataEntityId(masterDataEntityId);
        cmd.setFile(file);
        List<Long> ids = importMasterDataRecordsHandler.handle(cmd);
        return ApiResponse.success(ids);
    }

    @GetMapping
    public ApiResponse<PageResult<MasterDataRecordDTO>> list(MasterDataRecordListQuery qry) {
        PageResult<MasterDataRecordDTO> result = masterDataRecordListQueryHandler.handle(qry);
        return ApiResponse.success(result);
    }

    @PostMapping("/{id}/publish")
    public ApiResponse<Void> publish(@PathVariable Long id) {
        publishMasterDataRecordHandler.handle(id);
        return ApiResponse.success();
    }

    @GetMapping("/export")
    public ApiResponse<String> export(@RequestParam("masterDataEntityId") Long masterDataEntityId) {
        return ApiResponse.success(exportMasterDataRecordsQueryHandler.handle(masterDataEntityId));
    }
}