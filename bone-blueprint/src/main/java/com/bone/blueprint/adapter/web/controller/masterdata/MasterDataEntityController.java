package com.bone.blueprint.adapter.web.controller.masterdata;

import com.bone.blueprint.application.command.cmd.masterdata.CreateMasterDataEntityCmd;
import com.bone.blueprint.application.command.handler.masterdata.CreateMasterDataEntityHandler;
import com.bone.core.result.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/masterdata/entities")
@RequiredArgsConstructor
public class MasterDataEntityController {
    private final CreateMasterDataEntityHandler createMasterDataEntityHandler;
    
    @PostMapping
    public ApiResponse<Long> createMasterDataEntity(@RequestBody CreateMasterDataEntityCmd cmd) {
        Long id = createMasterDataEntityHandler.handle(cmd);
        return ApiResponse.success(id);
    }
}
