package com.bone.core.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import java.io.Serial;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import lombok.Data;

/** 分页结果模型 */
@Data
public class PageResult<T> implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  @JsonAlias("list")
  private List<T> records;

  private Long total;

  @JsonAlias("pageNum")
  private Integer page;

  @JsonAlias("pageSize")
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

  // === 兼容旧 API 的废弃方法 ===

  /**
   * @deprecated 使用 {@link #getRecords()} 代替
   */
  @Deprecated
  public List<T> getList() {
    return this.records;
  }

  /**
   * @deprecated 使用 {@link #getPage()} 代替
   */
  @Deprecated
  public Integer getPageNum() {
    return this.page;
  }

  /**
   * @deprecated 使用 {@link #getSize()} 代替
   */
  @Deprecated
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
