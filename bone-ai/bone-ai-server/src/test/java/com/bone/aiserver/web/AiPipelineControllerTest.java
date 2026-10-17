package com.bone.aiserver.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.aiorch.application.OrchestrationApplicationService;
import com.bone.aiorch.application.OrchestrationRequest;
import com.bone.aiorch.application.OrchestrationResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 演示端点的委托单测（纯 JUnit，不 boot Spring/SDK——宿主的 @EnableSqlRepositories 会触发 SDK 装配，@WebMvcTest 切片不适合）。
 * 覆盖控制器把请求转交编排服务的接线。
 */
class AiPipelineControllerTest {

  private final OrchestrationApplicationService orchestration =
      mock(OrchestrationApplicationService.class);
  private final AiPipelineController controller = new AiPipelineController(orchestration);

  @Test
  void run_delegatesToOrchestration() {
    OrchestrationRequest request = new OrchestrationRequest("t1", List.of(), Map.of(), null, false);
    when(orchestration.run(request))
        .thenReturn(new OrchestrationResult(List.of(), false, null, null));

    assertThat(controller.run(request).getData()).isNotNull();
    verify(orchestration).run(request);
  }
}
