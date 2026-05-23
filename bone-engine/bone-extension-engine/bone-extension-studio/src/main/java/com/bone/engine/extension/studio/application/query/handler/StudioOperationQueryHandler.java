package com.bone.engine.extension.studio.application.query.handler;

import com.bone.engine.extension.studio.application.service.StudioLroService;
import com.bone.engine.extension.studio.domain.model.StudioOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** LRO 操作读侧。 */
@Component
@RequiredArgsConstructor
public class StudioOperationQueryHandler {

    private final StudioLroService lroService;

    public StudioOperation getOperation(String operationId) {
        return lroService.getOperation(operationId);
    }
}
