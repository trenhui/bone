package com.bone.metadata.runtime.application;

import com.bone.metadata.runtime.domain.RuntimeActionDef;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 动作发现（Describe）·<strong>唯一形态</strong>：page 级 {@code meta.allowedActionsByStatus}。
 *
 * <p><b>为什么按 status 分组而不是 per-row</b>（Runtime 数据面设计 §4.3.1）：
 *
 * <ul>
 *   <li>「哪些动作有权限」「从 X 能否到 Y」只取决于 catalog + 用户，<strong>与行无关</strong>；
 *   <li>只有「当前 status」是行相关的；按 {@code (entityCode, status)} 分组一次算全量， 一页 20 行若只有 3 个不同 status，只需 3
 *       次判定；
 *   <li>N+1 因此<strong>结构性消失</strong>——不再依赖「前端不许逐行请求」的纪律约束；
 *   <li>详情页天然复用：前端已有该行 status，直接查本地 {@code allowedActionsByStatus[status]}，零额外请求。
 * </ul>
 *
 * <p><b>必须按权限裁剪</b>：无权限的动作<strong>不出现</strong>在返回里，而不是返回 {@code enabled:false}——
 * 后者会泄露「存在但你不能做」的信息，且把授权判断推给前端（前端裁剪可绕过，后端裁剪不可）。
 */
@Component
@RequiredArgsConstructor
public class RuntimeActionDescribeSupport {

  private final RuntimeActionDefRepository actionDefRepository;

  /**
   * 动作授权判据的<strong>唯一真源</strong>（2026-10-13 收敛）：本类曾各自复制一份判定，漏掉「逃逸动作必须动作级细码」约束， 导致持有 {@code
   * runtime:*:write} 的用户看得到「确认打款」按钮、点击却 403（按钮可见不可用， 比 {@code enabled:false}
   * 更糟——诱导越权探测且把授权判断推给用户）。现统一委托 {@link RuntimeAccessGuard#canInvoke}：展示裁剪与执行门禁逐行同一判据，结构性杜绝漂移。
   */
  private final RuntimeAccessGuard accessGuard;

  /**
   * @return {@code {"allowedActionsByStatus": {"DRAFT": [ {...}, ... ], "APPROVED": [...] }}}
   *     实体无任何动作定义时返回 {@code null}（PageResult.meta 为 null 时不序列化，避免无意义空对象）
   */
  public Map<String, Object> describe(long tenantId, String entityCode) {
    List<RuntimeActionDef> defs = actionDefRepository.listByEntity(tenantId, entityCode);
    if (defs.isEmpty()) {
      return null;
    }
    Map<String, List<Map<String, Object>>> grouped = new TreeMap<>();
    for (RuntimeActionDef def : defs) {
      if (!accessGuard.canInvoke(def)) {
        continue;
      }
      // from_status 为 null 表示「任意状态均可触发」：归入通配键 "*"，前端按行 status 查不到时兜底
      String key =
          (def.getFromStatus() == null || def.getFromStatus().isBlank())
              ? "*"
              : def.getFromStatus();
      grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(toView(def));
    }
    Map<String, Object> meta = new LinkedHashMap<>();
    meta.put("allowedActionsByStatus", grouped);
    return meta;
  }

  /** 动作视图（前端渲染按钮所需的最小集，含 params 声明 —— 前端禁止硬编码参数名）。 */
  private static Map<String, Object> toView(RuntimeActionDef def) {
    Map<String, Object> view = new LinkedHashMap<>();
    view.put("code", def.getActionCode());
    view.put("label", def.getLabel());
    view.put("kind", def.isEscape() ? "escape" : "state_transition");
    view.put("fromStatus", def.getFromStatus());
    view.put("toStatus", def.getToStatus());
    view.put("confirm", def.isEscape() || def.isRequiresIdempotency());
    view.put("requiresIdempotencyKey", def.isRequiresIdempotency());
    view.put("permission", def.getPermissionCode());
    if (def.getParams() != null && !def.getParams().isEmpty()) {
      List<Map<String, Object>> params = new ArrayList<>();
      def.getParams()
          .forEach(
              p -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("code", p.getName());
                m.put("label", p.getLabel());
                m.put("type", p.getType());
                m.put("required", p.isRequired());
                m.put("enumValues", p.getValues());
                params.add(m);
              });
      view.put("params", params);
    }
    return view;
  }
}
