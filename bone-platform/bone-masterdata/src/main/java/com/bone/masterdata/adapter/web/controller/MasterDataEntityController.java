package com.bone.masterdata.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
import com.bone.core.web.PlatformApiPaths;
import com.bone.masterdata.application.EntityApplicationService;
import com.bone.masterdata.application.command.CreateMasterDataEntityCommand;
import com.bone.masterdata.application.command.DisableMasterDataEntityCommand;
import com.bone.masterdata.application.command.UpdateMasterDataEntityCommand;
import com.bone.masterdata.application.query.dto.MasterDataEntityDTO;
import com.bone.masterdata.application.query.qry.MasterDataEntityByIdQuery;
import com.bone.masterdata.application.query.qry.MasterDataEntityPageQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(PlatformApiPaths.MASTERDATA_V1 + "/entities")
@RequiredArgsConstructor
public class MasterDataEntityController {

  private final EntityApplicationService entityService;

  @PreAuthorize("hasAuthority('masterdata:entities:write')")
  @PostMapping
  public ApiResponse<Long> create(@RequestBody CreateMasterDataEntityCommand cmd) {
    return ApiResponse.success(entityService.create(cmd));
  }

  @PreAuthorize("hasAuthority('masterdata:entities:write')")
  @PutMapping("/{id}")
  public ApiResponse<Void> update(
      @PathVariable Long id, @RequestBody UpdateMasterDataEntityCommand cmd) {
    cmd.setId(id);
    entityService.update(cmd);
    return ApiResponse.success();
  }

  /**
   * 发布实体（DRAFT → PUBLISHED）。
   *
   * <p>复用 {@code masterdata:entities:write}：实体发布是实体生命周期的普通维护动作（与 create/update/disable/delete
   * 同一决策权），无独立审批流，拆码只增加目录维护成本。
   */
  @PreAuthorize("hasAuthority('masterdata:entities:write')")
  @PostMapping("/{id}/publish")
  public ApiResponse<Void> publish(@PathVariable Long id) {
    entityService.publish(id);
    return ApiResponse.success();
  }

  @PreAuthorize("hasAuthority('masterdata:entities:write')")
  @PostMapping("/{id}/disable")
  public ApiResponse<Void> disable(
      @PathVariable Long id, @RequestBody DisableMasterDataEntityCommand cmd) {
    cmd.setId(id);
    entityService.disable(cmd);
    return ApiResponse.success();
  }

  @PreAuthorize("hasAuthority('masterdata:entities:write')")
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

  // convert 从业务实体派生主数据实体，属实体的创建动作，与 create 同码。
  @PreAuthorize("hasAuthority('masterdata:entities:write')")
  @PostMapping("/convert")
  public ApiResponse<Long> convert(@RequestParam Long metaEntityId) {
    return ApiResponse.success(entityService.convertFromBusinessEntity(metaEntityId));
  }

  /** 调整治理等级（§4.3）：body 为 {"tier": "L1|L2|L3"}。 */
  @PreAuthorize("hasAuthority('masterdata:entities:write')")
  @PostMapping("/{id}/governance-tier")
  public ApiResponse<Void> changeGovernanceTier(
      @PathVariable Long id, @RequestBody java.util.Map<String, String> body) {
    entityService.changeGovernanceTier(id, body.get("tier"));
    return ApiResponse.success();
  }

  /** 绑定责任归口应用（§3.2）：body 为 {"owningAppId": 123} 或 {"owningAppId": null}。 */
  @PreAuthorize("hasAuthority('masterdata:entities:write')")
  @PostMapping("/{id}/owning-app")
  public ApiResponse<Void> bindOwningApp(
      @PathVariable Long id, @RequestBody java.util.Map<String, Long> body) {
    entityService.bindOwningApp(id, body.get("owningAppId"));
    return ApiResponse.success();
  }
}
