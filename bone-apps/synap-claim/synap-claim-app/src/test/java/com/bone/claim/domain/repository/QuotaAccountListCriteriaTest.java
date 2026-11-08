package com.bone.claim.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.claim.domain.model.quota.QuotaAccount;
import com.bone.metadata.sdk.query.criteria.Criteria;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 额度账户列表筛选的 SQL 结构回归测试。
 *
 * <p><b>为什么是这组用例</b>：配置面额度列表从「全量返回、前端过滤」改为后端分页+筛选后， 三处查询（分页 list / count / 导出）必须共用同一套 {@code
 * accountListCriteria}——曾有先例： list 与 count 各写一份条件导致总数与列表对不上，翻页出现重复/丢行。
 *
 * <p><b>为什么断言 SQL 结构而不是 mock 返回值</b>：mock 仓储验不到条件是否真的下发。 只有直接抓取实际下发的 {@link Criteria} 并断言 WHERE
 * 片段，才能守住「筛选列写错 / 条件被静默丢弃」这类缺陷。
 *
 * <p><b>变异判别力</b>：删掉 {@code accountListCriteria} 里的 {@code .like(...insuredIdHash...)} → {@link
 * #keyword_matchesInsuredIdHashLike()} 转红；删掉 {@code removed} 分支 → {@link
 * #removedFilter_mapsToInsuredState()} 转红；list/count 导出三者任一改写条件 → {@link
 * #listCountExport_shareTheSameCriteria()} 转红。
 */
class QuotaAccountListCriteriaTest {

  private static final Long TENANT = 9L;
  private static final String POLICY = "P123";

  /** 抓取分页 list 路径实际下发的 Criteria。 */
  private static Criteria<QuotaAccount> captureList(
      String keyword, String quotaType, Boolean removed) {
    QuotaAccountRepository repo = mock(QuotaAccountRepository.class, CALLS_REAL_METHODS);
    when(repo.findByCriteria(any())).thenReturn(List.of());
    repo.findAccountsPage(TENANT, POLICY, keyword, quotaType, removed, 1, 20);
    ArgumentCaptor<Criteria<QuotaAccount>> captor = ArgumentCaptor.forClass(Criteria.class);
    verify(repo).findByCriteria(captor.capture());
    return captor.getValue();
  }

  /** 抓取 count 路径实际下发的 Criteria。 */
  private static Criteria<QuotaAccount> captureCount(
      String keyword, String quotaType, Boolean removed) {
    QuotaAccountRepository repo = mock(QuotaAccountRepository.class, CALLS_REAL_METHODS);
    when(repo.countByCriteria(any())).thenReturn(0L);
    repo.countAccountsPage(TENANT, POLICY, keyword, quotaType, removed);
    ArgumentCaptor<Criteria<QuotaAccount>> captor = ArgumentCaptor.forClass(Criteria.class);
    verify(repo).countByCriteria(captor.capture());
    return captor.getValue();
  }

  /** 抓取导出路径实际下发的 Criteria。 */
  private static Criteria<QuotaAccount> captureExport(
      String keyword, String quotaType, Boolean removed) {
    QuotaAccountRepository repo = mock(QuotaAccountRepository.class, CALLS_REAL_METHODS);
    when(repo.findByCriteria(any())).thenReturn(List.of());
    repo.findAccountsFiltered(TENANT, POLICY, keyword, quotaType, removed);
    ArgumentCaptor<Criteria<QuotaAccount>> captor = ArgumentCaptor.forClass(Criteria.class);
    verify(repo).findByCriteria(captor.capture());
    return captor.getValue();
  }

  @Test
  void baseConditions_alwaysIncludeTenantAndPolicy() {
    String where = captureList(null, null, null).whereSql();

    assertThat(where).contains("tenant_id").contains("policy_no");
  }

  @Test
  void keyword_matchesInsuredIdHashLike() {
    String where = captureList("abc123", null, null).whereSql();

    assertThat(where).as("关键字应对被保人假名化 ID 做 LIKE 模糊匹配").contains("insured_id_hash LIKE");
    assertThat(captureList(" abc123 ", null, null).getParameters())
        .as("关键字应去空白后按 contains 语义包裹")
        .containsValue("%abc123%");
  }

  @Test
  void blankKeyword_addsNoLikeCondition() {
    assertThat(captureList("   ", null, null).whereSql()).doesNotContain("LIKE");
    assertThat(captureList(null, null, null).whereSql()).doesNotContain("LIKE");
  }

  @Test
  void quotaTypeFilter_appliesEqWhenPresent() {
    assertThat(captureList(null, "PERSONAL", null).whereSql()).contains("quota_type");
    assertThat(captureList(null, "  ", null).whereSql())
        .as("空白维度视为不过滤，不得生成 quota_type = null 条件")
        .doesNotContain("quota_type");
  }

  @Test
  void removedFilter_mapsToInsuredState() {
    // 减人状态落在 insured_state（0 正常 / 1 减人），按整型等值过滤
    assertThat(captureList(null, null, true).whereSql()).contains("insured_state");
    assertThat(captureList(null, null, true).getParameters()).as("减人 → 1").containsValue(1);
    assertThat(captureList(null, null, false).getParameters()).as("正常 → 0").containsValue(0);
    assertThat(captureList(null, null, null).whereSql())
        .as("removed=null 不得生成 insured_state 条件")
        .doesNotContain("insured_state");
  }

  @Test
  void listCountExport_shareTheSameCriteria() {
    // 三方条件若不一致：翻页总数对不上、导出与页面所见不符
    String listWhere = captureList("abc", "PERSONAL", true).whereSql();
    String countWhere = captureCount("abc", "PERSONAL", true).whereSql();
    String exportWhere = captureExport("abc", "PERSONAL", true).whereSql();

    assertThat(countWhere).isEqualTo(listWhere);
    assertThat(exportWhere).isEqualTo(listWhere);
  }

  @Test
  void export_hasNoPagingClause() {
    Criteria<QuotaAccount> export = captureExport("abc", null, null);

    // 导出要全量：不得带 LIMIT/OFFSET（分页参数只出现在 list 路径）
    assertThat(export.whereSql().toLowerCase()).doesNotContain("limit");
  }
}
