package com.bone.iam.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.core.tenant.context.TenantContext;
import com.bone.iam.domain.model.dept.Dept;
import com.bone.iam.domain.repository.DeptRepository;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * <b>回归门禁</b>：锁定bone-metadata-sdk 的 {@code Repository#findById} 必须真正按主键过滤。
 *
 * <p><b>历史（2026-10-05 定位→ 当日复验已修复）</b>：真实链路上「建账号」对任何部门都报 {@code IAM_DEPT_NOT_FOUND}，而 {@code GET
 * /api/v1/iam/depts/tree}（列表路径）能正常列出同一批部门。 抓 SDK 的 DEBUG SQL 看到 {@code findById} 生成的语句是 {@code
 * WHERE m.deleted = :deleted_0 AND m.deleted = false AND m.tenant_id = :t}， {@code
 * params={deleted_0=<传入的部门id>}} —— <b>{@code m.id} 条件完全缺失，传入的主键值被绑到了软删占位符上</b>， 导致 {@code findById}
 * 永远返回 null。与软删/租户都无关（非 0 租户生成的 SQL 一模一样）。
 *
 * <p><b>当前状态（2026-10-06复验）</b>：SDK jar 重建后本类 5 条判据<b>全绿</b>， 实测 SQL 已恢复为 {@code WHERE m.id = :id_0
 * AND m.deleted = false AND m.tenant_id = :_sdk_tenant_id}。 缺陷已不复存在，故本类从「缺陷复现（默认
 * skip）」改为<b>常驻回归门禁</b> —— 它的价值从"证明有 bug"变成"<b>防止该 bug 再退化</b>"：一旦条件拼接逻辑回退，这里立刻红。
 *
 * <p><b>为什么这类测试必须有</b>：{@code findById} 是全仓 147 处存在性判定的公共依赖 （7 个模块），一旦退化表现为「资源不存在」类
 * 404/500，而数据其实好好地在库里， 列表路径却完全正常 —— 极易被误判成"租户口径不一致"或"数据没写进去"（本次就误判了 4 轮）。
 *
 * <p><b>造数据不能用</b> {@code Dept.create(...) + save(...)}：单测环境下 SDK 的 {@code ColumnAllocator} 未初始化 ⇒
 * {@code GenerationStrategy} 解析抛 {@code NullPointerException: Cannot invoke
 * "GenerationStrategy.ordinal()" because "strategy" is null}。 本类改用 {@code JdbcTemplate} 直插并显式给
 * ID（被测行为是"单查"，不是"ID 怎么生成"）。
 */
@SpringBootTest
@ActiveProfiles("test")
class DeptRepositoryPlatformTenantTest {

  private static final long PLATFORM_TENANT = 0L;
  private static final long BUSINESS_TENANT = 9001L;
  private static final String DEPT_NAME = "平台租户部门_" + System.nanoTime();

  /**
   * 绕开 SDK 的雪花 ID 生成（单测里 ColumnAllocator 未初始化 ⇒ GenerationStrategy 解析 NPE）， 直接用 JDBC
   * 造数据：被测行为是「单查」，不是「ID 怎么生成」。
   */
  @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

  @Autowired private DeptRepository deptRepository;

  private static final AtomicLong SEQ = new AtomicLong(770000000000000001L);

  @AfterEach
  void clearTenant() {
    TenantContext.clear();
  }

  private Long insertDept(long tenantId) {
    Long id = SEQ.incrementAndGet();
    jdbcTemplate.update(
        "INSERT INTO iam_dept (id, tenant_id, name, parent_id, order_no, status,"
            + " created_by, updated_by, created_at, updated_at, deleted, version)"
            + " VALUES (?, ?, ?, NULL, 1, 1, NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0)",
        id,
        tenantId,
        DEPT_NAME + "_" + tenantId);
    return id;
  }

  @Test
  @DisplayName("平台租户(0)能按主键单查到自己的部门 —— 曾经退化（m.id 条件缺失），防复发")
  void findByIdFindsDeptOfPlatformTenant() {
    Long deptId = insertDept(PLATFORM_TENANT);

    TenantContext.setTenantId(String.valueOf(PLATFORM_TENANT));
    Dept found = deptRepository.findById(deptId);

    assertThat(found)
        .as("平台租户(0) 单查部门 id=%s 应命中；为 null 说明 SDK 单查路径对 tenantId=0 有缺陷", deptId)
        .isNotNull();
    assertThat(found.getName()).isEqualTo(DEPT_NAME + "_0");
  }

  @Test
  @DisplayName("非 0 租户能按主键单查 —— 对照组：区分「租户口径」与「单查整体退化」")
  void findByIdFindsDeptOfNonZeroTenant() {
    Long deptId = insertDept(BUSINESS_TENANT);

    TenantContext.setTenantId(String.valueOf(BUSINESS_TENANT));
    Dept found = deptRepository.findById(deptId);

    assertThat(found).as("非 0 租户单查应命中").isNotNull();
  }

  @Test
  @DisplayName("跨租户单查必须查不到 —— 隔离线守卫，防止本测试给实现开口子")
  void findByIdFromOtherTenantFindsNothing() {
    Long deptId = insertDept(BUSINESS_TENANT);

    TenantContext.setTenantId(String.valueOf(PLATFORM_TENANT));
    Dept found = deptRepository.findById(deptId);

    assertThat(found).as("平台租户不应看到业务租户的部门").isNull();
  }

  @Test
  @DisplayName("查回的部门带tenantId —— 应用服务就比这个值（null ⇒ 误报 IAM_DEPT_NOT_FOUND）")
  void deptCarriesTenantIdAfterFindById() {
    Long platformDeptId = insertDept(PLATFORM_TENANT);

    TenantContext.setTenantId(String.valueOf(PLATFORM_TENANT));
    Dept found = deptRepository.findById(platformDeptId);

    assertThat(found).isNotNull();
    assertThat(found.getTenantId())
        .as(
            "Dept.getTenantId() 为 null ⇒ AccountApplicationService.assertDeptBelongsToTenant 的 "
                + "Objects.equals(null, 0L)=false ⇒ 误报 IAM_DEPT_NOT_FOUND（与「查不到行」是两种故障）")
        .isNotNull()
        .isEqualTo(PLATFORM_TENANT);
  }

  @Test
  @DisplayName("列表路径对照：平台租户能列出自己的部门（线上 tree 已通，此处固化该前提）")
  void listPathSeesPlatformTenantDepts() {
    insertDept(PLATFORM_TENANT);

    TenantContext.setTenantId(String.valueOf(PLATFORM_TENANT));
    List<Dept> all = deptRepository.listAll();

    assertThat(all).as("平台租户列表路径应能查到部门（线上 depts/tree 已通）").isNotEmpty();
  }
}
