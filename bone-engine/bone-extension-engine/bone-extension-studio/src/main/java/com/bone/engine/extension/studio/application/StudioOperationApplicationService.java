package com.bone.engine.extension.studio.application;

import com.bone.engine.extension.studio.application.service.StudioLroService;
import com.bone.engine.extension.studio.domain.model.operation.StudioOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** LRO 操作读侧。 */
@Component
@RequiredArgsConstructor
public class StudioOperationApplicationService {

  private final StudioLroService lroService;

  public StudioOperation getOperation(String operationId) {
    return lroService.getOperation(operationId);
  }
}
