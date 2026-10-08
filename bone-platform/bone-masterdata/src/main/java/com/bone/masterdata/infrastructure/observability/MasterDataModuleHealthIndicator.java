package com.bone.masterdata.infrastructure.observability;

import com.bone.masterdata.domain.model.category.RecordCategoryLink;
import com.bone.masterdata.domain.repository.RecordCategoryLinkRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * MasterData 模块健康检查。
 *
 * <p>真检查内容：
 *
 * <ul>
 *   <li>轻量 DB 可达探测：对 masterdata 关联表执行一次跨租户 count（disableTenantFilter）， 验证 metadata-sdk
 *       读路径与数据库连通性。探测本身极低开销（COUNT 走索引）， 异常时整体健康度降为 DOWN。
 * </ul>
 *
 * <p>后续可扩展：主数据实体总数、质量检查积压计数。
 */
@Component
public class MasterDataModuleHealthIndicator implements HealthIndicator {

  private final RecordCategoryLinkRepository linkRepository;

  public MasterDataModuleHealthIndicator(RecordCategoryLinkRepository linkRepository) {
    this.linkRepository = linkRepository;
  }

  @Override
  public Health health() {
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("module", "bone-masterdata");

    long linkCount;
    try {
      linkCount =
          linkRepository.countByCriteria(
              com.bone.metadata.sdk.query.criteria.Criteria.<RecordCategoryLink>create()
                  .disableTenantFilter());
    } catch (Exception ex) {
      details.put("db-error", ex.getMessage());
      return Health.down().withDetails(details).build();
    }
    details.put("record_category_link-total", linkCount);

    return Health.up().withDetails(details).build();
  }
}
