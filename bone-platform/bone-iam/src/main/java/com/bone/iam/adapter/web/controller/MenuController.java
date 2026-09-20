package com.bone.iam.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.core.web.PlatformApiPaths;
import com.bone.iam.adapter.web.converter.MenuWebConverter;
import com.bone.iam.adapter.web.dto.request.CreateMenuReq;
import com.bone.iam.adapter.web.dto.request.UpdateMenuReq;
import com.bone.iam.application.MenuApplicationService;
import com.bone.iam.application.command.cmd.CreateMenuCommand;
import com.bone.iam.application.command.cmd.DeleteMenuCommand;
import com.bone.iam.application.query.dto.MenuNode;
import com.bone.iam.application.query.dto.MenuTreeDTO;
import com.bone.iam.application.query.qry.MenuCurrentQuery;
import com.bone.iam.application.query.qry.MenuTreeQuery;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(PlatformApiPaths.IAM_V1 + "/menus")
@RequiredArgsConstructor
public class MenuController {

  private final MenuApplicationService menuApplicationService;
  private final MenuWebConverter menuWebConverter;

  @PostMapping
  @PreAuthorize("hasAuthority('iam:menus:write')")
  public ApiResponse<Long> create(@RequestBody CreateMenuReq req) {
    CreateMenuCommand cmd = menuWebConverter.toCreateMenuCommand(req);
    Long menuId = menuApplicationService.create(cmd);
    return ApiResponse.success(menuId);
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAuthority('iam:menus:write')")
  public ApiResponse<Void> update(@PathVariable Long id, @RequestBody UpdateMenuReq req) {
    menuApplicationService.update(menuWebConverter.toUpdateMenuCommand(id, req));
    return ApiResponse.success();
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAuthority('iam:menus:write')")
  public ApiResponse<Void> delete(@PathVariable Long id) {
    menuApplicationService.delete(new DeleteMenuCommand(id));
    return ApiResponse.success();
  }

  @GetMapping("/tree")
  @PreAuthorize("hasAuthority('iam:menus:read')")
  public ApiResponse<List<MenuTreeDTO>> tree(MenuTreeQuery qry) {
    return ApiResponse.success(menuApplicationService.tree(qry));
  }

  @GetMapping("/current")
  @PreAuthorize("hasAuthority('iam:menus:read')")
  public ApiResponse<List<MenuNode>> current(MenuCurrentQuery qry) {
    return ApiResponse.success(menuApplicationService.current(qry));
  }
}
