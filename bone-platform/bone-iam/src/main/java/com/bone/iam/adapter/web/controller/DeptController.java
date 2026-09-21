package com.bone.iam.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.core.web.PlatformApiPaths;
import com.bone.iam.adapter.web.converter.DeptWebConverter;
import com.bone.iam.adapter.web.dto.request.CreateDeptReq;
import com.bone.iam.adapter.web.dto.request.UpdateDeptReq;
import com.bone.iam.application.DeptApplicationService;
import com.bone.iam.application.command.CreateDeptCommand;
import com.bone.iam.application.command.DeleteDeptCommand;
import com.bone.iam.application.query.dto.DeptTreeDTO;
import com.bone.iam.application.query.qry.DeptTreeQuery;
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
@RequestMapping(PlatformApiPaths.IAM_V1 + "/depts")
@RequiredArgsConstructor
public class DeptController {

  private final DeptApplicationService deptApplicationService;
  private final DeptWebConverter deptWebConverter;

  @PostMapping
  @PreAuthorize("hasAuthority('iam:depts:write')")
  public ApiResponse<Long> create(@RequestBody CreateDeptReq req) {
    CreateDeptCommand cmd = deptWebConverter.toCreateDeptCommand(req);
    Long deptId = deptApplicationService.create(cmd);
    return ApiResponse.success(deptId);
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAuthority('iam:depts:write')")
  public ApiResponse<Void> update(@PathVariable Long id, @RequestBody UpdateDeptReq req) {
    deptApplicationService.update(deptWebConverter.toUpdateDeptCommand(id, req));
    return ApiResponse.success();
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAuthority('iam:depts:write')")
  public ApiResponse<Void> delete(@PathVariable Long id) {
    deptApplicationService.delete(new DeleteDeptCommand(id));
    return ApiResponse.success();
  }

  @GetMapping("/tree")
  @PreAuthorize("hasAuthority('iam:depts:read')")
  public ApiResponse<List<DeptTreeDTO>> tree(DeptTreeQuery qry) {
    return ApiResponse.success(deptApplicationService.tree(qry));
  }
}
