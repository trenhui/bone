package com.bone.core.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/** PageResult 分页结果模型测试 */
class PageResultTest {

  @Test
  void of_calculatesPaginationFields() {
    PageResult<String> page = PageResult.of(List.of("a", "b", "c"), 23L, 2, 10);
    assertThat(page.getRecords()).hasSize(3);
    assertThat(page.getTotal()).isEqualTo(23L);
    assertThat(page.getPage()).isEqualTo(2);
    assertThat(page.getSize()).isEqualTo(10);
    assertThat(page.getPages()).isEqualTo(3); // ceil(23/10)
    assertThat(page.getHasPrevious()).isTrue();
    assertThat(page.getHasNext()).isTrue(); // page 2 < pages 3
    assertThat(page.isEmpty()).isFalse();
  }

  @Test
  void of_lastPageHasNoNext() {
    PageResult<String> page = PageResult.of(Collections.emptyList(), 30L, 3, 10);
    assertThat(page.getHasNext()).isFalse();
    assertThat(page.getPages()).isEqualTo(3);
  }

  @Test
  void of_normalizesInvalidArgs() {
    PageResult<String> page = PageResult.of(null, null, 0, -5);
    assertThat(page.getRecords()).isEmpty();
    assertThat(page.getTotal()).isZero();
    assertThat(page.getPage()).isEqualTo(1);
    // size 下限保护为 1（Math.max(size, 1)）
    assertThat(page.getSize()).isEqualTo(1);
  }

  @Test
  void empty_returnsSafeDefaults() {
    PageResult<String> page = PageResult.empty();
    assertThat(page.getRecords()).isEmpty();
    assertThat(page.getTotal()).isZero();
    assertThat(page.getPage()).isEqualTo(1);
    assertThat(page.getSize()).isEqualTo(10);
    assertThat(page.isEmpty()).isTrue();
  }

  @Test
  void cursorOf_setsCursorPagingSemantics() {
    PageResult<String> page = PageResult.cursorOf(List.of("x"), "next-token", 20);
    assertThat(page.getNextCursor()).isEqualTo("next-token");
    assertThat(page.getHasNext()).isTrue();
    assertThat(page.getHasPrevious()).isFalse();
    assertThat(page.getSize()).isEqualTo(20);
    // 游标分页不适用 total/page/pages
    assertThat(page.getTotal()).isNull();
    assertThat(page.getPage()).isNull();
    assertThat(page.getPages()).isNull();
  }

  @Test
  void cursorOf_blankCursorMeansNoMorePages() {
    PageResult<String> page = PageResult.cursorOf(List.of("x"), "", 20);
    assertThat(page.getHasNext()).isFalse();
  }

  @Test
  void getRecordCount_returnsListSize() {
    PageResult<String> page = PageResult.of(List.of("a", "b"), 2L, 1, 10);
    assertThat(page.getRecordCount()).isEqualTo(2);
  }

  @Test
  void of_withPageParamUsesParamPaging() {
    PageResult<String> page = PageResult.of(List.of("a"), 15L, PageParam.of(2, 5));

    assertThat(page.getPage()).isEqualTo(2);
    assertThat(page.getSize()).isEqualTo(5);
    assertThat(page.getPages()).isEqualTo(3);
  }

  @Test
  void getOffset_computesSqlOffset() {
    assertThat(PageResult.of(List.of(), 100L, 1, 10).getOffset()).isEqualTo(0);
    assertThat(PageResult.of(List.of(), 100L, 3, 10).getOffset()).isEqualTo(20);
    // 游标分页 page 为 null → offset 不适用，返回 null（调用方须判空，不能当 0 用）
    assertThat(PageResult.cursorOf(List.of(), "c", 10).getOffset()).isNull();
  }

  @Test
  @SuppressWarnings("deprecation")
  void deprecatedAccessorsAliasNewOnes() {
    PageResult<String> page = PageResult.of(List.of("a"), 1L, 2, 5);

    assertThat(page.getList()).isEqualTo(page.getRecords());
    assertThat(page.getPageNum()).isEqualTo(page.getPage());
    assertThat(page.getPageSize()).isEqualTo(page.getSize());
  }

  /** map 只转换记录类型，分页元信息（total/page/size）必须原样保留。 */
  @Test
  void map_convertsRecordTypeKeepingPagingMeta() {
    PageResult<Integer> page = PageResult.of(List.of(1, 2, 3), 30L, 2, 10);

    PageResult<String> mapped = page.map(String::valueOf);

    assertThat(mapped.getRecords()).containsExactly("1", "2", "3");
    assertThat(mapped.getTotal()).isEqualTo(30L);
    assertThat(mapped.getPage()).isEqualTo(2);
    assertThat(mapped.getSize()).isEqualTo(10);
    assertThat(mapped.getPages()).isEqualTo(3);
  }

  @Test
  void equalityIsValueBasedAndToStringIncludesMeta() {
    PageResult<String> a = PageResult.of(List.of("a"), 1L, 1, 10);
    PageResult<String> b = PageResult.of(List.of("a"), 1L, 1, 10);
    PageResult<String> c = PageResult.of(List.of("b"), 1L, 1, 10);

    assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    assertThat(a).isNotEqualTo(c);
    assertThat(a.toString()).contains("records").contains("total");
  }
}
