package com.bone.iam.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.core.web.PlatformApiPaths;
import com.bone.iam.adapter.web.converter.MenuWebConverter;
import com.bone.iam.adapter.web.dto.request.CreateMenuReq;
import com.bone.iam.adapter.web.dto.request.UpdateMenuReq;
import com.bone.iam.application.MenuApplicationService;
import com.bone.iam.application.command.CreateMenuCommand;
import com.bone.iam.application.command.DeleteMenuCommand;
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

  /**
   * 当前登录用户可见菜单树（租户 + 权限码双重过滤在 {@link MenuApplicationService#current} 内完成）。
   *
   * <p><b>为什么这里不挂 {@code iam:menus:read}</b>（2026-10-08 修正）：{@code iam:menus:read} 是
   * 「进入菜单管理页做增删改」的<b>管理面</b>码，普通角色不会被授予。挂在「读自己的菜单」这条链路上， 结果是除 IAM 管理员外所有人 403 ⇒ 前端只能回退静态菜单 ⇒
   * 按租户/角色过滤的能力完全失效。 自助读 ≠ 管理面读：主体取自 JWT，返回内容已按该主体的租户与权限码收敛，不存在越权面； URL 层 {@code
   * anyRequest().authenticated()} 已是唯一必要的认证门禁。
   */
  @GetMapping("/current")
  public ApiResponse<List<MenuNode>> current(MenuCurrentQuery qry) {
    return ApiResponse.success(menuApplicationService.current(qry));
  }
}
