package com.bone.core.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 游标（keyset）分页参数模型 —— 与 {@link PageParam} 并列的第二种分页真源。
 *
 * <p><b>为何与 {@link PageParam} 并列而不是合并</b>：两者语义不同、不可互相替代。
 *
 * <ul>
 *   <li>{@code offset}（{@link PageParam}）：靠「第几页」定位，深翻时数据库要扫过前 N 页并 <b>随插入漂移</b>（新数据落在第 2 页会导致第 2
 *       页重复/漏读），适合总量可控的后台列表；
 *   <li>{@code cursor}（本类）：靠「上一页末位主键」定位，<b>不随插入漂移</b>且深翻代价恒定， 适合大表日志流/审计流；但<b>无法跳页、也不返回总数</b>
 *       （{@code SELECT count(*)} 在大表上不可接受）。
 * </ul>
 *
 * 强行统一成一种，等于让其中一种范式在不擅长的场景里假装能用（最常见的是把cursor 硬塞进 {@code page/size}，于是深翻性能退化成 offset 且仍在漂移）。
 *
 * <p><b>用法</b>：入参类继承本类只声明领域过滤条件；翻页循环以 {@code nextCursor == null || isBlank()} 为终止条件（{@link
 * PageResult#getNextCursor()} 驱动）。 出参用 {@link PageResult#cursorOf}，它会把 {@code total/page/pages}
 * 置null 并设 {@code hasNext}。
 *
 * <p><b>与 {@code CursorCodec} 的分工</b>：本类只承载「传输层的游标字符串 + 行数上限」， <b>不关心游标怎么编码</b>。编码策略（如
 * extension-studio 的 base64({@code id:}+末位 id)） 属基础设施细节，由各模块的 codec 负责，本类不做假设。
 *
 * @see PageParam offset 分页参数（另一范式，勿混用）
 */
@Data
@EqualsAndHashCode(callSuper = false)
@ToString(callSuper = false)
public class CursorPageParam implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 默认行数上限（与 {@link PageParam} 的默认 size 一致）。 */
  public static final int DEFAULT_LIMIT = 10;

  /** 行数上限上限，防止 {@code limit=Integer.MAX_VALUE} 打满内存/数据库。 */
  public static final int MAX_LIMIT = 100;

  /**
   * 上一页返回的游标；首页为 {@code null}。
   *
   * <p><b>刻意不做 {@code @NotBlank} / 格式校验</b>：游标是<b>不透明串</b>（可能是 base64、 也可能是复合排序键），其合法性由服务端的 codec
   * 判定。此处若加正则，等于把某一种编码策略 硬编码进传输层，换策略就得改基类 —— 这正是本类要避免的耦合。
   */
  private String cursor;

  /**
   * 单页行数上限（{@code @Min(1)} 防止非正数，{@code @Max(MAX_LIMIT)} 防止打满）。
   *
   * <p>游标分页通常比 offset 允许更大的单页（深翻本就要连续取数据）， 故上限独立取 {@value #MAX_LIMIT}，与 {@link
   * PageParam#MAX_PAGE_SIZE} 同值但不共用常量 —— 两者是不同范式的不同约束，共用常量会让「改一个连带改另一个」。
   */
  @Min(value = 1, message = "行数上限必须大于等于1")
  @Max(value = MAX_LIMIT, message = "行数上限不能超过100")
  private Integer limit = DEFAULT_LIMIT;

  /**
   * 按值构造并<b>做收窄</b>（下限 1、上限 {@value #MAX_LIMIT}），供裸 {@code @RequestParam} 场景使用。
   *
   * <p><b>为何必须收窄</b>：同 {@link PageParam#of(Integer, Integer)} —— {@code @Min}/{@code @Max} 只在 bean
   * validation 生效，裸 {@code @RequestParam} 不会触发 ⇒ {@code limit=0} 会让 「取 0 行」看起来像正常结果，{@code
   * limit=Integer.MAX_VALUE} 则把整表拉进内存。
   *
   * @param cursor 为 {@code null}/空白 时视为首页（不写入字段）
   * @param limit 为 {@code null} 时取 {@value #DEFAULT_LIMIT}
   */
  public static CursorPageParam of(String cursor, Integer limit) {
    CursorPageParam param = new CursorPageParam();
    if (cursor != null && !cursor.isBlank()) {
      param.setCursor(cursor.trim());
    }
    param.setLimit(clampLimit(limit));
    return param;
  }

  /**
   * 把任意入参收敛到 {@code [1, MAX_LIMIT]}。
   *
   * <p>与 {@link PageParam#clampSize(Integer)} 同构但<b>不复用</b>：两者上限虽同为 100，
   * 却是不同范式的不同约束，共用常量会让「调整其中一个连带影响另一个」。
   */
  public static int clampLimit(Integer limit) {
    if (limit == null) {
      return DEFAULT_LIMIT;
    }
    return Math.min(MAX_LIMIT, Math.max(1, limit));
  }

  /** 首次查询（无游标）。 */
  public static CursorPageParam firstPage(Integer limit) {
    return of(null, limit);
  }

  /** 是否有下一页游标 —— 翻页循环的终止条件。 */
  public boolean hasNext() {
    return cursor != null && !cursor.isBlank();
  }
}
