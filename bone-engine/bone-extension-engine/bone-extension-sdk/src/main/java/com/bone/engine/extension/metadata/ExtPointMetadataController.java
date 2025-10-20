package com.bone.engine.extension.metadata;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 扩展点元数据REST API控制器
 * <p>
 * 提供扩展点元数据的查询、管理接口，支持可视化平台的展示需求
 * </p>
 * 
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/ext-point/metadata")
public class ExtPointMetadataController {
    
    @Autowired
    private ExtPointMetadataService metadataService;
    
    /**
     * 获取所有扩展点元数据
     * 
     * @return 扩展点元数据列表
     */
    @GetMapping
    public ResponseEntity<List<ExtPointMetadata>> getAllExtPointMetadata() {
        return ResponseEntity.ok(metadataService.getAllExtPointMetadata());
    }
    
    /**
     * 根据接口名获取扩展点元数据
     * 
     * @param interfaceName 扩展点接口全限定名
     * @return 扩展点元数据
     */
    @GetMapping("/{interfaceName}")
    public ResponseEntity<?> getExtPointMetadata(@PathVariable String interfaceName) {
        return metadataService.getExtPointMetadata(interfaceName)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * 根据条件查询扩展点元数据
     * 
     * @param criteria 查询条件
     * @return 符合条件的扩展点元数据列表
     */
    @GetMapping("/search")
    public ResponseEntity<List<ExtPointMetadata>> searchExtPointMetadata(@RequestParam Map<String, String> criteria) {
        return ResponseEntity.ok(metadataService.findExtPointMetadata(criteria));
    }
    
    /**
     * 获取扩展点实现的详细元数据
     * 
     * @param interfaceName 扩展点接口名
     * @param implClassName 实现类名
     * @return 扩展实现元数据
     */
    @GetMapping("/{interfaceName}/implementations/{implClassName}")
    public ResponseEntity<?> getExtensionImplMetadata(
            @PathVariable String interfaceName,
            @PathVariable String implClassName) {
        
        return metadataService.getExtensionImplMetadata(interfaceName, implClassName)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * 刷新元数据缓存
     * 
     * @return 刷新结果
     */
    @PostMapping("/refresh")
    public ResponseEntity<Map<String, String>> refreshMetadata() {
        metadataService.refreshMetadata();
        return ResponseEntity.ok(Map.of("status", "success", "message", "元数据已刷新"));
    }
    
    /**
     * 更新扩展实现的路由配置
     * 
     * @param interfaceName 扩展点接口名
     * @param implClassName 实现类名
     * @param routingConfig 新的路由配置
     * @return 更新结果
     */
    @PutMapping("/{interfaceName}/implementations/{implClassName}/routing")
    public ResponseEntity<Map<String, String>> updateRoutingConfig(
            @PathVariable String interfaceName,
            @PathVariable String implClassName,
            @RequestBody Map<String, String> routingConfig) {
        
        boolean success = metadataService.updateExtensionRoutingConfig(interfaceName, implClassName, routingConfig);
        if (success) {
            return ResponseEntity.ok(Map.of("status", "success", "message", "路由配置已更新"));
        } else {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "路由配置更新失败"));
        }
    }
    
    /**
     * 获取扩展点使用统计信息
     * 
     * @return 使用统计信息
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, ?>> getExtPointUsageStats() {
        return ResponseEntity.ok(metadataService.getExtPointUsageStats());
    }
    
    /**
     * 导出扩展点元数据为JSON
     * 
     * @return JSON格式的元数据
     */
    @GetMapping("/export")
    public ResponseEntity<String> exportMetadata() {
        return ResponseEntity.ok(metadataService.exportMetadataAsJson());
    }
    
    /**
     * 导入扩展点元数据配置
     * 
     * @param metadataJson JSON格式的元数据配置
     * @return 导入结果
     */
    @PostMapping("/import")
    public ResponseEntity<Map<String, Object>> importMetadata(@RequestBody String metadataJson) {
        ExtPointMetadataService.ImportResult result = metadataService.importMetadataFromJson(metadataJson);
        
        Map<String, Object> response = Map.of(
            "success", result.isSuccess(),
            "updatedCount", result.getUpdatedCount(),
            "failedCount", result.getFailedCount(),
            "errorMessages", result.getErrorMessages()
        );
        
        return ResponseEntity.ok(response);
    }
}