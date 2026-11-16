package com.bone.masterdata.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.masterdata.adapter.web.controller.ChangeRequestController;
import com.bone.masterdata.application.ChangeRequestApplicationService;
import com.bone.masterdata.application.MasterDataOutboxRelay;
import com.bone.masterdata.testsupport.MetadataSdkIntegrationTestConfiguration;
import com.bone.masterdata.testsupport.changerequest.ChangeRequestHttpTestConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * 能力开关的装配级负向探针：即便测试夹具提供了全部终局端口 bean，两个开关关闭时 分段 CR 编排 / API / Outbox 中继都不得注册——不对外暴露「能调用但落不了库」的假能力。
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
      "bone.masterdata.change-request.enabled=false",
      "bone.masterdata.outbox-relay.enabled=false"
    })
@ActiveProfiles("test")
@Import({MetadataSdkIntegrationTestConfiguration.class, ChangeRequestHttpTestConfiguration.class})
class ChangeRequestCapabilityGateTest {

  @Autowired private ApplicationContext context;

  @Test
  @DisplayName("两个开关关闭时编排服务 / Controller / 中继均不装配")
  void disabledCapabilitiesExposeNoBeans() {
    assertThat(context.getBeanNamesForType(ChangeRequestApplicationService.class)).isEmpty();
    assertThat(context.getBeanNamesForType(ChangeRequestController.class)).isEmpty();
    assertThat(context.getBeanNamesForType(MasterDataOutboxRelay.class)).isEmpty();
  }
}
