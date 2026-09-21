package com.bone.masterdata.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.core.web.PlatformApiPaths;
import com.bone.masterdata.adapter.web.converter.MasterDataFieldWebConverter;
import com.bone.masterdata.adapter.web.dto.request.UpdateMasterDataFieldReq;
import com.bone.masterdata.application.FieldApplicationService;
import com.bone.masterdata.application.query.dto.MasterDataFieldDTO;
import com.bone.masterdata.application.query.qry.MasterDataFieldDetailQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(PlatformApiPaths.MASTERDATA_V1 + "/fields")
@RequiredArgsConstructor
public class MasterDataFieldTopController {

  private final FieldApplicationService fieldService;
  private final MasterDataFieldWebConverter converter;

  @GetMapping("/{id}")
  public ApiResponse<MasterDataFieldDTO> detail(@PathVariable Long id) {
    return ApiResponse.success(fieldService.detail(new MasterDataFieldDetailQuery(id)));
  }

  @PutMapping("/{id}")
  public ApiResponse<Void> update(
      @PathVariable Long id, @RequestBody UpdateMasterDataFieldReq req) {
    fieldService.update(converter.toCommand(id, req));
    return ApiResponse.success();
  }

  @DeleteMapping("/{id}")
  public ApiResponse<Void> delete(@PathVariable Long id) {
    fieldService.delete(id);
    return ApiResponse.success();
  }
}
