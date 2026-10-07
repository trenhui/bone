package com.bone.blueprint.adapter.web.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.core.model.CursorPageParam;
import com.bone.core.model.PageParam;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link OrderPageRequest} 收口为「分页字段继承 {@link PageParam}」后的行为锁定。
 *
 * <p><b>为何必须有此测试</b>：本页把内联的 {@code page}/{@code size}（含 {@code @Max(100)} 上限） 换成继承自 {@code
 * PageParam}。Bean Validation 会校验<b>完整继承链</b>上的约束， 因此约束「应当」仍然生效——但这属于<b>推断</b>，一旦 Lombok
 * 生成方式或注解元数据变化， {@code @Max(100)} 可能静默失效 ⇒ {@code pageSize=Integer.MAX_VALUE} 直接打满数据库。
 * 本测试把它变成可回归的契约。
 */
class OrderPageRequestPagingTest {

  private static ValidatorFactory factory;
  private static Validator validator;

  @BeforeAll
  static void setUp() {
    factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
  }

  @AfterAll
  static void tearDown() {
    if (factory != null) {
      factory.close();
    }
  }

  @Test
  @DisplayName("分页字段来自 PageParam 基类（单一真源），子类不重复声明")
  void pagingFieldsComeFromBaseClass() {
    // 若哪天有人把 page/size 又搬回本类，本断言立刻红——那是漂移回归
    assertThat(PageParam.class.getDeclaredFields()).extracting("name").contains("page", "size");
    assertThat(OrderPageRequest.class.getDeclaredFields())
        .extracting("name")
        .contains("customerId", "status")
        .doesNotContain("page", "size");
  }

  @Test
  @DisplayName("继承链上的 @Min(1) 生效：page=0 被拒")
  void inheritedMinConstraintIsEnforced() {
    OrderPageRequest req = new OrderPageRequest();
    req.setPage(0);

    Set<ConstraintViolation<OrderPageRequest>> violations = validator.validate(req);

    assertThat(violations)
        .as("page=0 必须被基类 @Min(1) 拒绝——证明校验覆盖了继承链")
        .anyMatch(v -> v.getPropertyPath().toString().equals("page"));
  }

  @Test
  @DisplayName("继承链上的 @Max(100) 生效：size=101 被拒（防打满数据库）")
  void inheritedMaxConstraintIsEnforced() {
    OrderPageRequest req = new OrderPageRequest();
    req.setSize(101);

    Set<ConstraintViolation<OrderPageRequest>> violations = validator.validate(req);

    assertThat(violations)
        .as("size=101 必须被基类 @Max(100) 拒绝——上限已收口到 PageParam，此处证明未丢失")
        .anyMatch(v -> v.getPropertyPath().toString().equals("size"));
  }

  @Test
  @DisplayName("默认值来自 PageParam（page=1/size=10），且合法值不产生分页违规")
  void defaultsComeFromBaseClassAndAreValid() {
    OrderPageRequest req = new OrderPageRequest();

    // 未显式设置时由基类字段初始值提供
    assertThat(req.getPage()).isEqualTo(1);
    assertThat(req.getSize()).isEqualTo(10);
    assertThat(validator.validate(req)).as("默认值必须是合法值，否则每个未带分页参数的请求都会 400").isEmpty();
  }

  // ===== 分页入参的收窄（clamp）行为 =====
  //
  // ⚠️ 本组断言放在 blueprint 而非 bone-core：bone-core 只依赖 jakarta.validation-api
  // （provided，无实现），在那边调用 Validation.buildDefaultValidatorFactory() 会抛
  // NoProviderFoundException（2026-10-07 实测）。纯逻辑 clamp 断言见 bone-core 的
  // PagingParamClampTest。

  @Test
  @DisplayName("裸 @RequestParam 场景：of(...) 收窄，绕开 bean validation 也能挡住越界值")
  void ofClampsOutOfRangeValues() {
    // @Min/@Max 只在 @Valid @ModelAttribute 下生效；控制器用裸 @RequestParam 时注解不执行，
    // 收窄必须由工厂承担，否则 size=0 / size=MAX_VALUE 会直打数据库。
    assertThat(PageParam.of(0, null).getPage()).isEqualTo(1);
    assertThat(PageParam.of(-1, null).getPage()).isEqualTo(1);
    assertThat(PageParam.of(1, 0).getSize()).isEqualTo(1);
    assertThat(PageParam.of(1, 9999).getSize()).isEqualTo(PageParam.MAX_PAGE_SIZE);
  }

  @Test
  @DisplayName("游标入参收窄与 offset 同构（[1,100]），两范式上限各自独立定义")
  void cursorParamClampIsIndependentFromOffset() {
    assertThat(CursorPageParam.of(null, 0).getLimit()).isEqualTo(1);
    assertThat(CursorPageParam.of(null, 9999).getLimit()).isEqualTo(CursorPageParam.MAX_LIMIT);
    // 首页不带游标；末页 nextCursor 为 null ⇒ 翻页循环终止
    assertThat(CursorPageParam.firstPage(20).hasNext()).isFalse();
    assertThat(CursorPageParam.of("Y3Vyc29y", 20).hasNext()).isTrue();

    // 上限同值但故意不共用常量：改一个不应连带改另一个
    assertThat(CursorPageParam.MAX_LIMIT).isEqualTo(100);
    assertThat(PageParam.MAX_PAGE_SIZE).isEqualTo(100);
  }

  @Test
  @DisplayName("游标入参在 bean validation 下同样受 @Max 约束（双保险，非仅靠 clamp）")
  void cursorLimitValidatedByAnnotation() {
    CursorPageParam param = CursorPageParam.firstPage(10);
    param.setLimit(101);

    assertThat(validator.validate(param))
        .as("clamp 已挡住运行期路径；注解是第二道防线")
        .anyMatch(v -> v.getPropertyPath().toString().equals("limit"));
  }
}
