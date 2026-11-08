package com.bone.claim.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.claim.domain.model.decision.AiDecisionDisclosure.Involvement;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 受控码 → 中文标签的覆盖护栏。
 *
 * <p>字典分类是<b>遍历枚举</b>生成的（{@code ClaimDictApplicationService#aiInvolvementItems}， 标签查 {@code
 * ClaimDictLabels}）：新增一个参与态却忘了配中文，接口照样返回 200，只是 {@code label} 为 {@code
 * null}，前端一路降级到显示裸码。这类缺口没有异常、没有日志，只能靠用例钉住。
 *
 * <p>双向断言：既拦「枚举有、标签没有」，也拦「标签有、枚举已删」——后者会让 前端字典多出一个永远不会出现的码，同样属漂移。
 */
class ClaimDictLabelsTest {

  @Test
  @DisplayName("AI 参与每一态都必须有中文标签，且标签表不得残留已删除的态")
  void everyInvolvementHasLabel() {
    for (Involvement v : Involvement.values()) {
      assertThat(ClaimDictLabels.AI_INVOLVEMENT)
          .as("参与态 %s 未在 ClaimDictLabels.AI_INVOLVEMENT 登记标签", v)
          .containsKey(v.name());
      assertThat(ClaimDictLabels.AI_INVOLVEMENT.get(v.name())).as("参与态 %s 的标签为空", v).isNotBlank();
    }

    assertThat(ClaimDictLabels.AI_INVOLVEMENT)
        .as("标签表存在枚举里已不存在的码")
        .hasSameSizeAs(Involvement.values());
  }
}
