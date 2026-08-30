package com.bone.core.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.core.enums.Operator;
import org.junit.jupiter.api.Test;

/**
 * 查询 / 分页 / 排序模型测试。
 *
 * <p>这三个模型是 SDK 通用查询的入参契约（{@code query(QueryParam)}、{@code queryPage(PageParam)}），
 * 默认值一旦变化会静默放大查询范围（如 EQ 变 LIKE、size 变大），故固化默认值语义。
 */
class QueryModelTest {

  // ===== QueryParam =====

  /** 默认操作符必须是 EQ：若默认变更会导致查询范围被静默放大。 */
  @Test
  void queryParam_defaultsToEqualOperator() {
    QueryParam param = new QueryParam();

    assertThat(param.getType()).isEqualTo(Operator.EQ);
    assertThat(param.getField()).isNull();
    assertThat(param.getValue()).isNull();
  }

  @Test
  void queryParam_twoArgConstructorUsesEq() {
    QueryParam param = new QueryParam("name", "bone");

    assertThat(param.getField()).isEqualTo("name");
    assertThat(param.getValue()).isEqualTo("bone");
    assertThat(param.getType()).isEqualTo(Operator.EQ);
  }

  @Test
  void queryParam_threeArgConstructorSetsOperator() {
    QueryParam param = new QueryParam("name", "%bone%", Operator.LIKE);

    assertThat(param.getType()).isEqualTo(Operator.LIKE);
  }

  @Test
  void queryParam_equalityAndToString() {
    QueryParam a = new QueryParam("name", "bone", Operator.EQ);
    QueryParam b = new QueryParam("name", "bone", Operator.EQ);
    QueryParam c = new QueryParam("name", "bone", Operator.NE);

    assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    assertThat(a).isNotEqualTo(c);
    assertThat(a.toString()).contains("name").contains("EQ");
  }

  // ===== SortingField =====

  @Test
  void sortingField_defaultsToAscending() {
    SortingField field = new SortingField();

    assertThat(field.getOrder()).isEqualTo(SortingField.ORDER_ASC).isEqualTo("asc");
    assertThat(SortingField.ORDER_DESC).isEqualTo("desc");
  }

  @Test
  void sortingField_allArgsConstructor() {
    SortingField field = new SortingField("createdAt", SortingField.ORDER_DESC);

    assertThat(field.getField()).isEqualTo("createdAt");
    assertThat(field.getOrder()).isEqualTo("desc");
  }

  @Test
  void sortingField_equalityAndToString() {
    SortingField a = new SortingField("id", "asc");
    SortingField b = new SortingField("id", "asc");
    SortingField c = new SortingField("id", "desc");

    assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    assertThat(a).isNotEqualTo(c);
    assertThat(a.toString()).contains("id");
  }

  // ===== PageParam =====

  @Test
  void pageParam_defaultsToOneAndTen() {
    PageParam param = new PageParam();

    assertThat(param.getPage()).isEqualTo(1);
    assertThat(param.getSize()).isEqualTo(10);
  }

  @Test
  void pageParam_ofAppliesNonNullValues() {
    PageParam param = PageParam.of(3, 50);

    assertThat(param.getPage()).isEqualTo(3);
    assertThat(param.getSize()).isEqualTo(50);
  }

  /** null 表示「不覆盖」，保留默认值——便于调用方透传可空参数。 */
  @Test
  void pageParam_ofKeepsDefaultsWhenNullPassed() {
    PageParam param = PageParam.of(null, null);

    assertThat(param.getPage()).isEqualTo(1);
    assertThat(param.getSize()).isEqualTo(10);
    assertThat(PageParam.of()).isEqualTo(param);
  }

  @Test
  void pageParam_equalityAndToString() {
    PageParam a = PageParam.of(2, 20);
    PageParam b = PageParam.of(2, 20);
    PageParam c = PageParam.of(2, 30);

    assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    assertThat(a).isNotEqualTo(c);
    assertThat(a.toString()).contains("20");
  }
}
