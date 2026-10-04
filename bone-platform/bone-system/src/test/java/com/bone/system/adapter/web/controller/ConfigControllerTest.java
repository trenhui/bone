package com.bone.system.adapter.web.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
import com.bone.core.tenant.context.TenantContext;
import com.bone.core.util.DistributedIdGenerator;
import com.bone.system.adapter.web.dto.request.ConfigPageReq;
import com.bone.system.adapter.web.dto.request.CreateConfigReq;
import com.bone.system.adapter.web.dto.request.UpdateConfigReq;
import com.bone.system.adapter.web.dto.response.ConfigResp;
import com.bone.system.domain.model.config.SystemConfig;
import com.bone.system.domain.model.config.valueobject.ConfigKey;
import com.bone.system.domain.model.config.valueobject.ConfigType;
import com.bone.system.domain.model.config.valueobject.ConfigValue;
import com.bone.system.domain.repository.SystemConfigRepository;
import com.bone.system.testsupport.MetadataSdkIntegrationTestConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Import(MetadataSdkIntegrationTestConfiguration.class)
@WithMockUser(authorities = {"sys:config:write"})
@Transactional
public class ConfigControllerTest {

  /**
   * sys_config 是租户作用域表，SDK 对其访问失败关闭（ADR-0029）：无 {@code TenantContext} 时 {@code findById} / {@code
   * page} / 写路径一律抛 {@code MissingTenantContextException}。 生产环境该上下文由 JWT Filter 写入，本测试直调 Controller
   * 故须自行模拟。
   *
   * <p>取值与 {@link #newConfig} 建数据的 {@code tenantId = 0L} 保持一致（平台租户）。
   */
  @BeforeEach
  void setUpTenantContext() {
    TenantContext.setTenantId(0L);
  }

  @AfterEach
  void tearDownTenantContext() {
    TenantContext.clear();
  }

  @Autowired private ConfigController configController;

  @Autowired private SystemConfigRepository systemConfigRepository;

  private SystemConfig newConfig(String key, String value) {
    return SystemConfig.create(
        DistributedIdGenerator.generateLongId(),
        ConfigKey.of(key),
        ConfigValue.of(value),
        "测试配置",
        ConfigType.SYSTEM,
        false,
        "admin",
        0L);
  }

  @Test
  public void testCreate() {
    CreateConfigReq req = new CreateConfigReq();
    req.setConfigKey("test.key");
    req.setConfigValue("test value");
    req.setDescription("测试配置");
    req.setConfigType("SYSTEM");

    ApiResponse<Long> apiResponse = configController.create(req);

    assertTrue(apiResponse.isSuccess());
    assertNotNull(apiResponse.getData());

    SystemConfig config = systemConfigRepository.findById(apiResponse.getData());
    assertNotNull(config);
    assertEquals("test.key", config.getConfigKey().value());
    assertEquals("test value", config.getConfigValue().value());
  }

  @Test
  public void testUpdate() {
    SystemConfig config = newConfig("test.key", "original value");
    systemConfigRepository.save(config);

    UpdateConfigReq req = new UpdateConfigReq();
    req.setId(config.getId());
    req.setConfigValue("updated value");
    req.setDescription("更新后的测试配置");

    ApiResponse<Void> apiResponse = configController.update(req);

    assertTrue(apiResponse.isSuccess());

    SystemConfig updatedConfig = systemConfigRepository.findById(config.getId());
    assertNotNull(updatedConfig);
    assertEquals("updated value", updatedConfig.getConfigValue().value());
  }

  @Test
  public void testDelete() {
    SystemConfig config = newConfig("test.key", "test value");
    systemConfigRepository.save(config);

    ApiResponse<Void> apiResponse = configController.delete(config.getId());

    assertTrue(apiResponse.isSuccess());
    assertNull(systemConfigRepository.findById(config.getId()));
  }

  @Test
  public void testGetById() {
    SystemConfig config = newConfig("test.key", "test value");
    systemConfigRepository.save(config);

    ApiResponse<ConfigResp> apiResponse = configController.getById(config.getId());

    assertTrue(apiResponse.isSuccess());
    assertNotNull(apiResponse.getData());
    assertEquals(config.getId(), apiResponse.getData().getId());
    assertEquals("test.key", apiResponse.getData().getConfigKey());
    assertEquals("test value", apiResponse.getData().getConfigValue());
  }

  @Test
  public void testGetByKey() {
    SystemConfig config = newConfig("test.key", "test value");
    systemConfigRepository.save(config);

    ApiResponse<ConfigResp> apiResponse = configController.getByKey("test.key");

    assertTrue(apiResponse.isSuccess());
    assertNotNull(apiResponse.getData());
    assertEquals("test.key", apiResponse.getData().getConfigKey());
    assertEquals("test value", apiResponse.getData().getConfigValue());
  }

  @Test
  public void testPage() {
    for (int i = 1; i <= 3; i++) {
      systemConfigRepository.save(newConfig("test.key" + i, "test value" + i));
    }

    ConfigPageReq req = new ConfigPageReq();
    req.setPage(1);
    req.setSize(10);

    ApiResponse<PageResult<ConfigResp>> apiResponse = configController.page(req);

    assertTrue(apiResponse.isSuccess());
    assertNotNull(apiResponse.getData());
    assertNotNull(apiResponse.getData().getRecords());
    assertEquals(3, apiResponse.getData().getRecords().size());
  }
}
