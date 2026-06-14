package com.bone.masterdata.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
import com.bone.core.web.PlatformApiPaths;
import com.bone.masterdata.application.command.cmd.CreateMasterDataEntityCommand;
import com.bone.masterdata.application.command.cmd.UpdateMasterDataEntityCommand;
import com.bone.masterdata.application.command.handler.ConvertFromBusinessEntityHandler;
import com.bone.masterdata.application.command.handler.CreateMasterDataEntityHandler;
import com.bone.masterdata.application.command.handler.PublishMasterDataEntityHandler;
import com.bone.masterdata.application.command.handler.UpdateMasterDataEntityHandler;
import com.bone.masterdata.application.query.dto.MasterDataEntityDTO;
import com.bone.masterdata.application.query.handler.MasterDataEntityDetailQueryHandler;
import com.bone.masterdata.application.query.handler.MasterDataEntityPageQueryHandler;
import com.bone.masterdata.application.query.qry.MasterDataEntityByIdQuery;
import com.bone.masterdata.application.query.qry.MasterDataEntityPageQuery;
import com.bone.masterdata.domain.repository.MasterDataEntityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(PlatformApiPaths.MASTERDATA_V1 + "/entities")
@RequiredArgsConstructor
public class MasterDataEntityController {
  private final CreateMasterDataEntityHandler createMasterDataEntityHandler;
  private final UpdateMasterDataEntityHandler updateMasterDataEntityHandler;
  private final PublishMasterDataEntityHandler publishMasterDataEntityHandler;
  private final MasterDataEntityPageQueryHandler masterDataEntityPageQueryHandler;
  private final MasterDataEntityDetailQueryHandler masterDataEntityDetailQueryHandler;
  private final ConvertFromBusinessEntityHandler convertFromBusinessEntityHandler;
  private final MasterDataEntityRepository masterDataEntityRepository;

  @PostMapping
  public ApiResponse<Long> create(@RequestBody CreateMasterDataEntityCommand cmd) {
    Long id = createMasterDataEntityHandler.handle(cmd);
    return ApiResponse.success(id);
  }

  @PutMapping("/{id}")
  public ApiResponse<Void> update(
      @PathVariable Long id, @RequestBody UpdateMasterDataEntityCommand cmd) {
    cmd.setId(id);
    updateMasterDataEntityHandler.handle(cmd);
    return ApiResponse.success();
  }

  @GetMapping
  public ApiResponse<PageResult<MasterDataEntityDTO>> list(MasterDataEntityPageQuery qry) {
    PageResult<MasterDataEntityDTO> result = masterDataEntityPageQueryHandler.handle(qry);
    return ApiResponse.success(result);
  }

  @GetMapping("/{id}")
  public ApiResponse<MasterDataEntityDTO> detail(@PathVariable Long id) {
    MasterDataEntityByIdQuery qry = new MasterDataEntityByIdQuery();
    qry.setId(id);
    MasterDataEntityDTO dto = masterDataEntityDetailQueryHandler.handle(qry);
    return ApiResponse.success(dto);
  }

  @PostMapping("/{id}/publish")
  public ApiResponse<Void> publish(@PathVariable Long id) {
    publishMasterDataEntityHandler.handle(id);
    return ApiResponse.success();
  }

  @PostMapping("/convert")
  public ApiResponse<Long> convertFromBusinessEntity(
      @RequestParam("businessEntityId") Long businessEntityId) {
    Long mdmEntityId = convertFromBusinessEntityHandler.handle(businessEntityId);
    return ApiResponse.success(mdmEntityId);
  }

  @DeleteMapping("/{id}")
  public ApiResponse<Void> delete(@PathVariable Long id) {
    masterDataEntityRepository.deleteById(id);
    return ApiResponse.success();
  }
}
