package com.bone.core.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidDefinitionException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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

  /**
   * 序列化契约：响应只吐权威字段，废弃 getter 的 list/pageNum/pageSize 不得出现。
   *
   * <p><b>为何必须有这道测试</b>：Jackson <b>默认不因 {@code @Deprecated} 忽略 getter</b>， 所以在加 {@code @JsonIgnore}
   * 之前，响应里 {@code records} 与 {@code list} 两组键同时存在 （实测 14 个键，见 {@code Bone-API-规范.md} §5.3）。
   *
   * <p><b>收敛顺序不可颠倒</b>：先前端全改读 {@code records}，才能加 {@code @JsonIgnore}。 若顺序反了，读 {@code list}
   * 的页面直接白屏且前端类型检查抓不到 （`shared-types` 早已不声明 {@code list}，但运行时响应仍在）。
   *
   * <p>前端护栏见 {@code scripts/check-paging-current-field.py}（check.sh [14/17]）。
   */
  @Test
  void serializationOmitsDeprecatedAccessorKeys() throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    PageResult<String> page = PageResult.of(List.of("a", "b"), 3L, 2, 10);

    String json = mapper.writeValueAsString(page);
    // fieldNames() 返回的是 Iterator（Jackson 2.13+ 无 stream()），只能 forEach 收集
    Set<String> keys = new HashSet<>();
    mapper.readTree(json).fieldNames().forEachRemaining(keys::add);

    assertThat(keys).contains("records", "total", "page", "size", "pages");
    assertThat(keys)
        .as("废弃 getter 的键必须被 @JsonIgnore 抹掉（此前 records 与 list 同时出现）")
        .doesNotContain("list", "pageNum", "pageSize");
  }

  /**
   * {@code @JsonAlias} 现状登记：本类<b>根本不可反序列化</b>，故别名从未真正生效。
   *
   * <p><b>实测结论</b>（2026-10-03 独立 javac + jackson-databind 2.20.0 验证）：对 {@code PageResult} 调用 {@code
   * mapper.readValue(...)} 会抛 {@link
   * com.fasterxml.jackson.databind.exc.InvalidDefinitionException}，报 {@code no Creators, like
   * default constructor, exist} —— 因为本类只有私有全参构造，无无参构造也无 {@code @JsonCreator}。
   *
   * <p><b>为何要登记</b>：字段上的 {@code @JsonAlias("list")}常被误读成「{@code list} 键能作为入参 传入」，从而在删除废弃 getter
   * 时被当成安全网。实际上它<b>不提供任何保护</b>——全仓亦无任何 {@code PageResult} 反序列化入口（无 {@code @RequestBody
   * PageResult}，无 {@code readValue(..., PageResult.class)}），本类是纯出站响应模型。
   *
   * <p><b>本测试锁住现状</b>而非认可它：若将来给本类加了无参构造器或 {@code @JsonCreator}， 本测试会失败，提示「{@code list}
   * 别名开始真正生效，需重新评估是否要对入参侧做白名单」。
   *
   * @see #serializationOmitsDeprecatedAccessorKeys() 序列化侧的收敛契约
   */
  @Test
  void classIsNotDeserializableSoJsonAliasIsInert() throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    String legacyJson = "{\"list\":[\"a\"],\"total\":1,\"pageNum\":2,\"pageSize\":5}";

    assertThatThrownBy(() -> mapper.readValue(legacyJson, PageResult.class))
        .isInstanceOf(InvalidDefinitionException.class)
        .hasMessageContaining("no Creators");

    // @JsonAlias 注解本身确实挂在字段上——它只是没有机会被触发
    JsonAlias alias = PageResult.class.getDeclaredField("records").getAnnotation(JsonAlias.class);
    assertThat(alias).isNotNull();
    assertThat(alias.value()).containsExactly("list");
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
