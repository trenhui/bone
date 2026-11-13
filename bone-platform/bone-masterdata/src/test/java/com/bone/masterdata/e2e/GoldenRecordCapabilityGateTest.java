package com.bone.masterdata.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.masterdata.adapter.web.controller.GoldenRecordController;
import com.bone.masterdata.application.GoldenRecordOrchestrationService;
import com.bone.masterdata.testsupport.MetadataSdkIntegrationTestConfiguration;
import com.bone.masterdata.testsupport.goldenrecord.GoldenRecordHttpTestConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * 能力开关的装配级负向探针：生产端口未就绪（默认 enabled=false）时，编排服务与 API 端点都不注册， 不对外暴露「能调用但落不了库」的假能力。即使测试夹具提供了端口
 * bean，开关仍然生效。
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = "bone.masterdata.golden-record.enabled=false")
@ActiveProfiles("test")
@Import({MetadataSdkIntegrationTestConfiguration.class, GoldenRecordHttpTestConfiguration.class})
class GoldenRecordCapabilityGateTest {

  @Autowired private ApplicationContext context;

  @Test
  @DisplayName("开关关闭时编排服务与 Controller 均不装配")
  void disabledCapabilityShouldNotExposeBeans() {
    assertThat(context.getBeanNamesForType(GoldenRecordOrchestrationService.class)).isEmpty();
    assertThat(context.getBeanNamesForType(GoldenRecordController.class)).isEmpty();
  }
}
