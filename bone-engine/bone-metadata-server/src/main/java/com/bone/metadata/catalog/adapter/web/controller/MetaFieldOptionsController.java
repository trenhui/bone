package com.bone.metadata.catalog.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.metadata.catalog.application.EnumSourceApplicationService;
import com.bone.metadata.catalog.application.EnumSourceApplicationService.DictBinding;
import com.bone.metadata.catalog.application.EnumSourceApplicationService.DriftDiff;
import com.bone.metadata.catalog.application.EnumSourceApplicationService.MaterializeResult;
import com.bone.metadata.catalog.domain.model.meta.OptionItem;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 枚举选项源端点（doc/design/modules/11 §9）：实时解析、手动刷新物化、快照差异对比。
 *
 * <p><b>与字段 CRUD 的路径差异</b>：字段 CRUD 嵌套在实体资源下（{@code /entities/{entityId}/fields}），
 * 本控制器是<b>字段级</b>操作（选项与字段绑定，与实体无关），故挂平级 {@code /fields/{fieldId}}—— 调用方（设计器字典面板）持有 fieldId 而非
 * entityId。
 *
 * <p><b>读端点为何不挂 @PreAuthorize</b>：同 bone-system {@code DictItemController#options} 的既定姿态——
 * 字典是「每个表单都要读」的基础数据，挂一个未登记的读码会让所有消费方 403；认证由 SecurityConfig 的 {@code anyRequest().authenticated()}
 * 统一保证。
 */
@RestController
@RequestMapping("/api/v1/metadata/fields")
@RequiredArgsConstructor
@Tag(name = "枚举选项源", description = "字段选项源实时解析 / 物化刷新 / 差异对比")
public class MetaFieldOptionsController {

  private final EnumSourceApplicationService enumSourceApplicationService;

  @GetMapping("/{fieldId}/options")
  @Operation(summary = "实时解析选项", description = "设计器预览用：dict 来源返实时选项，inline 返已物化快照")
  public ApiResponse<List<OptionItem>> options(@PathVariable("fieldId") Long fieldId) {
    return ApiResponse.success(enumSourceApplicationService.liveOptions(fieldId));
  }

  @GetMapping("/{fieldId}/option-diff")
  @Operation(
      summary = "快照 vs 真源差异",
      description = "dict 来源返 added/removed/labelChanged/stateChanged；真源不可达时 resolved=false")
  public ApiResponse<DriftDiff> optionDiff(@PathVariable("fieldId") Long fieldId) {
    return ApiResponse.success(enumSourceApplicationService.diff(fieldId));
  }

  /**
   * 手动刷新物化快照。
   *
   * <p><b>权限码说明</b>：doc 11 §9 写的 {@code metadata:field:write} <b>未在 IAM 权限目录登记</b>
   * （DefaultPermissionCodes 只有 metadata:model:write 等）——挂未登记码 = 所有调用方 403 （同 DictItemController
   * 注释记录的欠账）。故复用字段 CRUD 同码 {@code metadata:model:write}； 若后续要在权限目录正式登记 field 级写码，应连同 doc 11
   * 一并修订，而不是让端点先坏掉。
   */
  @PostMapping("/{fieldId}/options/refresh")
  @Operation(summary = "重新物化快照", description = "按 enum_source 重新解析并覆写 enum_values；失败保留旧快照并报错")
  @PreAuthorize("hasAnyAuthority('metadata:model:write', 'metadata:write')")
  public ApiResponse<MaterializeResult> refresh(@PathVariable("fieldId") Long fieldId) {
    return ApiResponse.success(enumSourceApplicationService.refresh(fieldId));
  }

  /**
   * 字典引用方清单（界面设计器字典打通 v1.1 §5.4）：某值域被哪些实体/字段绑定 + 各自快照新鲜度。
   *
   * <p>服务字典管理页的「被引用情况」与「漂移告警入口」——改值域前先看谁会受影响 （Salesforce Global Picklist 变更提示的产品化）。字面路径段优先于 {@code
   * /{fieldId}} 匹配，无路由冲突。
   *
   * <p>读端点不挂 @PreAuthorize（同本类其它读端点）：字典是基础数据，且返回的是建模元数据而非字典内容本身。
   */
  @GetMapping("/dict-bindings")
  @Operation(
      summary = "字典引用方清单",
      description = "按值域编码返回绑定字段（实体/字段名 + 快照项数 + 漂移状态）；真源不可达时 resolved=false")
  public ApiResponse<List<DictBinding>> dictBindings(@RequestParam("typeCode") String typeCode) {
    return ApiResponse.success(enumSourceApplicationService.listBindings(typeCode));
  }
}
