package com.bone.claim.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.claim.domain.model.config.ClaimFlowConfig;
import com.bone.metadata.sdk.query.criteria.Criteria;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 流程配置「生效版本」语义的回归测试（{@code t_claim_flow_config}）。
 *
 * <p><b>为什么是这组用例</b>：0099 建表时给的唯一键是 {@code uk_tenant_flow_version (tenant_id, flow_key,
 * config_version)}——它约束的是<b>版本</b>，不是<b>生效行</b>。同一 {@code (tenant_id, flow_key)} 因而可以合法地存在多行（历史版本
 * {@code active_flag=0}、当前版本 {@code active_flag=1}）。 原仓储用 {@code findOneByCriteria} 只按 {@code
 * (tenant_id, flow_key)} 过滤、<b>不过滤 active_flag</b>，命中多行时 SDK 直接抛 {@code MultipleResultsException}：
 *
 * <pre>{@code
 * // (tenant_id=7, flow_key='ADJ') 有 v1(act=0) 与 v2(act=1) 两行
 * findOneByCriteria(eq(tenantId).eq(flowKey))  // ⇒ MultipleResultsException("Expected single result, found: 2")
 * }</pre>
 *
 * <p>这个缺陷在<b>接线那一刻</b>才暴露（当前无消费方、活库该表为空），且报错文本完全看不出成因，排查成本极高。
 *
 * <p><b>变异判别力</b>：删掉 {@code .eq(getActiveFlag, 1)} → {@link #query_filtersActiveFlag()} 转红；删掉
 * {@code .orderByDesc(getConfigVersion)} → {@link #query_sortsByConfigVersionDesc()} 转红；改回 {@code
 * findOneByCriteria} → {@link #query_usesListLookup_soMultipleRowsDoNotThrow()} 转红。
 */
class ClaimFlowConfigRepositoryTest {

  private static final Long TENANT = 7L;
  private static final String FLOW = "ADJUSTMENT_ORDER";

  /** 造一行配置（只需 id / config_version 用于断言）。 */
  private static ClaimFlowConfig config(Long id, Integer configVersion, Integer activeFlag) {
    ClaimFlowConfig config = mock(ClaimFlowConfig.class);
    // 注意：stubbing 必须在 mock 创建之后、且不得在 when(...) 内部再调 mock 方法
    // （Mockito 会报 UnfinishedStubbing）。
    when(config.getId()).thenReturn(id);
    when(config.getConfigVersion()).thenReturn(configVersion);
    when(config.getActiveFlag()).thenReturn(activeFlag);
    return config;
  }

  /** 抓取 {@code findByFlowKey} 实际下发的 Criteria。 */
  private static Criteria<ClaimFlowConfig> captureFindByFlowKey() {
    ClaimFlowConfigRepository repo = mock(ClaimFlowConfigRepository.class, CALLS_REAL_METHODS);
    when(repo.findByCriteria(any())).thenReturn(List.of());
    repo.findByFlowKey(TENANT, FLOW);
    ArgumentCaptor<Criteria<ClaimFlowConfig>> captor = ArgumentCaptor.forClass(Criteria.class);
    verify(repo).findByCriteria(captor.capture());
    return captor.getValue();
  }

  @Test
  @DisplayName("查询必须过滤 active_flag=1（历史版本不得被当作当前配置）")
  void query_filtersActiveFlag() {
    Criteria<ClaimFlowConfig> criteria = captureFindByFlowKey();

    assertThat(criteria.whereSql()).contains("active_flag");
    assertThat(criteria.getParameters()).as("生效标记 → 1").containsValue(1);
  }

  @Test
  @DisplayName("查询必须按 config_version 降序（多 active 时取发版号最大者，结果确定）")
  void query_sortsByConfigVersionDesc() {
    Criteria<ClaimFlowConfig> criteria = captureFindByFlowKey();

    assertThat(criteria.toSql().toLowerCase())
        .as("排序子句缺失 ⇒ 库里意外存在多 active 时，返回哪条随数据库返回顺序漂移")
        .contains("order by")
        .contains("config_version desc");
  }

  @Test
  @DisplayName("必须用 findByCriteria 而非 findOneByCriteria（多行命中不得抛 MultipleResultsException）")
  void query_usesListLookup_soMultipleRowsDoNotThrow() {
    ClaimFlowConfigRepository repo = mock(ClaimFlowConfigRepository.class, CALLS_REAL_METHODS);
    // 库里意外存在两条 active（数据被直连改坏 / 并发写 / 未来迁移失误）
    // 注意：实体必须先造好再 stub —— 在 when(...) 参数里调 mock 方法会触发 UnfinishedStubbing。
    List<ClaimFlowConfig> rows = List.of(config(1L, 3, 1), config(2L, 7, 1));
    when(repo.findByCriteria(any())).thenReturn(rows);

    ClaimFlowConfig found = repo.findByFlowKey(TENANT, FLOW);

    assertThat(found).as("取发版号最大的那条").isNotNull();
    assertThat(found.getConfigVersion()).isEqualTo(7);
    verify(repo, never()).findOneByCriteria(any());
  }

  @Test
  @DisplayName("「取最大版本」是应用层显式计算，不依赖数据库返回顺序（乱序返回也须取最大）")
  void highestVersion_computedInApplication_notByRowOrder() {
    ClaimFlowConfigRepository repo = mock(ClaimFlowConfigRepository.class, CALLS_REAL_METHODS);
    // 刻意用**乱序**返回：若实现只是取 rows.get(0)，这里就会取到 3 而非 7。
    List<ClaimFlowConfig> rows = List.of(config(1L, 3, 1), config(2L, 7, 1), config(3L, 5, 1));
    when(repo.findByCriteria(any())).thenReturn(rows);

    assertThat(repo.findByFlowKey(TENANT, FLOW).getConfigVersion()).isEqualTo(7);
  }

  @Test
  @DisplayName("版本号可空时不得把 null 当 0 参与比较（否则会选错行）")
  void highestVersion_nullVersion_doesNotWinByDefault() {
    ClaimFlowConfigRepository repo = mock(ClaimFlowConfigRepository.class, CALLS_REAL_METHODS);
    List<ClaimFlowConfig> rows = List.of(config(1L, null, 1), config(2L, 7, 1));
    when(repo.findByCriteria(any())).thenReturn(rows);

    assertThat(repo.findByFlowKey(TENANT, FLOW).getConfigVersion()).isEqualTo(7);
  }

  @Test
  @DisplayName("无生效版本时返回 null（调用方须按「未配置」处理，不得默认放行）")
  void noActiveRow_returnsNull() {
    ClaimFlowConfigRepository repo = mock(ClaimFlowConfigRepository.class, CALLS_REAL_METHODS);
    when(repo.findByCriteria(any())).thenReturn(List.of());

    assertThat(repo.findByFlowKey(TENANT, FLOW)).isNull();
  }

  @Test
  @DisplayName("入参缺失时不查库（null 租户 / 空 flow_key 一律返回 null）")
  void blankInput_doesNotHitDb() {
    ClaimFlowConfigRepository repo = mock(ClaimFlowConfigRepository.class, CALLS_REAL_METHODS);

    assertThat(repo.findByFlowKey(null, FLOW)).isNull();
    assertThat(repo.findByFlowKey(TENANT, null)).isNull();
    assertThat(repo.findByFlowKey(TENANT, "  ")).isNull();
    verify(repo, never()).findByCriteria(any());
  }

  @Test
  @DisplayName("按发版号查历史版本走 findOneByCriteria（该组合由唯一键保证唯一）")
  void findByVersion_usesUniqueKeyLookup() {
    ClaimFlowConfigRepository repo = mock(ClaimFlowConfigRepository.class, CALLS_REAL_METHODS);
    ClaimFlowConfig historical = config(9L, 2, 0);
    when(repo.findOneByCriteria(any())).thenReturn(historical);

    ClaimFlowConfig found = repo.findByVersion(TENANT, FLOW, 2);

    assertThat(found).isNotNull();
    assertThat(found.getConfigVersion()).isEqualTo(2);
    verify(repo).findOneByCriteria(any());
    verify(repo, never()).findByCriteria(any());
  }
}
