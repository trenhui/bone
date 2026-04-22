package com.bone.masterdata.adapter.web.controller;

import com.bone.masterdata.application.command.cmd.CreateMasterDataFieldCmd;
import com.bone.masterdata.application.command.handler.CreateMasterDataFieldHandler;
import com.bone.masterdata.application.query.dto.MasterDataFieldDTO;
import com.bone.masterdata.application.query.handler.MasterDataFieldListQueryHandler;
import com.bone.masterdata.application.query.qry.MasterDataFieldListQry;
import com.bone.core.result.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/masterdata/fields")
@RequiredArgsConstructor
public class MasterDataFieldController {
    private final CreateMasterDataFieldHandler createHandler;
    private final MasterDataFieldListQueryHandler listQueryHandler;

    @PostMapping
    public ApiResponse<Long> create(@RequestBody CreateMasterDataFieldCmd cmd) {
        Long id = createHandler.handle(cmd);
        return ApiResponse.success(id);
    }

    @GetMapping
    public ApiResponse<List<MasterDataFieldDTO>> list(@RequestParam Long masterDataEntityId) {
        MasterDataFieldListQry qry = new MasterDataFieldListQry();
        qry.setMasterDataEntityId(masterDataEntityId);
        List<MasterDataFieldDTO> dtos = listQueryHandler.handle(qry);
        return ApiResponse.success(dtos);
    }
}