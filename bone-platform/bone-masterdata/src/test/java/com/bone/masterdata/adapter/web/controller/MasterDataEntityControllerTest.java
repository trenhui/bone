package com.bone.masterdata.adapter.web.controller;

import com.bone.core.result.ApiResponse;
import com.bone.core.result.PageResult;
import com.bone.core.util.DistributedIdGenerator;
import com.bone.masterdata.application.command.cmd.CreateMasterDataEntityCommand;
import com.bone.masterdata.application.command.cmd.UpdateMasterDataEntityCommand;
import com.bone.masterdata.application.query.dto.MasterDataEntityDTO;
import com.bone.masterdata.application.query.qry.MasterDataEntityPageQuery;
import com.bone.masterdata.domain.entity.MasterDataEntity;
import com.bone.masterdata.domain.model.entity.vo.MasterDataEntityName;
import com.bone.masterdata.domain.repository.MasterDataEntityRepository;
import com.bone.masterdata.testsupport.MetadataSdkIntegrationTestConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.bone.core.exception.BizException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Import(MetadataSdkIntegrationTestConfiguration.class)
@Transactional
public class MasterDataEntityControllerTest {

    @Autowired
    private MasterDataEntityController masterDataEntityController;

    @Autowired
    private MasterDataEntityRepository masterDataEntityRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MasterDataEntity newEntity(String name) {
        return MasterDataEntity.create(
                DistributedIdGenerator.generateLongId(),
                null,
                MasterDataEntityName.of(name),
                "测试主数据实体",
                "default");
    }

    @Test
    public void testCreate() {
        CreateMasterDataEntityCommand cmd = CreateMasterDataEntityCommand.builder()
                .name("测试实体")
                .description("测试主数据实体")
                .category("default")
                .build();

        ApiResponse<Long> apiResponse = masterDataEntityController.create(cmd);

        assertTrue(apiResponse.isSuccess());
        assertNotNull(apiResponse.getData());

        MasterDataEntity entity = masterDataEntityRepository.findById(apiResponse.getData());
        assertNotNull(entity);
        assertEquals("测试实体", entity.getName().value());
    }

    @Test
    public void testUpdate() {
        MasterDataEntity entity = newEntity("原始实体");
        masterDataEntityRepository.save(entity);

        UpdateMasterDataEntityCommand cmd = UpdateMasterDataEntityCommand.builder()
                .id(entity.getId())
                .name("更新后的实体")
                .description("更新后的描述")
                .category("default")
                .build();

        ApiResponse<Void> apiResponse = masterDataEntityController.update(entity.getId(), cmd);

        assertTrue(apiResponse.isSuccess());

        MasterDataEntity updatedEntity = masterDataEntityRepository.findById(entity.getId());
        assertNotNull(updatedEntity);
        assertEquals("更新后的实体", updatedEntity.getName().value());
    }

    @Test
    public void testList() {
        for (int i = 1; i <= 3; i++) {
            masterDataEntityRepository.save(newEntity("测试实体" + i));
        }

        MasterDataEntityPageQuery qry = new MasterDataEntityPageQuery();
        qry.setPageNum(1);
        qry.setPageSize(10);

        ApiResponse<PageResult<MasterDataEntityDTO>> apiResponse = masterDataEntityController.list(qry);

        assertTrue(apiResponse.isSuccess());
        assertNotNull(apiResponse.getData());
        assertNotNull(apiResponse.getData().getList());
        assertEquals(3, apiResponse.getData().getList().size());
    }

    @Test
    public void testDetail() {
        MasterDataEntity entity = newEntity("测试实体");
        masterDataEntityRepository.save(entity);

        ApiResponse<MasterDataEntityDTO> apiResponse = masterDataEntityController.detail(entity.getId());

        assertTrue(apiResponse.isSuccess());
        assertNotNull(apiResponse.getData());
        assertEquals(entity.getId(), apiResponse.getData().getId());
        assertEquals("测试实体", apiResponse.getData().getName());
    }

    @Test
    public void testPublish() {
        MasterDataEntity entity = newEntity("测试实体");
        masterDataEntityRepository.save(entity);

        ApiResponse<Void> apiResponse = masterDataEntityController.publish(entity.getId());

        assertTrue(apiResponse.isSuccess());
    }

    @Test
    public void testConvertFromBusinessEntity() {
        long metaId = 9001L;
        jdbcTemplate.update(
                """
                INSERT INTO meta_entity (id, tenant_id, name, code, display_name, status, deleted)
                VALUES (?, 0, '客户', 'customer', '客户主数据', 1, 0)
                """,
                metaId);

        ApiResponse<Long> first = masterDataEntityController.convertFromBusinessEntity(metaId);
        assertTrue(first.isSuccess());
        assertNotNull(first.getData());

        ApiResponse<Long> second = masterDataEntityController.convertFromBusinessEntity(metaId);
        assertTrue(second.isSuccess());
        assertEquals(first.getData(), second.getData());
    }

    @Test
    public void testConvertRejectsDraftMetaEntity() {
        long metaId = 9002L;
        jdbcTemplate.update(
                """
                INSERT INTO meta_entity (id, tenant_id, name, code, display_name, status, deleted)
                VALUES (?, 0, '草稿', 'draft', '草稿实体', 0, 0)
                """,
                metaId);

        BizException ex =
                assertThrows(
                        BizException.class,
                        () -> masterDataEntityController.convertFromBusinessEntity(metaId));
        assertEquals(422, ex.getCode());
    }
}
