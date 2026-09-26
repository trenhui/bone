package com.bone.catalog.catalog.application;

import com.bone.core.model.PageResult;
import com.bone.catalog.catalog.adapter.web.dto.response.E2eEntity1202624423478541Response;
import com.bone.catalog.catalog.domain.model.e2eentity1202624423478541.E2eEntity1202624423478541;
import com.bone.catalog.catalog.domain.repository.E2eEntity1202624423478541Repository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * E2E实体 (物理表: meta_e2e_1202624423590500)应用服务。
 *
 * <p>由代码生成器基于表 e2e_entity_1202624423478541 生成。简单读写走本服务，不生成 CommandHandler / QueryHandler。
 */
@Service
@RequiredArgsConstructor
public class E2eEntity1202624423478541ApplicationService {

  private final E2eEntity1202624423478541Repository repository;

  @Transactional(readOnly = true)
  public E2eEntity1202624423478541Response get(Long id) {
    return E2eEntity1202624423478541Response.from(repository.findById(id));
  }

  @Transactional(readOnly = true)
  public PageResult<E2eEntity1202624423478541Response> page(int page, int size) {
    return repository.findPage(page, size).map(E2eEntity1202624423478541Response::from);
  }
}
