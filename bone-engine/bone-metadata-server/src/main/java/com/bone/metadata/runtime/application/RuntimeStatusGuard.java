package com.bone.metadata.runtime.application;

import com.bone.metadata.engine.runtime.MetadataErrorCodes;
import com.bone.metadata.engine.runtime.RuntimeRecordException;
import com.bone.metadata.runtime.domain.RuntimeActionDef;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 状态机守卫：状态字段由动作 SPI 独占所有，禁止经普通 CRUD 直写。
 *
 * <p>背景（G14 回归实测）：{@code meta_entity_action_def} 声明了状态机后， 普通 create / update 仍可携带 {@code
 * status=PAID} 直接落库（客户端可跳过 DRAFT→SUBMITTED→APPROVED 铸造终态单据）。 本守卫在 Controller 入口收口：
 *
 * <ul>
 *   <li><strong>create</strong>：**剥**离**客**户**端** status，**强**制**注**入**状**态**机**入**口**状**态**（0041
 *       起：**显**式**入**口**动**作** {@code is_entry} 的 {@code from_status} **优**先**，**回**退** {@code
 *       from_status} 集**合**减** {@code to_status} 集**合**的**无**入**边**者**）。**入**口**不**可**推**导**时**
 *       fail-closed（status 置**空** → **交**由**必**填**校**验**拒**绝**）， **绝**不**采**信**客**户**端**值**。
 *   <li><strong>update</strong>：仅剥离 status —— 状态只允许经动作端点（{@code POST /records/{id}/actions/{code}}）
 *       流转。
 * </ul>
 *
 * <p>动作执行链路（{@link RuntimeActionApplicationService}）在 application 层直接调用 {@code
 * JdbcRuntimeRecordService.update} 流转状态，不经过本守卫（守卫挂在 adapter 的 HTTP 入口）， 因此合法流转不受影响 ——
 * 这正是「只有动作端点能改状态」的边界。
 */
@Component
@RequiredArgsConstructor
public class RuntimeStatusGuard {

  private static final String DEFAULT_STATUS_FIELD = "status";

  private final RuntimeActionDefRepository actionDefRepository;

  /**
   * create 守卫：客户端<strong>不得</strong>携带状态字段（携带即 422）。
   *
   * <p>为何从「静默剥离 + 注入入口状态」改为「显式拒绝」：静默剥离会让调用方误以为自己提交的 {@code status=PAID}
   * 已生效，从而在「以为已打款」的前提下继续下游业务（Runtime §4.11 禁止项 6）。 入口状态的注入下沉到 {@code JdbcRuntimeRecordService}
   * 写路径（唯一出口），此处只做快速失败。
   */
  public void applyCreateGuard(long tenantId, String entityCode, Map<String, Object> body) {
    List<RuntimeActionDef> defs = actionDefRepository.listByEntity(tenantId, entityCode);
    if (defs.isEmpty()) {
      return;
    }
    rejectIfPresent(body, statusFieldOf(defs), "POST");
  }

  /** update 守卫：状态字段只允许经动作端点流转，客户端携带即 422。 */
  public void applyUpdateGuard(long tenantId, String entityCode, Map<String, Object> body) {
    List<RuntimeActionDef> defs = actionDefRepository.listByEntity(tenantId, entityCode);
    if (defs.isEmpty()) {
      return;
    }
    rejectIfPresent(body, statusFieldOf(defs), "PUT");
  }

  private static void rejectIfPresent(Map<String, Object> body, String statusField, String op) {
    if (body != null && body.containsKey(statusField)) {
      throw new RuntimeRecordException(
          MetadataErrorCodes.RUNTIME_FIELD_PROTECTED,
          "字段「"
              + statusField
              + "」为状态机独占字段，禁止经 "
              + op
              + " 直写；唯一合法入口是 POST /api/v1/runtime/entities/{entityCode}/records/{id}/actions/{actionCode}");
    }
  }

  /** 状态机入口状态（供 {@code RuntimeStateEntryResolver} bean 使用，创建时由平台注入）。 */
  public String entryStatus(long tenantId, String entityCode) {
    List<RuntimeActionDef> defs = actionDefRepository.listByEntity(tenantId, entityCode);
    return defs.isEmpty() ? null : entryStatusOf(defs);
  }

  /** 状态机作用字段（各动作定义一致；缺省 {@code status}）。 */
  private String statusFieldOf(List<RuntimeActionDef> defs) {
    String field = defs.get(0).getStatusField();
    return field != null && !field.isBlank() ? field : DEFAULT_STATUS_FIELD;
  }

  /**
   * 入口状态推导（0041）：<b>显式入口动作优先，图推导兜底，都不可推导则 fail-closed</b>。
   *
   * <ol>
   *   <li><b>显式入口动作</b>（{@code is_entry=1}，按 {@code sort_order} 取首个）：其 {@code from_status} 即初始状态。
   *       这是 UML 状态图「初始状态伪状态」的等价物——全环状态机（如设备巡检 {@code OPEN→CLOSED→OPEN}）每个状态都有入边，
   *       图推导无解，必须由建模者显式声明，否则「创建记录」死锁（不传 → 必填失败；传 → 422）。
   *   <li><b>图推导</b>：{@code from_status} 集合 − {@code to_status} 集合（无入边者）。
   *   <li>都不可推导 → {@code null}（fail-closed，由必填校验拒绝，绝不采信客户端值）。
   * </ol>
   *
   * <p>显式优先于推断：建模者声明即意图，推断只在前者缺席时生效。入口动作要求 {@code from_status} 非空（空白视为未声明，回退图推导）。 {@code
   * from_status IS NULL} 的「任意状态可触发」边<b>不是</b>入口候选——入口与任意态是两个正交语义。
   */
  private String entryStatusOf(List<RuntimeActionDef> defs) {
    List<RuntimeActionDef> sorted =
        defs.stream().sorted(Comparator.comparingInt(RuntimeActionDef::getSortOrder)).toList();
    String explicit =
        sorted.stream()
            .filter(RuntimeActionDef::isEntry)
            .map(RuntimeActionDef::getFromStatus)
            .filter(s -> s != null && !s.isBlank())
            .findFirst()
            .orElse(null);
    if (explicit != null) {
      return explicit;
    }
    Set<String> targets =
        sorted.stream()
            .map(RuntimeActionDef::getToStatus)
            .filter(s -> s != null && !s.isBlank())
            .collect(Collectors.toSet());
    return sorted.stream()
        .map(RuntimeActionDef::getFromStatus)
        .filter(s -> s != null && !s.isBlank() && !targets.contains(s))
        .findFirst()
        .orElse(null);
  }
}
