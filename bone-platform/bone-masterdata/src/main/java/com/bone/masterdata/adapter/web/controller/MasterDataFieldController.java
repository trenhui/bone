package com.bone.masterdata.adapter.web.controller;

import com.bone.masterdata.application.command.cmd.CreateMasterDataFieldCommand;
import com.bone.masterdata.application.command.handler.CreateMasterDataFieldHandler;
import com.bone.masterdata.application.query.dto.MasterDataFieldDTO;
import com.bone.masterdata.application.query.handler.MasterDataFieldListQueryHandler;
import com.bone.masterdata.application.query.qry.MasterDataFieldListQuery;
import com.bone.core.result.ApiResponse;
import com.bone.core.web.PlatformApiPaths;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(PlatformApiPaths.MASTERDATA_V1 + "/fields")
@RequiredArgsConstructor
public class MasterDataFieldController {
    private final CreateMasterDataFieldHandler createHandler;
    private final MasterDataFieldListQueryHandler listQueryHandler;

    @PostMapping
    public ApiResponse<Long> create(@RequestBody CreateMasterDataFieldCommand cmd) {
        Long id = createHandler.handle(cmd);
        return ApiResponse.success(id);
    }

    @GetMapping
    public ApiResponse<List<MasterDataFieldDTO>> list(@RequestParam("masterDataEntityId") Long masterDataEntityId) {
        MasterDataFieldListQuery qry = new MasterDataFieldListQuery();
        qry.setMasterDataEntityId(masterDataEntityId);
        List<MasterDataFieldDTO> dtos = listQueryHandler.handle(qry);
        return ApiResponse.success(dtos);
    }
}