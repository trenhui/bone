package com.bone.system.adapter.web.controller;

import com.bone.system.adapter.web.dto.req.ConfigPageReq;
import com.bone.system.adapter.web.dto.req.CreateConfigReq;
import com.bone.system.adapter.web.dto.req.UpdateConfigReq;
import com.bone.system.adapter.web.dto.resp.ConfigResp;
import com.bone.system.domain.config.SystemConfig;
import com.bone.system.domain.repository.SystemConfigRepository;
import com.bone.system.common.result.ApiResponse;
import com.bone.system.common.result.PageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@TestPropertySource(locations = "classpath:application-test.yml")
@Transactional
public class ConfigControllerTest {

    @Autowired
    private ConfigController configController;

    @Autowired
    private SystemConfigRepository systemConfigRepository;

    @BeforeEach
    public void setUp() {
        // 清理测试数据
        systemConfigRepository.deleteAll();
    }

    @Test
    public void testCreate() {
        // 准备测试数据
        CreateConfigReq req = new CreateConfigReq();
        req.setConfigKey("test.key");
        req.setConfigValue("test value");
        req.setDescription("测试配置");

        // 执行测试
        ApiResponse<Long> apiResponse = configController.create(req);

        // 验证结果
        assertTrue(apiResponse.isSuccess());
        assertTrue(apiResponse.getData() != null);
        
        // 验证数据已保存到数据库
        SystemConfig config = systemConfigRepository.findById(apiResponse.getData()).orElse(null);
        assertTrue(config != null);
        assertEquals("test.key", config.getConfigKey());
        assertEquals("test value", config.getConfigValue());
    }

    @Test
    public void testUpdate() {
        // 先创建一个配置
        SystemConfig config = new SystemConfig();
        config.setConfigKey("test.key");
        config.setConfigValue("original value");
        config = systemConfigRepository.save(config);

        // 准备测试数据
        UpdateConfigReq req = new UpdateConfigReq();
        req.setId(config.getId());
        req.setConfigValue("updated value");
        req.setDescription("更新后的测试配置");

        // 执行测试
        ApiResponse<Void> apiResponse = configController.update(req);

        // 验证结果
        assertTrue(apiResponse.isSuccess());
        
        // 验证数据已更新
        SystemConfig updatedConfig = systemConfigRepository.findById(config.getId()).orElse(null);
        assertTrue(updatedConfig != null);
        assertEquals("updated value", updatedConfig.getConfigValue());
    }

    @Test
    public void testDelete() {
        // 先创建一个配置
        SystemConfig config = new SystemConfig();
        config.setConfigKey("test.key");
        config.setConfigValue("test value");
        config = systemConfigRepository.save(config);

        // 执行测试
        ApiResponse<Void> apiResponse = configController.delete(config.getId());

        // 验证结果
        assertTrue(apiResponse.isSuccess());
        
        // 验证数据已删除
        SystemConfig deletedConfig = systemConfigRepository.findById(config.getId()).orElse(null);
        assertTrue(deletedConfig == null);
    }

    @Test
    public void testGetById() {
        // 先创建一个配置
        SystemConfig config = new SystemConfig();
        config.setConfigKey("test.key");
        config.setConfigValue("test value");
        config = systemConfigRepository.save(config);

        // 执行测试
        ApiResponse<ConfigResp> apiResponse = configController.getById(config.getId());

        // 验证结果
        assertTrue(apiResponse.isSuccess());
        assertTrue(apiResponse.getData() != null);
        assertEquals(config.getId(), apiResponse.getData().getId());
        assertEquals("test.key", apiResponse.getData().getConfigKey());
        assertEquals("test value", apiResponse.getData().getConfigValue());
    }

    @Test
    public void testGetByKey() {
        // 先创建一个配置
        SystemConfig config = new SystemConfig();
        config.setConfigKey("test.key");
        config.setConfigValue("test value");
        config = systemConfigRepository.save(config);

        // 执行测试
        ApiResponse<ConfigResp> apiResponse = configController.getByKey("test.key");

        // 验证结果
        assertTrue(apiResponse.isSuccess());
        assertTrue(apiResponse.getData() != null);
        assertEquals("test.key", apiResponse.getData().getConfigKey());
        assertEquals("test value", apiResponse.getData().getConfigValue());
    }

    @Test
    public void testPage() {
        // 创建测试数据
        for (int i = 1; i <= 3; i++) {
            SystemConfig config = new SystemConfig();
            config.setConfigKey("test.key" + i);
            config.setConfigValue("test value" + i);
            systemConfigRepository.save(config);
        }

        // 准备测试数据
        ConfigPageReq req = new ConfigPageReq();
        req.setPageNum(1);
        req.setPageSize(10);

        // 执行测试
        ApiResponse<PageResult<ConfigResp>> apiResponse = configController.page(req);

        // 验证结果
        assertTrue(apiResponse.isSuccess());
        assertTrue(apiResponse.getData() != null);
        assertTrue(apiResponse.getData().getList() != null);
        assertEquals(3, apiResponse.getData().getList().size());
    }
}
