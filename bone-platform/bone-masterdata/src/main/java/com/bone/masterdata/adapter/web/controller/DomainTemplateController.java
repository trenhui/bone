package com.bone.masterdata.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
import com.bone.core.web.PlatformApiPaths;
import com.bone.masterdata.application.DomainTemplateApplicationService;
import com.bone.masterdata.application.command.CreateDomainTemplateCommand;
import com.bone.masterdata.application.command.InstantiateFromTemplateCommand;
import com.bone.masterdata.application.command.PublishDomainTemplateVersionCommand;
import com.bone.masterdata.application.command.UpdateDomainTemplateCommand;
import com.bone.masterdata.application.query.dto.DomainTemplateDTO;
import com.bone.masterdata.application.query.dto.TemplateVersionDTO;
import com.bone.masterdata.application.query.qry.DomainTemplatePageQuery;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** 域模板控制器（G9）：/templates 为平台模板 CRUD 与版本发布；/instantiate 为租户实例化入口（UC-T1）。 */
@RestController
@RequestMapping(PlatformApiPaths.MASTERDATA_V1 + "/templates")
@RequiredArgsConstructor
public class DomainTemplateController {

  private final DomainTemplateApplicationService templateService;

  @PreAuthorize("hasAuthority('masterdata:templates:write')")
  @PostMapping
  public ApiResponse<Long> create(@RequestBody CreateDomainTemplateCommand cmd) {
    return ApiResponse.success(templateService.create(cmd));
  }

  @PreAuthorize("hasAuthority('masterdata:templates:write')")
  @PutMapping("/{id}")
  public ApiResponse<Void> update(
      @PathVariable Long id, @RequestBody UpdateDomainTemplateCommand cmd) {
    cmd.setId(id);
    templateService.update(cmd);
    return ApiResponse.success();
  }

  @PreAuthorize("hasAuthority('masterdata:templates:write')")
  @PostMapping("/{id}/versions")
  public ApiResponse<Long> publishVersion(
      @PathVariable Long id, @RequestBody PublishDomainTemplateVersionCommand cmd) {
    cmd.setId(id);
    return ApiResponse.success(templateService.publishVersion(cmd));
  }

  @PreAuthorize("hasAuthority('masterdata:templates:write')")
  @PostMapping("/{id}/archive")
  public ApiResponse<Void> archive(@PathVariable Long id) {
    templateService.archive(id);
    return ApiResponse.success();
  }

  @PreAuthorize("hasAuthority('masterdata:templates:instantiate')")
  @PostMapping("/instantiate")
  public ApiResponse<Long> instantiate(@RequestBody InstantiateFromTemplateCommand cmd) {
    return ApiResponse.success(templateService.instantiate(cmd));
  }

  @GetMapping
  public ApiResponse<PageResult<DomainTemplateDTO>> list(DomainTemplatePageQuery qry) {
    return ApiResponse.success(templateService.page(qry));
  }

  @GetMapping("/{id}")
  public ApiResponse<DomainTemplateDTO> detail(@PathVariable Long id) {
    return ApiResponse.success(templateService.detail(id));
  }

  @GetMapping("/{id}/versions")
  public ApiResponse<List<TemplateVersionDTO>> versions(@PathVariable Long id) {
    return ApiResponse.success(templateService.versions(id));
  }
}
