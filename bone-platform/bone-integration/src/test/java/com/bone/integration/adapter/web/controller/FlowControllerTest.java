package com.bone.integration.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
import com.bone.integration.application.command.cmd.CreateFlowCmd;
import com.bone.integration.application.command.cmd.UpdateFlowCmd;
import com.bone.integration.application.query.dto.FlowDTO;
import com.bone.integration.application.query.qry.FlowPageQry;
import com.bone.integration.domain.model.flow.IntegrationFlow;
import com.bone.integration.domain.repository.IntegrationFlowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@TestPropertySource(locations = "classpath:application-test.yml")
@Transactional
public class FlowControllerTest {

    @Autowired
    private FlowController flowController;

    @Autowired
    private IntegrationFlowRepository integrationFlowRepository;

    @BeforeEach
    public void setUp() {
        // 清理测试数据
        integrationFlowRepository.deleteAll();
    }

    @Test
    public void testCreate() {
        // 准备测试数据
        CreateFlowCmd cmd = new CreateFlowCmd();
        cmd.setName("测试流程");
        cmd.setDescription("测试集成流程");

        // 执行测试
        ApiResponse<Long> apiResponse = flowController.create(cmd);

        // 验证结果
        assertTrue(apiResponse.isSuccess());
        assertTrue(apiResponse.getData() != null);
        
        // 验证数据已保存到数据库
        IntegrationFlow flow = integrationFlowRepository.findById(apiResponse.getData()).orElse(null);
        assertTrue(flow != null);
        assertEquals("测试流程", flow.getName());
        assertEquals("测试集成流程", flow.getDescription());
    }

    @Test
    public void testUpdate() {
        // 先创建一个流程
        IntegrationFlow flow = new IntegrationFlow();
        flow.setName("原始流程");
        flow.setDescription("原始流程描述");
        flow = integrationFlowRepository.save(flow);

        // 准备测试数据
        Long flowId = flow.getId();
        UpdateFlowCmd cmd = new UpdateFlowCmd();
        cmd.setName("更新后的流程");
        cmd.setDescription("更新后的流程描述");

        // 执行测试
        ApiResponse<Void> apiResponse = flowController.update(flowId, cmd);

        // 验证结果
        assertTrue(apiResponse.isSuccess());
        
        // 验证数据已更新
        IntegrationFlow updatedFlow = integrationFlowRepository.findById(flowId).orElse(null);
        assertTrue(updatedFlow != null);
        assertEquals("更新后的流程", updatedFlow.getName());
        assertEquals("更新后的流程描述", updatedFlow.getDescription());
    }

    @Test
    public void testPage() {
        // 创建测试数据
        for (int i = 1; i <= 3; i++) {
            IntegrationFlow flow = new IntegrationFlow();
            flow.setName("测试流程" + i);
            flow.setDescription("测试流程描述" + i);
            integrationFlowRepository.save(flow);
        }

        // 准备测试数据
        FlowPageQry qry = new FlowPageQry();
        qry.setPageNum(1);
        qry.setPageSize(10);

        // 执行测试
        ApiResponse<PageResult<FlowDTO>> apiResponse = flowController.page(qry);

        // 验证结果
        assertTrue(apiResponse.isSuccess());
        assertTrue(apiResponse.getData() != null);
        assertTrue(apiResponse.getData().getList() != null);
        assertEquals(3, apiResponse.getData().getList().size());
    }
}
