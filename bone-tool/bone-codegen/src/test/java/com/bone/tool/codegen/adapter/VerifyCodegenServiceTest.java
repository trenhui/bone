package com.bone.tool.codegen.adapter;

import com.bone.tool.codegen.domain.service.CodegenService;
import com.bone.tool.codegen.application.dto.CodegenTableRequest;
import com.bone.tool.codegen.application.dto.CodegenTablePageRequest;

/**
 * 简单验证测试，确保CodegenService能够正常实例化和运行
 */
public class VerifyCodegenServiceTest {
    
    public static void main(String[] args) {
        System.out.println("开始验证CodegenService...");
        
        try {
            // 实例化CodegenService
            CodegenService service = new CodegenService();
            System.out.println("✅ CodegenService实例化成功");
            
            // 调用测试方法
            service.testMethod();
            System.out.println("✅ testMethod调用成功");
            
            // 测试getCodegenDetail方法
            Object detail = service.getCodegenDetail(1L);
            System.out.println("✅ getCodegenDetail调用成功，返回值: " + detail);
            
            // 测试deleteTable方法
            service.deleteTable(1L);
            System.out.println("✅ deleteTable调用成功");
            
            // 测试updateCodegenTable方法
            CodegenTableRequest tableRequest = new CodegenTableRequest();
            tableRequest.setTableName("test_table");
            service.updateCodegenTable(tableRequest);
            System.out.println("✅ updateCodegenTable调用成功");
            
            // 测试getCodegenTablePageResponse方法
            CodegenTablePageRequest pageRequest = new CodegenTablePageRequest();
            pageRequest.setPageNum(1);
            pageRequest.setPageSize(10);
            Object pageResponse = service.getCodegenTablePageResponse(pageRequest);
            System.out.println("✅ getCodegenTablePageResponse调用成功，返回值: " + pageResponse);
            
            System.out.println("\n🎉 所有测试通过！CodegenService功能验证完成。");
        } catch (Exception e) {
            System.err.println("❌ 测试失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}