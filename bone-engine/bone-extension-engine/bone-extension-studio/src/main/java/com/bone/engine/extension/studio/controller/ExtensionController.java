package com.bone.engine.extension.studio.controller;

import com.bone.engine.extension.studio.model.ExtensionEntity;
import com.bone.engine.extension.studio.service.ExtensionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

/**
 * 扩展实现控制器，提供REST API
 */
@RestController
@RequestMapping("/api/extensions")
public class ExtensionController {

    @Autowired
    private ExtensionService extensionService;

    /**
     * 获取扩展实现列表（分页）
     */
    @GetMapping
    public ResponseEntity<Page<ExtensionEntity>> getExtensions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "desc") String direction,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long extPointId,
            @RequestParam(required = false) String tenantCode) {
        
        Sort.Direction sortDirection = direction.equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sortBy));
        
        Page<ExtensionEntity> extensions;
        if (keyword != null) {
            extensions = extensionService.searchExtensions(keyword, pageable);
        } else if (extPointId != null && tenantCode != null) {
            List<ExtensionEntity> resultList = extensionService.findExtensionsByExtPointIdAndTenantCode(extPointId, tenantCode);
            // 简单分页处理
            int start = Math.min((int)pageable.getOffset(), resultList.size());
            int end = Math.min((start + pageable.getPageSize()), resultList.size());
            Page<ExtensionEntity> pageResult = new org.springframework.data.domain.PageImpl<>(
                    resultList.subList(start, end), pageable, resultList.size());
            extensions = pageResult;
        } else if (extPointId != null) {
            List<ExtensionEntity> resultList = extensionService.findExtensionsByExtPointId(extPointId);
            // 简单分页处理
            int start = Math.min((int)pageable.getOffset(), resultList.size());
            int end = Math.min((start + pageable.getPageSize()), resultList.size());
            Page<ExtensionEntity> pageResult = new org.springframework.data.domain.PageImpl<>(
                    resultList.subList(start, end), pageable, resultList.size());
            extensions = pageResult;
        } else if (tenantCode != null) {
            List<ExtensionEntity> resultList = extensionService.findExtensionsByTenantCode(tenantCode);
            // 简单分页处理
            int start = Math.min((int)pageable.getOffset(), resultList.size());
            int end = Math.min((start + pageable.getPageSize()), resultList.size());
            Page<ExtensionEntity> pageResult = new org.springframework.data.domain.PageImpl<>(
                    resultList.subList(start, end), pageable, resultList.size());
            extensions = pageResult;
        } else {
            extensions = extensionService.findAllExtensions(pageable);
        }
        
        return ResponseEntity.ok(extensions);
    }

    /**
     * 获取单个扩展实现详情
     */
    @GetMapping("/{id}")
    public ResponseEntity<ExtensionEntity> getExtensionById(@PathVariable Long id) {
        Optional<ExtensionEntity> extension = extensionService.findExtensionById(id);
        return extension.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 创建扩展实现
     */
    @PostMapping
    public ResponseEntity<ExtensionEntity> createExtension(@RequestBody ExtensionEntity extension) {
        ExtensionEntity savedExtension = extensionService.saveExtension(extension);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedExtension);
    }

    /**
     * 更新扩展实现
     */
    @PutMapping("/{id}")
    public ResponseEntity<ExtensionEntity> updateExtension(@PathVariable Long id, @RequestBody ExtensionEntity extension) {
        ExtensionEntity updatedExtension = extensionService.updateExtension(id, extension);
        return ResponseEntity.ok(updatedExtension);
    }

    /**
     * 删除扩展实现
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteExtension(@PathVariable Long id) {
        extensionService.deleteExtension(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * 启用/禁用扩展实现
     */
    @PatchMapping("/{id}/enable")
    public ResponseEntity<ExtensionEntity> enableExtension(@PathVariable Long id, @RequestParam boolean enabled) {
        ExtensionEntity updatedExtension = extensionService.enableExtension(id, enabled);
        return ResponseEntity.ok(updatedExtension);
    }

    /**
     * 更新扩展实现的优先级
     */
    @PatchMapping("/{id}/priority")
    public ResponseEntity<ExtensionEntity> updateExtensionPriority(@PathVariable Long id, @RequestParam int priority) {
        ExtensionEntity updatedExtension = extensionService.updateExtensionPriority(id, priority);
        return ResponseEntity.ok(updatedExtension);
    }

    /**
     * 扫描并注册扩展实现
     */
    @PostMapping("/scan")
    public ResponseEntity<String> scanAndRegisterExtensions() {
        int registeredCount = extensionService.scanAndRegisterExtensions();
        return ResponseEntity.ok("成功注册 " + registeredCount + " 个扩展实现");
    }

    /**
     * 验证扩展实现的有效性
     */
    @PostMapping("/validate")
    public ResponseEntity<String> validateExtension(@RequestBody ExtensionEntity extension) {
        boolean isValid = extensionService.validateExtension(extension);
        if (isValid) {
            return ResponseEntity.ok("扩展实现验证通过");
        } else {
            return ResponseEntity.badRequest().body("扩展实现验证失败");
        }
    }

    /**
     * 获取扩展实现的调用统计信息
     */
    @GetMapping("/{id}/statistics")
    public ResponseEntity<String> getExtensionStatistics(@PathVariable Long id) {
        String statistics = extensionService.getExtensionStatistics(id);
        return ResponseEntity.ok(statistics);
    }

    /**
     * 重置扩展实现的调用统计信息
     */
    @PostMapping("/{id}/statistics/reset")
    public ResponseEntity<String> resetExtensionStatistics(@PathVariable Long id) {
        extensionService.resetExtensionStatistics(id);
        return ResponseEntity.ok("统计信息已重置");
    }
}