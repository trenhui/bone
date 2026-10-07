package com.bone.core.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.io.Serializable;
import lombok.Data;

/** 分页参数模型 */
@Data
public class PageParam implements Serializable {

  private static final long serialVersionUID = 1L;

  private static final Integer DEFAULT_PAGE = 1;
  private static final Integer DEFAULT_SIZE = 10;

  /** 每页大小上限，防止 {@code pageSize=Integer.MAX_VALUE} 打满数据库。 */
  public static final int MAX_PAGE_SIZE = 100;

  @Min(value = 1, message = "页码必须大于等于1")
  private Integer page = DEFAULT_PAGE;

  /**
   * 每页大小，上限 {@value #MAX_PAGE_SIZE}。
   *
   * <p><b>上限为何在基类而不在各入参类</b>：{@code size} 上限用于防止 {@code pageSize=Integer.MAX_VALUE}
   * 一类输入直接打满数据库。原先各入参类各自声明 {@code @Max(100)}，而本类无上限 ⇒ 两份定义需手工同步，任一处遗漏即漂移 （实测 {@code
   * OrderPageRequest} 有上限、{@code ApplicationPageQuery} 没有）。 收口到本类后<b>改一处全局生效</b>，且各入参类继承即可，无需重复声明。
   */
  @Min(value = 1, message = "每页大小必须大于等于1")
  @Max(value = MAX_PAGE_SIZE, message = "每页大小不能超过100")
  private Integer size = DEFAULT_SIZE;

  /**
   * 按值构造并<b>做收窄</b>（下限 1、上限 {@value #MAX_PAGE_SIZE}），供<b>裸 {@code @RequestParam}</b> 场景使用。
   *
   * <p><b>为何必须收窄而不是直接 set</b>：{@code @Min}/{@code @Max} 只在 bean validation 生效 （即入参是
   * {@code @Valid @ModelAttribute} 的对象时）。若控制器用裸 {@code @RequestParam Integer
   * size}，<b>校验注解完全不会执行</b> ⇒ {@code size=0} 会算出 负的 offset、{@code size=Integer.MAX_VALUE} 直接打满数据库。
   * 实测 extension-studio 的 {@code ExecutionLogs}/{@code auditLogs} 两个端点正是裸参数， 原先各自手写 {@code
   * Math.min(100, Math.max(1, size))}（同一逻辑在本类复制了多份）。 ⇒ 收窄逻辑下沉到本工厂，各调用点不再重复手写。
   *
   * @param size 为 {@code null} 时取 {@value #DEFAULT_SIZE}
   */
  public static PageParam of(Integer page, Integer size) {
    PageParam param = new PageParam();
    if (page != null) {
      param.setPage(Math.max(1, page));
    }
    if (size != null) {
      param.setSize(clampSize(size));
    }
    return param;
  }

  /**
   * 把任意入参收敛到 {@code [1, MAX_PAGE_SIZE]}。
   *
   * <p>抽成 public 是为了让 {@link CursorPageParam} 与各控制器复用同一套边界 —— 边界值只有一处定义，改上限才能全局生效。
   */
  public static int clampSize(Integer size) {
    if (size == null) {
      return DEFAULT_SIZE;
    }
    return Math.min(MAX_PAGE_SIZE, Math.max(1, size));
  }

  public static PageParam of() {
    return new PageParam();
  }
}
