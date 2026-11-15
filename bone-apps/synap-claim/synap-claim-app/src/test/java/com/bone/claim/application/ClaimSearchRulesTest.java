package com.bone.claim.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 检索护栏的正负向钉子：通配转义、长度、危险字符、limit 上限。
class ClaimSearchRulesTest {

  @Test
  @DisplayName("合法保单号 / 中文姓名通过")
  void happyPath() {
    assertThat(ClaimSearchRules.validate("P202611001", 10)).isNull();
    assertThat(ClaimSearchRules.validate("张*三", 10)).isNull();
  }

  @Test
  @DisplayName("过短 / 过长 / 空关键词显式拒绝，不静默裁剪")
  void lengthGuard() {
    assertThat(ClaimSearchRules.validate("A", 10)).contains("长度");
    assertThat(ClaimSearchRules.validate("x".repeat(65), 10)).contains("长度");
    assertThat(ClaimSearchRules.validate("", 10)).contains("长度");
    assertThat(ClaimSearchRules.validate(null, 10)).contains("长度");
  }

  @Test
  @DisplayName("limit 越界拒绝，防全库拉取")
  void limitGuard() {
    assertThat(ClaimSearchRules.validate("P202611001", 0)).contains("limit");
    assertThat(ClaimSearchRules.validate("P202611001", 21)).contains("limit");
    assertThat(ClaimSearchRules.validate("P202611001", 20)).isNull();
  }

  @Test
  @DisplayName("引号 / 分号 / 尖括号 / 反斜杠 / 控制字符被拒")
  void dangerousChars() {
    for (String bad : new String[] {"a'b", "a\"b", "a;b", "a<b", "a\\b", "a\nb"}) {
      assertThat(ClaimSearchRules.validate(bad, 10)).as(bad).contains("不允许的字符");
    }
  }

  @Test
  @DisplayName("LIKE 通配符被转义为字面量，用户不能放大扫描面")
  void likeEscaping() {
    assertThat(ClaimSearchRules.toLikePattern("50%")).isEqualTo("%50!%%");
    assertThat(ClaimSearchRules.toLikePattern("A_B")).isEqualTo("%A!_B%");
    assertThat(ClaimSearchRules.toLikePattern("a!b")).isEqualTo("%a!!b%");
    assertThat(ClaimSearchRules.toLikePattern("  P2026  ")).isEqualTo("%P2026%");
  }

  @Test
  @DisplayName("纯通配符关键词即使放行也不会变成全表匹配（已转义）")
  void pureWildcardIsLiteral() {
    assertThat(ClaimSearchRules.toLikePattern("%%")).isEqualTo("%!%!%%");
  }
}
