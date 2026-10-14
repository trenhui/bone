package com.bone.metadata.runtime.adapter.web;

import com.bone.core.model.ApiResponse;
import com.bone.metadata.runtime.application.RuntimeAccessGuard;
import com.bone.metadata.runtime.application.RuntimeRelationApplicationService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * G10 运行时关系端点：引用候选（lookup）、子表（hasMany）CRUD。
 *
 * <p><b>路径形状遵循「动态资源」原则</b>（Runtime 设计 §4）：一切以 {@code entityCode} + 字段编码为键，不为任何业务开名词路径。前端换场景只换 code，
 * 不换路由模板。
 *
 * <p><b>与 {@link RuntimeRecordController} 的分工</b>：本类只管「关系」——引用候选与子表行；单表CRUD 仍在 {@code
 * RuntimeRecordController}，避免把 记录与关系混在一个控制器里让权限与校验规则互相干扰。
 *
 * <p><b>分层</b>（ADR-0028）：控制器只做参数绑定 + 鉴权注解 + 信封组装；实体定位、关系编排、事务边界与行级 ACL 判定全部在 {@link
 * RuntimeRelationApplicationService}。本类不持有任何 engine 持久化服务（P2-1 收敛： 原直注的 {@code
 * JdbcRuntimeRecordService} / {@code RuntimeRelationSupport} 曾绕过 god-object 门禁谓词）。
 */
@RestController
@RequestMapping("/api/v1/runtime/entities/{entityCode}")
@RequiredArgsConstructor
public class RuntimeRelationController {

  private final RuntimeRelationApplicationService relationService;
  private final RuntimeAccessGuard accessGuard;

  /**
   * 引用候选列表：给关联选择器用。
   *
   * <p><b>为什么单独一个端点而不把 label 嵌进列表</b>：候选是「按需、限量、带关键字」的查询，与记录列表的分页语义完全不同；且权限上候选读取应能独立
   * 收口。前端在选择器展开时调用，未展开则零请求。
   */
  @GetMapping("/lookup-options")
  @PreAuthorize("@runtimeAccessGuard.canRead(#entityCode)")
  public ApiResponse<List<Map<String, Object>>> lookupOptions(
      @PathVariable String entityCode,
      @RequestParam String field,
      @RequestParam(required = false) String q,
      @RequestParam(defaultValue = "50") int limit) {
    accessGuard.requireRead(entityCode);
    return ApiResponse.success(relationService.lookupOptions(entityCode, field, q, limit));
  }

  /** 列出一行父记录的全部子行。 */
  @GetMapping("/records/{id}/children/{childEntityCode}")
  @PreAuthorize("@runtimeAccessGuard.canRead(#entityCode)")
  public ApiResponse<List<Map<String, Object>>> listChildren(
      @PathVariable String entityCode,
      @PathVariable String id,
      @PathVariable String childEntityCode) {
    accessGuard.requireRead(entityCode);
    accessGuard.requireRead(childEntityCode);
    return ApiResponse.success(relationService.listChildren(entityCode, id, childEntityCode));
  }

  /**
   * 批量替换子表行（父记录表单一次性提交明细）。
   *
   * <p>整个替换在一个事务内（{@code RuntimeRelationApplicationService#replaceChildren} 持事务边界）：父行存在性校验 + 子行增删改
   * + 孤儿软删 + 明细金额聚合回填必须原子。响应保持 List 契约不变，前端保存明细成功后 re-fetch 主记录对齐头表金额展示。
   */
  @PostMapping("/records/{id}/children/{childEntityCode}")
  @PreAuthorize("@runtimeAccessGuard.canWrite(#entityCode)")
  public ApiResponse<List<Map<String, Object>>> replaceChildren(
      @PathVariable String entityCode,
      @PathVariable String id,
      @PathVariable String childEntityCode,
      @RequestBody List<Map<String, Object>> rows) {
    accessGuard.requireWrite(entityCode);
    accessGuard.requireWrite(childEntityCode);
    return ApiResponse.success(
        relationService.replaceChildren(entityCode, id, childEntityCode, rows));
  }
}
