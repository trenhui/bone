package com.bone.claim.common;

import com.bone.core.model.PageParam;

/**
 * 分页入参的收窄边界（页码 ≥ 1、每页 ≤ {@link PageParam#MAX_PAGE_SIZE}）。
 *
 * <p><b>为什么必须在应用服务入口收窄</b>：理赔模块的分页端点全部用裸 {@code @RequestParam int page, int size} 接参，而
 * {@code @Min}/{@code @Max} 只在「入参是 {@code @Valid} 对象」时才执行 —— 裸基本类型绕过了整个 bean validation。于是 {@code
 * size=10000000} 会被原样传给 SDK 的 {@code Criteria.page()}，一次请求把全表读进内存； {@code size=0} 则算出负
 * offset。这类输入不需要恶意，前端翻页组件写错或脚本调接口就会触发。
 *
 * <p><b>为什么收窄而不是拒绝</b>：与框架 {@link PageParam#of} 同口径（对越界值收敛而非抛错）。分页上限属于「客户端写错了
 * 也不该炸服务」的防御性约束；真正需要严格校验的是业务语义（金额、状态），不是页大小。若要拒绝，调用方还得区分 {@code size} 越界与其它 400，收益不抵成本。
 *
 * <p><b>为什么在 application 层而不是 controller</b>：同一个分页服务会被多个端点调用（如 {@code ReviewTaskController} 直接把裸
 * {@code page}/{@code size} 透传），收窄放在服务入口才能保证「新增调用方不会绕过」—— 这与模块内既有的 「判据只留一处真源」是同一条约束。
 *
 * @param page 页码，从 1 开始
 * @param size 每页条数，落在 {@code [1, PageParam.MAX_PAGE_SIZE]}
 */
public record PageBounds(int page, int size) {

  /**
   * 收敛分页入参。
   *
   * @param page 原始页码（≤ 0 或负数一律收敛为 1）
   * @param size 原始每页条数（≤ 0 收敛为 1，超过上限收敛为上限）
   */
  public static PageBounds of(int page, int size) {
    return new PageBounds(Math.max(1, page), PageParam.clampSize(size));
  }
}
