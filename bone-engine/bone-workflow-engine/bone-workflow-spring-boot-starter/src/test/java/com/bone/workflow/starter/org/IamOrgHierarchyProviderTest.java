package com.bone.workflow.starter.org;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link IamOrgHierarchyProvider} 的语义契约：缓存、向上 N 级、环保护、失败关闭。
 *
 * <p><b>为什么这些断言值得写</b>：这一层是「审批会不会审到错的人」的最后一道闸。它返错的代价不是报错，而是
 * <b>静默把单据路由给不该看的人</b>——所以每条失败路径都断言返回空（引擎据此挂起），而不是断言「抛异常」或 「凑合返回一个人」。
 */
class IamOrgHierarchyProviderTest {

  @Test
  @DisplayName("直属主管 / 部门负责人分别取自各自字段，不互相顶替")
  void resolvesManagerAndDeptHeadIndependently() {
    IamOrgHierarchyProvider provider =
        providerOf(id -> Optional.of(hierarchy("9104", "9100", "9200")));

    assertThat(provider.lineManagerOf("9104")).contains("9100");
    assertThat(provider.deptHeadOf("9104")).contains("9200");
  }

  @Test
  @DisplayName("没有主管 / 没有部门负责人时返回空（引擎据此挂起，不是放行）")
  void absenceIsEmptyNotFallback() {
    IamOrgHierarchyProvider provider =
        providerOf(id -> Optional.of(new OrgHierarchy(id, "9200", null, "  ")));

    assertThat(provider.lineManagerOf("9104")).isEmpty();
    assertThat(provider.deptHeadOf("9104")).isEmpty();
  }

  @Test
  @DisplayName("查不到该账户返回空")
  void unknownAccountIsEmpty() {
    IamOrgHierarchyProvider provider = providerOf(id -> Optional.empty());

    assertThat(provider.lineManagerOf("404")).isEmpty();
  }

  @Test
  @DisplayName("IAM 抛异常时收敛成空并继续，不把异常穿透到发起流程")
  void queryFailureIsFailClosed() {
    IamOrgHierarchyProvider provider =
        providerOf(
            id -> {
              throw new IllegalStateException("IAM down");
            });

    assertThat(provider.lineManagerOf("9104")).isEmpty();
    assertThat(provider.deptHeadOf("9104")).isEmpty();
  }

  @Test
  @DisplayName("向上 N 级逐级解析（level=1 等同直属主管）")
  void levelUpWalksChain() {
    // 9104 的主管是 9100，9100 的主管是 9000，9000 是顶层
    IamOrgHierarchyProvider provider =
        providerOf(
            id ->
                switch (id) {
                  case "9104" -> Optional.of(new OrgHierarchy(id, "9200", "9100", null));
                  case "9100" -> Optional.of(new OrgHierarchy(id, "9200", "9000", null));
                  case "9000" -> Optional.of(new OrgHierarchy(id, "9200", null, null));
                  default -> Optional.empty();
                });

    assertThat(provider.levelUpOf("9104", 1)).contains("9100");
    assertThat(provider.levelUpOf("9104", 2)).contains("9000");
    // 9000 没有主管：第 3 级必须落空，而不是把 9000 自己当成答案
    assertThat(provider.levelUpOf("9104", 3)).isEmpty();
  }

  @Test
  @DisplayName("汇报链成环时中止并返回空，绝不返回环上的某个人")
  void cyclicChainIsRejected() {
    // A→B→A 的脏数据：没有 visited 保护时 level=4 会「成功」返回 A 或 B
    IamOrgHierarchyProvider provider =
        providerOf(
            id ->
                switch (id) {
                  case "A" -> Optional.of(new OrgHierarchy(id, null, "B", null));
                  case "B" -> Optional.of(new OrgHierarchy(id, null, "A", null));
                  default -> Optional.empty();
                });

    assertThat(provider.levelUpOf("A", 2)).isEmpty();
    assertThat(provider.levelUpOf("A", 3)).isEmpty();
  }

  @Test
  @DisplayName("超出级数上限按失败关闭，不静默截断到上限级")
  void levelBeyondLimitIsRejected() {
    AtomicInteger calls = new AtomicInteger();
    IamOrgHierarchyProvider provider =
        new IamOrgHierarchyProvider(
            id -> {
              calls.incrementAndGet();
              return Optional.of(new OrgHierarchy(id, null, "up-" + id, null));
            },
            0,
            3);

    assertThat(provider.levelUpOf("9104", 4)).isEmpty();
    assertThat(calls.get()).as("超限应立即拒绝，不应发起任何查询").isZero();
  }

  @Test
  @DisplayName("TTL 内命中缓存，不重复打 IAM")
  void cacheHitsWithinTtl() {
    AtomicInteger calls = new AtomicInteger();
    IamOrgHierarchyProvider provider =
        new IamOrgHierarchyProvider(
            id -> {
              calls.incrementAndGet();
              return Optional.of(new OrgHierarchy(id, "9200", "9100", "9200"));
            },
            30,
            10);

    provider.lineManagerOf("9104");
    provider.deptHeadOf("9104");
    provider.lineManagerOf("9104");

    assertThat(calls.get()).as("同一账户三次读取只应查一次").isEqualTo(1);
  }

  @Test
  @DisplayName("查询失败不写缓存：IAM 抖一下不能让该账户在整个 TTL 内都解析不出人")
  void failureIsNotCached() {
    AtomicInteger calls = new AtomicInteger();
    IamOrgHierarchyProvider provider =
        new IamOrgHierarchyProvider(
            id -> {
              if (calls.incrementAndGet() == 1) {
                throw new IllegalStateException("IAM 抖动");
              }
              return Optional.of(new OrgHierarchy(id, "9200", "9100", null));
            },
            30,
            10);

    assertThat(provider.lineManagerOf("9104")).isEmpty();
    assertThat(provider.lineManagerOf("9104")).contains("9100");
  }

  @Test
  @DisplayName("cacheTtlSeconds=0 时每次都实时查（改完主管立刻生效）")
  void zeroTtlAlwaysRefreshes() {
    AtomicInteger calls = new AtomicInteger();
    IamOrgHierarchyProvider provider =
        new IamOrgHierarchyProvider(
            id -> {
              calls.incrementAndGet();
              return Optional.of(new OrgHierarchy(id, "9200", "9100", null));
            },
            0,
            10);

    provider.lineManagerOf("9104");
    provider.lineManagerOf("9104");

    assertThat(calls.get()).isEqualTo(2);
  }

  private static OrgHierarchy hierarchy(String accountId, String managerId, String deptHeadId) {
    return new OrgHierarchy(accountId, "9200", managerId, deptHeadId);
  }

  private static IamOrgHierarchyProvider providerOf(OrgHierarchyQuery query) {
    return new IamOrgHierarchyProvider(query, 30, 10);
  }
}
