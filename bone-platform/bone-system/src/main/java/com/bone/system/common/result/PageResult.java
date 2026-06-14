package com.bone.system.common.result;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 分页结果
 *
 * @param <T> 数据类型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {
  private List<T> records;
  private long total;
  private int pageNum;
  private int pageSize;

  public static <T> PageResult<T> of(List<T> records, long total, int pageNum, int pageSize) {
    return PageResult.<T>builder()
        .records(records)
        .total(total)
        .pageNum(pageNum)
        .pageSize(pageSize)
        .build();
  }

  /**
   * 转换分页结果中的数据类型
   *
   * @param mapper 转换函数
   * @param <U> 目标类型
   * @return 转换后的分页结果
   */
  public <U> PageResult<U> map(Function<T, U> mapper) {
    List<U> mappedRecords = records.stream().map(mapper).collect(Collectors.toList());
    return PageResult.of(mappedRecords, total, pageNum, pageSize);
  }
}
