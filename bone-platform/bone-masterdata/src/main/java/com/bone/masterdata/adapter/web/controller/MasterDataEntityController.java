package com.bone.masterdata.adapter.web.controller;

import com.bone.masterdata.application.command.cmd.CreateMasterDataEntityCmd;
import com.bone.masterdata.application.command.cmd.UpdateMasterDataEntityCmd;
import com.bone.masterdata.application.usecase.standard.CreateMasterDataEntityUseCase;
import com.bone.masterdata.application.usecase.standard.UpdateMasterDataEntityUseCase;
import com.bone.masterdata.application.usecase.standard.PublishMasterDataEntityUseCase;
import com.bone.masterdata.application.usecase.standard.MasterDataEntityPageQueryUseCase;
import com.bone.masterdata.application.usecase.standard.MasterDataEntityDetailQueryUseCase;
import com.bone.masterdata.application.query.dto.MasterDataEntityDTO;
import com.bone.masterdata.application.query.qry.MasterDataEntityByIdQry;
import com.bone.masterdata.application.query.qry.MasterDataEntityPageQry;
import com.bone.core.result.ApiResponse;
import com.bone.core.result.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/masterdata/entities")
@RequiredArgsConstructor
public class MasterDataEntityController {
    private final CreateMasterDataEntityUseCase createMasterDataEntityUseCase;
    private final UpdateMasterDataEntityUseCase updateMasterDataEntityUseCase;
    private final PublishMasterDataEntityUseCase publishMasterDataEntityUseCase;
    private final MasterDataEntityPageQueryUseCase masterDataEntityPageQueryUseCase;
    private final MasterDataEntityDetailQueryUseCase masterDataEntityDetailQueryUseCase;

    @PostMapping
    public ApiResponse<Long> create(@RequestBody CreateMasterDataEntityCmd cmd) {
        Long id = createMasterDataEntityUseCase.execute(cmd);
        return ApiResponse.success(id);
    }

    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable Long id, @RequestBody UpdateMasterDataEntityCmd cmd) {
        cmd.setId(id);
        updateMasterDataEntityUseCase.execute(cmd);
        return ApiResponse.success();
    }

    @GetMapping
    public ApiResponse<PageResult<MasterDataEntityDTO>> list(MasterDataEntityPageQry qry) {
        PageResult<MasterDataEntityDTO> result = masterDataEntityPageQueryUseCase.execute(qry);
        return ApiResponse.success(result);
    }

    @GetMapping("/{id}")
    public ApiResponse<MasterDataEntityDTO> detail(@PathVariable Long id) {
        MasterDataEntityByIdQry qry = new MasterDataEntityByIdQry();
        qry.setId(id);
        MasterDataEntityDTO dto = masterDataEntityDetailQueryUseCase.execute(qry);
        return ApiResponse.success(dto);
    }

    @PostMapping("/{id}/publish")
    public ApiResponse<Void> publish(@PathVariable Long id) {
        publishMasterDataEntityUseCase.execute(id);
        return ApiResponse.success();
    }

    @PostMapping("/convert")
    public ApiResponse<Boolean> convertFromBusinessEntity(@RequestParam Long businessEntityId) {
        // TODO: 实现从业务实体转换的逻辑
        return ApiResponse.success(true);
    }
}