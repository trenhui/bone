package com.bone.masterdata.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.core.web.PlatformApiPaths;
import com.bone.masterdata.adapter.web.converter.MasterDataFieldWebConverter;
import com.bone.masterdata.adapter.web.dto.request.UpdateMasterDataFieldReq;
import com.bone.masterdata.application.command.handler.DeleteMasterDataFieldHandler;
import com.bone.masterdata.application.command.handler.UpdateMasterDataFieldHandler;
import com.bone.masterdata.application.query.dto.MasterDataFieldDTO;
import com.bone.masterdata.application.query.handler.MasterDataFieldDetailQueryHandler;
import com.bone.masterdata.application.query.qry.MasterDataFieldDetailQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(PlatformApiPaths.MASTERDATA_V1 + "/fields")
@RequiredArgsConstructor
public class MasterDataFieldTopController {
  private final MasterDataFieldDetailQueryHandler detailQueryHandler;
  private final UpdateMasterDataFieldHandler updateHandler;
  private final DeleteMasterDataFieldHandler deleteHandler;
  private final MasterDataFieldWebConverter converter;

  @GetMapping("/{id}")
  public ApiResponse<MasterDataFieldDTO> detail(@PathVariable Long id) {
    return ApiResponse.success(detailQueryHandler.handle(new MasterDataFieldDetailQuery(id)));
  }

  @PutMapping("/{id}")
  public ApiResponse<Void> update(
      @PathVariable Long id, @RequestBody UpdateMasterDataFieldReq req) {
    updateHandler.handle(converter.toCommand(id, req));
    return ApiResponse.success();
  }

  @DeleteMapping("/{id}")
  public ApiResponse<Void> delete(@PathVariable Long id) {
    deleteHandler.handle(id);
    return ApiResponse.success();
  }
}
