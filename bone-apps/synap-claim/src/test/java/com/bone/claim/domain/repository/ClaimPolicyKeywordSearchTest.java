package com.bone.claim.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.claim.domain.model.policy.ClaimPolicy;
import com.bone.metadata.sdk.query.criteria.Criteria;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 保单列表关键字搜索的 SQL 结构回归测试。
 *
 * <p><b>为什么是这组用例</b>：前端搜索框 placeholder 承诺「保单号 / 名称」两维搜索，API 契约同样写明 「保单号/名称模糊匹配」，但实现此前只对 {@code
 * policy_no} 单列做 LIKE —— 按保单名称搜索恒返回 0 条。 这里是「注释宣称 X ≠ 代码做了 X」的契约违背，属真实缺陷而非文档瑕疵。
 *
 * <p><b>为什么断言 SQL 结构而不是 mock 返回值</b>：mock 仓储验不到 SQL 优先级——扁平 {@code or()} 链在后续还有 {@code and()} 时会退化为
 * {@code a OR (b AND c)}，「仅保单号命中、却不满足规则开关过滤」的行会从 OR 左支漏过过滤。 只有直接断言生成的 WHERE
 * 片段（条件组被括号整体包裹、过滤谓词留在组外），才能守住这个缺陷类。
 *
 * <p><b>变异判别力</b>：删掉 {@code policyListCriteria} 里的 {@code .like(ClaimPolicy::getPolicyName,
 * pattern)} 一行 → {@link #keyword_matchesBothColumns_insideOneGroup()} 与 {@link
 * #listAndCount_shareTheSameCriteria()} 同时转红；把条件组换成扁平 {@code or()} 链 → {@link
 * #configStatusFilter_staysOutsideTheOrGroup()} 转红。
 */
class ClaimPolicyKeywordSearchTest {

  /** 抓取 list 路径实际下发的 Criteria。 */
  private static Criteria<ClaimPolicy> captureListCriteria(String keyword, String configStatus) {
    ClaimPolicyRepository repo = mock(ClaimPolicyRepository.class, CALLS_REAL_METHODS);
    when(repo.findByCriteria(any())).thenReturn(List.of());
    repo.findPolicyPage(0L, keyword, configStatus, 1, 10);
    ArgumentCaptor<Criteria<ClaimPolicy>> captor = ArgumentCaptor.forClass(Criteria.class);
    verify(repo).findByCriteria(captor.capture());
    return captor.getValue();
  }

  /** 抓取 count 路径实际下发的 Criteria。 */
  private static Criteria<ClaimPolicy> captureCountCriteria(String keyword, String configStatus) {
    ClaimPolicyRepository repo = mock(ClaimPolicyRepository.class, CALLS_REAL_METHODS);
    when(repo.countByCriteria(any())).thenReturn(0L);
    repo.countPolicyPage(0L, keyword, configStatus);
    ArgumentCaptor<Criteria<ClaimPolicy>> captor = ArgumentCaptor.forClass(Criteria.class);
    verify(repo).countByCriteria(captor.capture());
    return captor.getValue();
  }

  /**
   * 取出 WHERE 片段里包裹 OR 条件的那个括号组。
   *
   * <p>注意不能简单地取「第一个 {@code (}」——LIKE 子句里含 {@code CONCAT('%%', ?, '%%')}，其左括号在组内。 这里先定位首个 LIKE
   * 谓词，再<b>向前</b>找组开括号，并做带嵌套的括号配对，保证取到的是 OR 组而非子表达式。
   */
  private static String orGroup(String whereSql) {
    int like = whereSql.indexOf("m.policy_no LIKE");
    assertThat(like).as("WHERE 应含保单号 LIKE 谓词: %s", whereSql).isGreaterThan(0);
    int open = whereSql.lastIndexOf('(', like);
    assertThat(open).as("OR 组应有开括号: %s", whereSql).isGreaterThanOrEqualTo(0);
    int depth = 0;
    for (int i = open; i < whereSql.length(); i++) {
      char ch = whereSql.charAt(i);
      if (ch == '(') {
        depth++;
      } else if (ch == ')') {
        depth--;
        if (depth == 0) {
          return whereSql.substring(open + 1, i);
        }
      }
    }
    throw new AssertionError("OR 组缺少闭合括号: " + whereSql);
  }

  @Test
  void keyword_matchesBothColumns_insideOneGroup() {
    String group = orGroup(captureListCriteria("智联", null).whereSql());

    assertThat(group)
        .as("关键字须匹配保单号、保单名称、保司侧保单号三列")
        .contains("policy_no LIKE")
        .contains("policy_name LIKE")
        .contains("insurer_policy_no LIKE")
        .contains("OR");
    // 组内恰好三个 LIKE：防止多/少匹配列时静默漂移
    assertThat(group.split("LIKE", -1)).as("条件组内应恰有三个 LIKE 谓词").hasSize(4);
  }

  @Test
  void configStatusFilter_staysOutsideTheOrGroup() {
    String where = captureListCriteria("智联", "ACTIVE").whereSql();

    // 优先级回归：过滤谓词必须留在 OR 组外，否则「仅保单号命中、状态不符」的行会漏过过滤
    assertThat(orGroup(where)).doesNotContain("config_status");
    assertThat(where).contains("config_status");
    assertThat(where).as("过滤谓词应与 OR 组以 AND 连接").containsPattern("\\).*AND.*config_status");
  }

  @Test
  void blankKeyword_addsNoLikeCondition() {
    String where = captureListCriteria("   ", null).whereSql();

    assertThat(where).doesNotContain("LIKE");
    assertThat(where).contains("tenant_id");
  }

  @Test
  void nullKeyword_addsNoLikeCondition() {
    assertThat(captureListCriteria(null, null).whereSql()).doesNotContain("LIKE");
  }

  @Test
  void listAndCount_shareTheSameCriteria() {
    // list 与 count 若各写一份条件，口径会漂移（曾因 find/count 条件不同导致总数与列表对不上）
    String listWhere = captureListCriteria("智联", "ACTIVE").whereSql();
    String countWhere = captureCountCriteria("智联", "ACTIVE").whereSql();

    assertThat(countWhere).isEqualTo(listWhere);
    assertThat(countWhere).contains("policy_no LIKE").contains("policy_name LIKE");
  }

  @Test
  void keywordPattern_isTrimmedAndWildcardWrapped() {
    Criteria<ClaimPolicy> criteria = captureListCriteria("  智联  ", null);

    assertThat(criteria.getParameters()).as("关键字应去空白后按 contains 语义包裹").containsValue("%智联%");
    // 只取两列 LIKE 参数（参数表里还有 tenant_id 等无关参数）
    List<Object> likeParams =
        criteria.getParameters().entrySet().stream()
            .filter((e) -> e.getKey().startsWith("policy_"))
            .map(java.util.Map.Entry::getValue)
            .toList();
    assertThat(likeParams).as("保单号与保单名称两列应共用同一匹配模式").containsExactly("%智联%", "%智联%");
  }
}
