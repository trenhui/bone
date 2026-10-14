package com.bone.metadata.catalog.application;

import com.bone.core.model.PageResult;
import com.bone.metadata.catalog.application.query.dto.MetaEntityDTO;
import com.bone.metadata.catalog.application.query.dto.ModelingWorkspaceDTO;
import com.bone.metadata.catalog.application.query.mapper.CatalogDtoMapper;
import com.bone.metadata.catalog.domain.gateway.TenantProvider;
import com.bone.metadata.catalog.domain.model.iam.IamApplicationRef;
import com.bone.metadata.catalog.domain.model.iam.IamModuleRef;
import com.bone.metadata.catalog.domain.model.meta.MetaEntity;
import com.bone.metadata.catalog.domain.model.meta.MetaEntityStatus;
import com.bone.metadata.catalog.domain.repository.IamApplicationRepository;
import com.bone.metadata.catalog.domain.repository.IamModuleRepository;
import com.bone.metadata.catalog.domain.repository.MetaEntityRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 建模工作台（模型设计页）聚合应用服务（P0-1）。
 *
 * <p>入站边界语义化（ADR-0028）：Controller 只依赖本类。读侧一律经 domain.repository 的读模型方法 （pageEntities /
 * listByTenant），不持有 Criteria / FluentQuery（CORE-05 readSideDslOnlyInQueryLayer）。
 *
 * <p>统计口径与「业务对象」页严格同源：三项计数均走 {@code pageEntities(size=1)} 读 total， 不另写 COUNT SQL——避免「工作台数字」与「列表
 * total」两套口径漂移（此前前端 sum(app.entityCount) 恒为 0 的事故正是口径分裂的教训）。
 */
@Service
@RequiredArgsConstructor
public class ModelingWorkspaceApplicationService {

  /** 待发布草稿取前 6 条（待办提示，非清单；完整清单真源在业务对象页）。 */
  private static final int RECENT_DRAFTS_SIZE = 6;

  private final MetaEntityRepository metaEntityRepository;
  private final IamApplicationRepository iamApplicationRepository;
  private final IamModuleRepository iamModuleRepository;
  private final TenantProvider tenantProvider;

  /**
   * 加载建模工作台聚合数据。
   *
   * <p>无写操作、无跨聚合变更，只读事务。异常不兜底：统计失败应让页面走降级路径（前端 legacy 取数）， 而不是返回一份 看起来正常实则缺项的聚合——静默缺项比显式失败更难排查。
   */
  @Transactional(readOnly = true)
  public ModelingWorkspaceDTO loadWorkspace() {
    long tenantId = tenantProvider.currentTenantId();

    long entityTotal = totalOf(tenantId, null);
    long draftTotal = totalOf(tenantId, MetaEntityStatus.DRAFT.getCode());
    long publishedTotal = totalOf(tenantId, MetaEntityStatus.PUBLISHED.getCode());

    PageResult<MetaEntity> recentPage =
        metaEntityRepository.pageEntities(
            tenantId,
            null,
            MetaEntityStatus.DRAFT.getCode(),
            null,
            null,
            null,
            1,
            RECENT_DRAFTS_SIZE);
    List<MetaEntityDTO> recentDrafts =
        recentPage.getRecords().stream().map(CatalogDtoMapper::toDto).toList();

    // 应用→模块两级结构：应用一次查全（租户过滤），模块一次查全后按 appId 分组。
    // 不做「每应用一次模块查询」——那正是本端点要消除的 N+1。
    List<IamApplicationRef> apps = iamApplicationRepository.listByTenant(tenantId);
    List<IamModuleRef> modules = iamModuleRepository.listByTenant(tenantId);
    Map<Long, List<IamModuleRef>> modulesByApp =
        modules.stream().collect(Collectors.groupingBy(IamModuleRef::getAppId));
    List<ModelingWorkspaceDTO.AppModulesDTO> appModules =
        apps.stream()
            .map(
                app ->
                    new ModelingWorkspaceDTO.AppModulesDTO(
                        app.getId(),
                        app.getName(),
                        modulesByApp.getOrDefault(app.getId(), List.of()).stream()
                            .map(
                                m ->
                                    new ModelingWorkspaceDTO.ModuleItemDTO(
                                        m.getId(), m.getName(), m.getCode()))
                            .toList()))
            .toList();

    return new ModelingWorkspaceDTO(
        entityTotal, draftTotal, publishedTotal, recentDrafts, appModules);
  }

  /**
   * 与业务对象页同口径的计数：pageEntities(size=1) 读 total（tenant + deleted 过滤内建于读模型方法）。
   *
   * <p>type / deliveryMode 固定传 null：工作台统计的是租户内全量口径，不套用列表页的两个可选筛选维度（否则 「工作台数字」会随列表筛选条件漂移）。
   */
  private long totalOf(long tenantId, Integer status) {
    PageResult<MetaEntity> page =
        metaEntityRepository.pageEntities(tenantId, null, status, null, null, null, 1, 1);
    Long total = page.getTotal();
    return total == null ? 0L : total;
  }
}
