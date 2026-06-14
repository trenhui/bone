package com.bone.masterdata.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.core.web.PlatformApiPaths;
import com.bone.masterdata.adapter.web.converter.MasterDataFieldWebConverter;
import com.bone.masterdata.adapter.web.dto.req.CreateMasterDataFieldReq;
import com.bone.masterdata.application.command.cmd.CreateMasterDataFieldCommand;
import com.bone.masterdata.application.command.handler.CreateMasterDataFieldHandler;
import com.bone.masterdata.application.query.dto.MasterDataFieldDTO;
import com.bone.masterdata.application.query.handler.MasterDataFieldListQueryHandler;
import com.bone.masterdata.application.query.qry.MasterDataFieldListQuery;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(PlatformApiPaths.MASTERDATA_V1 + "/entities")
@RequiredArgsConstructor
public class MasterDataFieldController {
  private final CreateMasterDataFieldHandler createHandler;
  private final MasterDataFieldListQueryHandler listQueryHandler;
  private final MasterDataFieldWebConverter converter;

  @PostMapping("/{entityId}/fields")
  public ApiResponse<Long> create(
      @PathVariable Long entityId, @RequestBody CreateMasterDataFieldReq req) {
    req.setMasterDataEntityId(entityId);
    CreateMasterDataFieldCommand cmd = converter.toCommand(req);
    Long id = createHandler.handle(cmd);
    return ApiResponse.success(id);
  }

  @GetMapping("/{entityId}/fields")
  public ApiResponse<List<MasterDataFieldDTO>> list(@PathVariable Long entityId) {
    MasterDataFieldListQuery qry = new MasterDataFieldListQuery();
    qry.setMasterDataEntityId(entityId);
    List<MasterDataFieldDTO> dtos = listQueryHandler.handle(qry);
    return ApiResponse.success(dtos);
  }
}
