package com.bone.metadata.catalog.application.command.cmd;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/**
 * 「登记/更新实体动作定义」入参（状态机建模，POST {@code /entities/{id}/action-defs}）。
 *
 * <p><b>只收建模者该填的字段</b>：{@code permissionCode} 不开放——它按 {@code runtime:{entityCode}:{actionCode}}
 * 由服务端推导 （{@code ActionDefSpec#permissionCodeOf}）。放开会让细码与动作码脱钩，逃逸动作因此永远命中不了授权（Runtime 数据面设计 §4.6）。
 *
 * <p>{@code actionCode} 走标识符白名单：动作码进 URL 路径段（{@code
 * /records/{id}/actions/{actionCode}}），宽松字符会破坏路由与审计日志对齐。
 */
@Getter
@Setter
public class UpsertActionDefCommand {

  @NotBlank
  @Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9_]*$", message = "动作编码须为字母开头的字母数字下划线")
  private String actionCode;

  @NotBlank private String label;

  /** 状态机边起点；不传 = 任意状态均可触发。 */
  private String fromStatus;

  /** 状态机边终点；不传 = 仅记动作不流转状态（如「催办」「转办」）。 */
  private String toStatus;

  /** 被流转的状态字段 code；不传落库为 {@code status}。 */
  private String statusField;

  /** 逃逸动作（有资金/外部副作用），须命中动作级细码；默认 false。 */
  private Boolean escape;

  /** 逃逸动作处理器 bean 名；非逃逸动作留空走内置 state_transition。 */
  private String handlerBean;

  /** 动作入参 JSON Schema（前端据此渲染表单，后端据此校验必填）。 */
  private String paramsSchema;

  /** 是否要求 {@code Idempotency-Key} 头；资金类动作应为 true。 */
  private Boolean requiresIdempotency;

  /** 同状态内动作排序；缺省 0。 */
  private Integer sortOrder;

  /**
   * 入口动作标记（0041）：{@code true} 时其 {@code fromStatus} 即新记录初始状态。
   *
   * <p><b>什么时候必须传</b>：全环状态机（每个状态都有入边，如巡检 {@code OPEN→CLOSED→OPEN}）图推导不出入口， 不标记则运行期「创建记录」死锁（不传
   * status → 必填 400；传 → 422）。非全环机器可不传（回退图推导）。 与 {@code fromStatus} 不传（任意态可触发）是两个正交语义，互不替代。
   */
  private Boolean entry;
}
