package com.bone.masterdata.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.core.web.PlatformApiPaths;
import com.bone.masterdata.adapter.web.converter.MasterDataFieldWebConverter;
import com.bone.masterdata.adapter.web.dto.request.CreateMasterDataFieldReq;
import com.bone.masterdata.application.FieldApplicationService;
import com.bone.masterdata.application.command.CreateMasterDataFieldCommand;
import com.bone.masterdata.application.query.dto.MasterDataFieldDTO;
import com.bone.masterdata.application.query.qry.MasterDataFieldListQuery;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(PlatformApiPaths.MASTERDATA_V1 + "/entities")
@RequiredArgsConstructor
public class MasterDataFieldController {

  private final FieldApplicationService fieldService;
  private final MasterDataFieldWebConverter converter;

  // 字段是实体的子资源，无独立权限码（权限目录也未登记 masterdata:fields:*），
  // 随其宿主实体一同授权。
  @PreAuthorize("hasAuthority('masterdata:entities:write')")
  @PostMapping("/{entityId}/fields")
  public ApiResponse<Long> create(
      @PathVariable Long entityId, @Valid @RequestBody CreateMasterDataFieldReq req) {
    req.setMasterDataEntityId(entityId);
    CreateMasterDataFieldCommand cmd = converter.toCommand(req);
    Long id = fieldService.create(cmd);
    return ApiResponse.success(id);
  }

  @GetMapping("/{entityId}/fields")
  public ApiResponse<List<MasterDataFieldDTO>> list(@PathVariable Long entityId) {
    MasterDataFieldListQuery qry = new MasterDataFieldListQuery();
    qry.setMasterDataEntityId(entityId);
    List<MasterDataFieldDTO> dtos = fieldService.list(qry);
    return ApiResponse.success(dtos);
  }
}
