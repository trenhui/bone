package com.bone.studio.generator.application;

import com.bone.studio.generator.application.dto.GeneratorOperationView;
import com.bone.studio.generator.application.query.qry.GenerationOperationViewQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 查询代码生成操作视图（LRO 状态）。 */
@Component
@RequiredArgsConstructor
public class GenerationOperationViewQueryApplicationService {

  private final GenerationTaskOperationApplicationService operationService;

  @Transactional(readOnly = true)
  public GeneratorOperationView handle(GenerationOperationViewQuery qry) {
    return operationService.toOperationView(qry.operationId());
  }
}
