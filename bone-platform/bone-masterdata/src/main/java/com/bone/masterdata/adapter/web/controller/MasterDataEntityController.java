package com.bone.masterdata.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
import com.bone.masterdata.application.EntityApplicationService;
import com.bone.masterdata.application.command.cmd.CreateMasterDataEntityCommand;
import com.bone.masterdata.application.command.cmd.DisableMasterDataEntityCommand;
import com.bone.masterdata.application.command.cmd.UpdateMasterDataEntityCommand;
import com.bone.masterdata.application.query.dto.MasterDataEntityDTO;
import com.bone.masterdata.application.query.qry.MasterDataEntityByIdQuery;
import com.bone.masterdata.application.query.qry.MasterDataEntityPageQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/masterdata/entities")
@RequiredArgsConstructor
public class MasterDataEntityController {

  private final EntityApplicationService entityService;

  @PostMapping
  public ApiResponse<Long> create(@RequestBody CreateMasterDataEntityCommand cmd) {
    return ApiResponse.success(entityService.create(cmd));
  }

  @PutMapping("/{id}")
  public ApiResponse<Void> update(
      @PathVariable Long id, @RequestBody UpdateMasterDataEntityCommand cmd) {
    cmd.setId(id);
    entityService.update(cmd);
    return ApiResponse.success();
  }

  @PostMapping("/{id}/publish")
  public ApiResponse<Void> publish(@PathVariable Long id) {
    entityService.publish(id);
    return ApiResponse.success();
  }

  @PostMapping("/{id}/disable")
  public ApiResponse<Void> disable(
      @PathVariable Long id, @RequestBody DisableMasterDataEntityCommand cmd) {
    cmd.setId(id);
    entityService.disable(cmd);
    return ApiResponse.success();
  }

  @DeleteMapping("/{id}")
  public ApiResponse<Void> delete(@PathVariable Long id) {
    entityService.delete(id);
    return ApiResponse.success();
  }

  @GetMapping
  public ApiResponse<PageResult<MasterDataEntityDTO>> list(MasterDataEntityPageQuery qry) {
    return ApiResponse.success(entityService.page(qry));
  }

  @GetMapping("/{id}")
  public ApiResponse<MasterDataEntityDTO> detail(@PathVariable Long id) {
    MasterDataEntityByIdQuery qry = new MasterDataEntityByIdQuery();
    qry.setId(id);
    return ApiResponse.success(entityService.detail(qry));
  }

  @PostMapping("/convert")
  public ApiResponse<Long> convert(@RequestParam Long metaEntityId) {
    return ApiResponse.success(entityService.convertFromBusinessEntity(metaEntityId));
  }
}
