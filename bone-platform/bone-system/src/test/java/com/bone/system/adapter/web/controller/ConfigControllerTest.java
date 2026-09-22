package com.bone.system.adapter.web.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
import com.bone.core.util.DistributedIdGenerator;
import com.bone.system.adapter.web.dto.request.ConfigPageReq;
import com.bone.system.adapter.web.dto.request.CreateConfigReq;
import com.bone.system.adapter.web.dto.request.UpdateConfigReq;
import com.bone.system.adapter.web.dto.response.ConfigResp;
import com.bone.system.domain.model.config.SystemConfig;
import com.bone.system.domain.model.config.vo.ConfigKey;
import com.bone.system.domain.model.config.vo.ConfigType;
import com.bone.system.domain.model.config.vo.ConfigValue;
import com.bone.system.domain.repository.SystemConfigRepository;
import com.bone.system.testsupport.MetadataSdkIntegrationTestConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Import(MetadataSdkIntegrationTestConfiguration.class)
@Transactional
public class ConfigControllerTest {

  @Autowired private ConfigController configController;

  @Autowired private SystemConfigRepository systemConfigRepository;

  private SystemConfig newConfig(String key, String value) {
    return SystemConfig.create(
        DistributedIdGenerator.generateLongId(),
        ConfigKey.of(key),
        ConfigValue.of(value),
        "测试配置",
        ConfigType.SYSTEM,
        false);
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
    req.setPageNum(1);
    req.setPageSize(10);

    ApiResponse<PageResult<ConfigResp>> apiResponse = configController.page(req);

    assertTrue(apiResponse.isSuccess());
    assertNotNull(apiResponse.getData());
    assertNotNull(apiResponse.getData().getRecords());
    assertEquals(3, apiResponse.getData().getRecords().size());
  }
}
