package com.bone.iam.domain.repository;

import com.bone.core.model.PageResult;
import com.bone.iam.domain.model.app.BoneApplication;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;

/**
 * 应用聚合的域仓储（写 + 本聚合读，ADR-0030）。
 *
 * <p>本聚合的读模型驻留此处，不另设 {@code *QueryPort}：分页与关键字检索都只涉及 {@link BoneApplication}
 * 自身，属「本聚合读」，建端口只是仪式性分层（E-3.2）。
 *
 * <p><b>为何本接口必须在 {@code domain.repository} 包下</b>：模块内联规则 {@code domain_no_query_builder} 只豁免 {@code
 * ..domain.repository..}，读侧 DSL 出现在 {@code domain} 的其余包会判违规； 而 {@code
 * read_side_dsl_only_in_query_adapter} 禁止 {@code application} 依赖读侧 DSL。 两条规定共同把「本聚合读的
 * DSL」唯一落点收敛到此处。
 */
public interface BoneApplicationRepository extends Repository<BoneApplication, Long> {

  /**
   * 按关键字与状态分页检索应用（本聚合读）。
   *
   * @param keyword 匹配 {@code name} 或 {@code code} 的关键字；为 null 或空白则不参与过滤
   * @param status 应用状态；为 null 则不参与过滤
   */
  default PageResult<BoneApplication> findPage(String keyword, Integer status, int page, int size) {
    var query = QueryBuilder.from(BoneApplication.class);
    if (keyword != null && !keyword.isBlank()) {
      String pattern = "%" + keyword + "%";
      query.where(
          w -> {
            w.and(BoneApplication::getName).like(pattern);
            w.or(BoneApplication::getCode).like(pattern);
          });
    }
    if (status != null) {
      query.where(BoneApplication::getStatus).eq(status);
    }
    return query.orderByDesc(BoneApplication::getCreatedAt).page(page, size);
  }
}
