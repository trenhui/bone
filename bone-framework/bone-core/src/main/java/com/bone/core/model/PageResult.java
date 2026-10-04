package com.bone.core.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.io.Serial;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import lombok.Data;

/** 分页结果模型 */
@Data
public class PageResult<T> implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  private List<T> records;

  private Long total;

  private Integer page;

  private Integer size;

  private Integer pages;
  private Boolean hasNext;
  private Boolean hasPrevious;

  /** 游标分页：下一页游标；offset 分页时为 null */
  private String nextCursor;

  // 私有构造方法
  private PageResult(List<T> records, Long total, Integer page, Integer size) {
    this.records = records != null ? records : Collections.emptyList();
    this.total = total != null ? Math.max(total, 0L) : 0L;
    this.page = page != null ? Math.max(page, 1) : 1;
    this.size = size != null ? Math.max(size, 1) : 10;
    calculateFields();
  }

  // === 静态工厂方法 ===
  public static <T> PageResult<T> of(List<T> records, Long total, Integer page, Integer size) {
    return new PageResult<>(records, total, page, size);
  }

  public static <T> PageResult<T> of(List<T> records, Long total, PageParam param) {
    return new PageResult<>(records, total, param.getPage(), param.getSize());
  }

  public static <T> PageResult<T> empty() {
    return new PageResult<>(Collections.emptyList(), 0L, 1, 10);
  }

  /** 游标分页结果（由 nextCursor 驱动翻页；total/page/pages 不适用）。 */
  public static <T> PageResult<T> cursorOf(List<T> records, String nextCursor, int limit) {
    PageResult<T> page = new PageResult<>(records, 0L, 1, limit);
    page.setTotal(null);
    page.setPage(null);
    page.setPages(null);
    page.setNextCursor(nextCursor);
    page.setHasNext(nextCursor != null && !nextCursor.isBlank());
    page.setHasPrevious(false);
    return page;
  }

  // === 业务方法 ===
  private void calculateFields() {
    this.pages = (int) Math.ceil((double) this.total / this.size);
    this.hasPrevious = this.page > 1;
    this.hasNext = this.page < this.pages;
  }

  public Boolean isEmpty() {
    return this.records.isEmpty();
  }

  public Integer getRecordCount() {
    return this.records.size();
  }

  public Integer getOffset() {
    if (this.page == null) {
      return null;
    }
    return (this.page - 1) * this.size;
  }

  /**
   * 兼容旧 API 的废弃方法。
   *
   * <p><b>2026-10-03 收敛为 {@code @JsonIgnore}</b>：这 3 个 getter此前只标了 {@code @Deprecated}，而 <b>Jackson
   * 默认不因 {@code @Deprecated} 忽略 getter</b>， 故序列化时 {@code records} 与 {@code list} 两组键<b>同时存在</b>
   * （实测响应 14 个键，见 {@code Bone-API-规范.md} §5.3）。
   *
   * <p><b>顺序不可颠倒</b>：必须先把前端全部改读 {@code records}，才能加本注解 —— 否则响应里 {@code list} 消失，读它的页面直接白屏。前端迁移由门禁
   * {@code scripts/check-paging-current-field.py}（check.sh [14/17]）守护。
   *
   * <p><b>保留方法本身、不删</b>：它们是 {@code PageResult} 的公共 API， 删掉会破坏二进制兼容性（{@code map()} 等内部方法也在用）， 且
   * SDK/第三方可能仍在调用。{@code @JsonIgnore} 只切断序列化出口。
   *
   * <p><b>本类是纯出站响应模型</b>：只有私有全参构造，无无参构造也无 {@code @JsonCreator}，Jackson <b>无法反序列化本类</b>（实测抛 {@code
   * InvalidDefinitionException: no Creators}）；全仓亦无任何 {@code PageResult} 反序列化入口（无
   * {@code @RequestBody PageResult}，无 {@code readValue(..., PageResult.class)}）。<b>故不可把任何入参别名当成删除废弃
   * getter 的安全网</b>——历史上挂在字段上的 {@code @JsonAlias} 从未生效过，已于 2026-10-04 移除。本类只输出、不接收。
   */

  /**
   * @deprecated 使用 {@link #getRecords()} 代替
   */
  @Deprecated
  @JsonIgnore
  public List<T> getList() {
    return this.records;
  }

  /**
   * @deprecated 使用 {@link #getPage()} 代替
   */
  @Deprecated
  @JsonIgnore
  public Integer getPageNum() {
    return this.page;
  }

  /**
   * @deprecated 使用 {@link #getSize()} 代替
   */
  @Deprecated
  @JsonIgnore
  public Integer getPageSize() {
    return this.size;
  }

  /**
   * 转换分页结果中的数据类型
   *
   * @param mapper 转换函数
   * @param <U> 目标类型
   * @return 转换后的分页结果
   */
  public <U> PageResult<U> map(java.util.function.Function<T, U> mapper) {
    java.util.List<U> mappedRecords =
        records.stream().map(mapper).collect(java.util.stream.Collectors.toList());
    return PageResult.of(mappedRecords, total, page, size);
  }
}
