package com.bone.masterdata.adapter.web.controller;

import com.bone.core.result.ApiResponse;
import com.bone.core.result.PageResult;
import com.bone.masterdata.application.command.cmd.CreateMasterDataEntityCmd;
import com.bone.masterdata.application.command.cmd.UpdateMasterDataEntityCmd;
import com.bone.masterdata.application.query.dto.MasterDataEntityDTO;
import com.bone.masterdata.application.query.qry.MasterDataEntityPageQry;
import com.bone.masterdata.domain.entity.MasterDataEntity;
import com.bone.masterdata.domain.repository.MasterDataEntityRepository;
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
public class MasterDataEntityControllerTest {

    @Autowired
    private MasterDataEntityController masterDataEntityController;

    @Autowired
    private MasterDataEntityRepository masterDataEntityRepository;

    @BeforeEach
    public void setUp() {
        // 清理测试数据
        masterDataEntityRepository.deleteAll();
    }

    @Test
    public void testCreate() {
        // 准备测试数据
        CreateMasterDataEntityCmd cmd = new CreateMasterDataEntityCmd();
        cmd.setName("测试实体");
        cmd.setCode("TEST_ENTITY");
        cmd.setDescription("测试主数据实体");

        // 执行测试
        ApiResponse<Long> apiResponse = masterDataEntityController.create(cmd);

        // 验证结果
        assertTrue(apiResponse.isSuccess());
        assertTrue(apiResponse.getData() != null);
        
        // 验证数据已保存到数据库
        MasterDataEntity entity = masterDataEntityRepository.findById(apiResponse.getData()).orElse(null);
        assertTrue(entity != null);
        assertEquals("测试实体", entity.getName());
        assertEquals("TEST_ENTITY", entity.getCode());
    }

    @Test
    public void testUpdate() {
        // 先创建一个实体
        MasterDataEntity entity = new MasterDataEntity();
        entity.setName("原始实体");
        entity.setCode("ORIGINAL_ENTITY");
        entity = masterDataEntityRepository.save(entity);

        // 准备测试数据
        Long entityId = entity.getId();
        UpdateMasterDataEntityCmd cmd = new UpdateMasterDataEntityCmd();
        cmd.setId(entityId);
        cmd.setName("更新后的实体");
        cmd.setDescription("更新后的描述");

        // 执行测试
        ApiResponse<Void> apiResponse = masterDataEntityController.update(entityId, cmd);

        // 验证结果
        assertTrue(apiResponse.isSuccess());
        
        // 验证数据已更新
        MasterDataEntity updatedEntity = masterDataEntityRepository.findById(entityId).orElse(null);
        assertTrue(updatedEntity != null);
        assertEquals("更新后的实体", updatedEntity.getName());
    }

    @Test
    public void testList() {
        // 创建测试数据
        for (int i = 1; i <= 3; i++) {
            MasterDataEntity entity = new MasterDataEntity();
            entity.setName("测试实体" + i);
            entity.setCode("TEST_ENTITY_" + i);
            masterDataEntityRepository.save(entity);
        }

        // 准备测试数据
        MasterDataEntityPageQry qry = new MasterDataEntityPageQry();
        qry.setPageNum(1);
        qry.setPageSize(10);

        // 执行测试
        ApiResponse<PageResult<MasterDataEntityDTO>> apiResponse = masterDataEntityController.list(qry);

        // 验证结果
        assertTrue(apiResponse.isSuccess());
        assertTrue(apiResponse.getData() != null);
        assertTrue(apiResponse.getData().getList() != null);
        assertEquals(3, apiResponse.getData().getList().size());
    }

    @Test
    public void testDetail() {
        // 创建测试数据
        MasterDataEntity entity = new MasterDataEntity();
        entity.setName("测试实体");
        entity.setCode("TEST_ENTITY");
        entity = masterDataEntityRepository.save(entity);

        // 执行测试
        ApiResponse<MasterDataEntityDTO> apiResponse = masterDataEntityController.detail(entity.getId());

        // 验证结果
        assertTrue(apiResponse.isSuccess());
        assertTrue(apiResponse.getData() != null);
        assertEquals(entity.getId(), apiResponse.getData().getId());
        assertEquals("测试实体", apiResponse.getData().getName());
    }

    @Test
    public void testPublish() {
        // 创建测试数据
        MasterDataEntity entity = new MasterDataEntity();
        entity.setName("测试实体");
        entity.setCode("TEST_ENTITY");
        entity = masterDataEntityRepository.save(entity);

        // 执行测试
        ApiResponse<Void> apiResponse = masterDataEntityController.publish(entity.getId());

        // 验证结果
        assertTrue(apiResponse.isSuccess());
    }

    @Test
    public void testConvertFromBusinessEntity() {
        // 执行测试
        ApiResponse<Boolean> apiResponse = masterDataEntityController.convertFromBusinessEntity(1L);

        // 验证结果
        assertTrue(apiResponse.isSuccess());
        assertTrue(apiResponse.getData());
    }
}
