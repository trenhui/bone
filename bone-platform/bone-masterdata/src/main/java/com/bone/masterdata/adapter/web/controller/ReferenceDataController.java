package com.bone.masterdata.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.core.web.PlatformApiPaths;
import com.bone.masterdata.application.ReferenceDataApplicationService;
import com.bone.masterdata.application.command.CreateReferenceSetCommand;
import com.bone.masterdata.application.command.CreateReferenceValueCommand;
import com.bone.masterdata.application.command.UpdateReferenceSetCommand;
import com.bone.masterdata.application.command.UpdateReferenceValueCommand;
import com.bone.masterdata.application.query.dto.ReferenceValueView;
import com.bone.masterdata.domain.model.reference.ReferenceSet;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** 参考数据控制器（G15 / §2.3）。 */
@RestController
@RequestMapping(PlatformApiPaths.MASTERDATA_V1 + "/reference-sets")
@RequiredArgsConstructor
public class ReferenceDataController {

  private final ReferenceDataApplicationService referenceService;

  @PreAuthorize("hasAuthority('masterdata:reference:write')")
  @PostMapping
  public ApiResponse<Long> createSet(@Valid @RequestBody CreateReferenceSetCommand cmd) {
    return ApiResponse.success(referenceService.createSet(cmd));
  }

  @PreAuthorize("hasAuthority('masterdata:reference:write')")
  @PutMapping("/{id}")
  public ApiResponse<Void> updateSet(
      @PathVariable Long id, @Valid @RequestBody UpdateReferenceSetCommand cmd) {
    cmd.setId(id);
    referenceService.updateSet(cmd);
    return ApiResponse.success();
  }

  @PreAuthorize("hasAuthority('masterdata:reference:write')")
  @PostMapping("/{id}/archive")
  public ApiResponse<Void> archiveSet(@PathVariable Long id) {
    referenceService.archiveSet(id);
    return ApiResponse.success();
  }

  @GetMapping
  public ApiResponse<List<ReferenceSet>> sets() {
    return ApiResponse.success(referenceService.sets());
  }

  @PreAuthorize("hasAuthority('masterdata:reference:write')")
  @PostMapping("/{id}/values")
  public ApiResponse<Long> createValue(
      @PathVariable Long id, @Valid @RequestBody CreateReferenceValueCommand cmd) {
    cmd.setSetId(id);
    return ApiResponse.success(referenceService.createValue(cmd));
  }

  @PreAuthorize("hasAuthority('masterdata:reference:write')")
  @PutMapping("/values/{valueId}")
  public ApiResponse<Void> updateValue(
      @PathVariable Long valueId, @Valid @RequestBody UpdateReferenceValueCommand cmd) {
    cmd.setId(valueId);
    referenceService.updateValue(cmd);
    return ApiResponse.success();
  }

  @PreAuthorize("hasAuthority('masterdata:reference:write')")
  @PostMapping("/values/{valueId}/disable")
  public ApiResponse<Void> disableValue(@PathVariable Long valueId) {
    referenceService.disableValue(valueId);
    return ApiResponse.success();
  }

  @GetMapping("/{id}/values")
  public ApiResponse<List<ReferenceValueView>> values(@PathVariable Long id) {
    return ApiResponse.success(referenceService.values(id));
  }
}
