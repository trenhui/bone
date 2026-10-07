package com.bone.core.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link CursorPageParam} / {@link PageParam} 的 <b>纯逻辑</b>收窄（clamp）行为锁定。
 *
 * <p><b>为何只测纯逻辑、不用 jakarta.validation</b>：{@code bone-core} 只依赖 {@code
 * jakarta.validation-api}（{@code provided}，<b>无实现</b>）⇒ 在本模块调用 {@code
 * Validation.buildDefaultValidatorFactory()} 会抛 {@code NoProviderFoundException: no Jakarta Bean
 * Validation provider could be found}。 实测踩过（2026-10-07）。注解是否生效的验证放在有 provider 的模块 （见 blueprint 的
 * {@code OrderPageRequestPagingTest}）。
 *
 * <p><b>为何 clamp 必须锁</b>：两个真源的 {@code @Min/@Max} 只在 bean validation 生效， 而实测 {@code
 * ExtensionManagementController} 的两个日志端点用<b>裸 {@code @RequestParam}</b> ⇒ 注解不执行，此前靠各端点手写 {@code
 * Math.min/Math.max} 兜（同一逻辑复制了两份）。 收窄下沉到 {@code of(...)} 后，一旦有人改成「直接 set 不 clamp」， 就会静默放开 {@code
 * limit=0} 与 {@code limit=Integer.MAX_VALUE}。
 */
class PagingParamClampTest {

  // ===== CursorPageParam =====

  @Test
  @DisplayName("cursor 入参收窄：limit=0/-5/9999 都被夹到 [1,100]")
  void cursorLimitIsClamped() {
    assertThat(CursorPageParam.of(null, 0).getLimit()).isEqualTo(1);
    assertThat(CursorPageParam.of(null, -5).getLimit()).isEqualTo(1);
    assertThat(CursorPageParam.of(null, 9999).getLimit())
        .as("必须夹到 MAX_LIMIT，否则整表拉进内存")
        .isEqualTo(CursorPageParam.MAX_LIMIT);
    assertThat(CursorPageParam.of(null, 30).getLimit()).isEqualTo(30);
    assertThat(CursorPageParam.clampLimit(null)).isEqualTo(CursorPageParam.DEFAULT_LIMIT);
  }

  @Test
  @DisplayName("cursor 为 null/空白视为首页，且非空白会被 trim（不把空白串当游标传下去）")
  void cursorBlankMeansFirstPage() {
    assertThat(CursorPageParam.of(null, 10).getCursor()).isNull();
    assertThat(CursorPageParam.of("   ", 10).getCursor()).isNull();
    assertThat(CursorPageParam.of("abc", 10).getCursor()).isEqualTo("abc");
    assertThat(CursorPageParam.firstPage(20).getCursor()).isNull();
    assertThat(CursorPageParam.firstPage(20).getLimit()).isEqualTo(20);
  }

  @Test
  @DisplayName("hasNext 是翻页循环终止条件，与 PageResult.nextCursor 语义配套")
  void hasNextDrivesLoopTermination() {
    assertThat(CursorPageParam.firstPage(10).hasNext()).isFalse();
    assertThat(CursorPageParam.of("Y3Vyc29yOjEyMw", 10).hasNext()).isTrue();
    assertThat(CursorPageParam.of("  ", 10).hasNext()).isFalse();
  }

  // ===== PageParam（同一收窄语义，本次一并加固）=====

  @Test
  @DisplayName("offset 入参收窄：page=0/-3、size=0/9999 都被夹住（此前 of() 不收窄，是真实漏洞）")
  void pageParamIsClamped() {
    assertThat(PageParam.of(0, null).getPage()).isEqualTo(1);
    assertThat(PageParam.of(-3, null).getPage()).isEqualTo(1);
    assertThat(PageParam.of(1, 0).getSize()).isEqualTo(1);
    assertThat(PageParam.of(1, 9999).getSize()).isEqualTo(PageParam.MAX_PAGE_SIZE);
    assertThat(PageParam.of(5, 25).getPage()).isEqualTo(5);
    assertThat(PageParam.of(5, 25).getSize()).isEqualTo(25);
  }

  @Test
  @DisplayName("两种范式上限同值 100 但各自独立定义——改一个不应连带改另一个")
  void twoFamiliesHaveIndependentBounds() {
    // 上限同值但故意不共用常量：改一个不应连带改另一个
    assertThat(PageParam.MAX_PAGE_SIZE).isEqualTo(100);
    assertThat(CursorPageParam.MAX_LIMIT).isEqualTo(100);
    // 默认值：null 入参时各自回落到自己的默认（不跨范式借用）
    assertThat(PageParam.of(1, null).getSize()).isEqualTo(new PageParam().getSize());
    assertThat(CursorPageParam.clampLimit(null)).isEqualTo(CursorPageParam.DEFAULT_LIMIT);
  }

  @Test
  @DisplayName("游标出参 PageResult.cursorOf 与入参语义配套：total/page/pages 置 null、hasNext 随 nextCursor")
  void cursorOutParamMatchesInParam() {
    PageResult<String> withNext = PageResult.cursorOf(java.util.List.of("a"), "Y3Vyc29y", 10);
    assertThat(withNext.getNextCursor()).isEqualTo("Y3Vyc29y");
    assertThat(withNext.getHasNext()).isTrue();
    assertThat(withNext.getTotal()).isNull();
    assertThat(withNext.getPage()).isNull();

    // 末页：nextCursor 为 null ⇒ 翻页终止（对应入参 hasNext()==false）
    PageResult<String> last = PageResult.cursorOf(java.util.List.of("a"), null, 10);
    assertThat(last.getHasNext()).isFalse();
    assertThat(CursorPageParam.of(last.getNextCursor(), 10).hasNext())
        .as("出参无 nextCursor ⇒ 下次请求不带cursor ⇒ 入参判定无下一页，循环终止")
        .isFalse();
  }
}
