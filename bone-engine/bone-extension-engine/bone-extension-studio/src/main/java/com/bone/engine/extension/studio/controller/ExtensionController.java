package com.bone.engine.extension.studio.controller;

import com.bone.engine.extension.studio.model.ExtensionEntity;
import com.bone.engine.extension.studio.service.ExtensionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 扩展实现控制器，提供REST API
 */
@RestController
@RequestMapping("/api/extensions")
public class ExtensionController {
    
    private static final Logger log = LoggerFactory.getLogger(ExtensionController.class);

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
        
        log.debug("获取扩展实现列表，页码: {}, 每页数量: {}, 排序: {}, 关键词: {}, 扩展点ID: {}, 租户代码: {}", 
                page, size, sortBy + " " + direction, keyword, extPointId, tenantCode);
        
        try {
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
            
            log.debug("获取扩展实现列表成功，共找到 {} 条记录", extensions.getTotalElements());
            return ResponseEntity.ok(extensions);
        } catch (Exception e) {
            log.error("获取扩展实现列表失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 获取单个扩展实现详情
     */
    @GetMapping("/{id}")
    public ResponseEntity<ExtensionEntity> getExtensionById(@PathVariable Long id) {
        log.debug("获取扩展实现详情，ID: {}", id);
        try {
            Optional<ExtensionEntity> extension = extensionService.findExtensionById(id);
            return extension.map(ResponseEntity::ok)
                    .orElseGet(() -> {
                        log.warn("扩展实现不存在，ID: {}", id);
                        return ResponseEntity.notFound().build();
                    });
        } catch (Exception e) {
            log.error("获取扩展实现详情失败，ID: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * 获取扩展实现的详细文档信息
     */
    @GetMapping("/{id}/doc")
    public ResponseEntity<Map<String, Object>> getExtensionDoc(@PathVariable Long id) {
        log.debug("获取扩展实现文档详情，ID: {}", id);
        try {
            Optional<ExtensionEntity> extensionOpt = extensionService.findExtensionById(id);
            if (!extensionOpt.isPresent()) {
                log.warn("扩展实现不存在，ID: {}", id);
                return ResponseEntity.notFound().build();
            }
            
            ExtensionEntity extension = extensionOpt.get();
            Map<String, Object> docInfo = new HashMap<>();
            docInfo.put("name", extension.getName());
            docInfo.put("className", extension.getClassName());
            docInfo.put("tenantCode", extension.getTenantCode());
            docInfo.put("bizCode", extension.getBizCode());
            
            return ResponseEntity.ok(docInfo);
        } catch (Exception e) {
            log.error("获取扩展实现文档详情失败，ID: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 创建扩展实现
     */
    @PostMapping
    public ResponseEntity<ExtensionEntity> createExtension(@RequestBody ExtensionEntity extension) {
        log.debug("创建扩展实现: {}", extension.getName());
        try {
            ExtensionEntity savedExtension = extensionService.saveExtension(extension);
            log.info("创建扩展实现成功，ID: {}, 名称: {}", savedExtension.getId(), savedExtension.getName());
            return ResponseEntity.status(HttpStatus.CREATED).body(savedExtension);
        } catch (IllegalArgumentException e) {
            log.error("创建扩展实现失败: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            log.error("创建扩展实现时发生异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 更新扩展实现
     */
    @PutMapping("/{id}")
    public ResponseEntity<ExtensionEntity> updateExtension(@PathVariable Long id, @RequestBody ExtensionEntity extension) {
        log.debug("更新扩展实现，ID: {}, 名称: {}", id, extension.getName());
        try {
            ExtensionEntity updatedExtension = extensionService.updateExtension(id, extension);
            log.info("更新扩展实现成功，ID: {}, 名称: {}", updatedExtension.getId(), updatedExtension.getName());
            return ResponseEntity.ok(updatedExtension);
        } catch (IllegalArgumentException e) {
            log.error("更新扩展实现失败: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            log.error("更新扩展实现时发生异常，ID: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 删除扩展实现
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteExtension(@PathVariable Long id) {
        log.debug("删除扩展实现，ID: {}", id);
        try {
            extensionService.deleteExtension(id);
            log.info("删除扩展实现成功，ID: {}", id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            log.error("删除扩展实现失败: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            log.error("删除扩展实现时发生异常，ID: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 启用/禁用扩展实现
     */
    @PatchMapping("/{id}/enable")
    public ResponseEntity<ExtensionEntity> enableExtension(@PathVariable Long id, @RequestParam boolean enabled) {
        log.debug("{}扩展实现，ID: {}", enabled ? "启用" : "禁用", id);
        try {
            ExtensionEntity updatedExtension = extensionService.enableExtension(id, enabled);
            log.info("{}扩展实现成功，ID: {}, 名称: {}", 
                    enabled ? "启用" : "禁用", updatedExtension.getId(), updatedExtension.getName());
            return ResponseEntity.ok(updatedExtension);
        } catch (IllegalArgumentException e) {
            log.error("{}扩展实现失败: {}", enabled ? "启用" : "禁用", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            log.error("{}扩展实现时发生异常，ID: {}", enabled ? "启用" : "禁用", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 更新扩展实现的优先级
     */
    @PatchMapping("/{id}/priority")
    public ResponseEntity<ExtensionEntity> updateExtensionPriority(@PathVariable Long id, @RequestParam int priority) {
        log.debug("更新扩展实现优先级，ID: {}, 优先级: {}", id, priority);
        try {
            ExtensionEntity updatedExtension = extensionService.updateExtensionPriority(id, priority);
            log.info("更新扩展实现优先级成功，ID: {}, 名称: {}, 新优先级: {}", 
                    updatedExtension.getId(), updatedExtension.getName(), updatedExtension.getPriority());
            return ResponseEntity.ok(updatedExtension);
        } catch (IllegalArgumentException e) {
            log.error("更新扩展实现优先级失败: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            log.error("更新扩展实现优先级时发生异常，ID: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 扫描并注册扩展实现
     */
    @PostMapping("/scan")
    public ResponseEntity<String> scanAndRegisterExtensions() {
        log.debug("开始扫描并注册扩展实现");
        try {
            int registeredCount = extensionService.registerExtensions();
            log.info("扫描并注册扩展实现完成，共注册 {} 个", registeredCount);
            return ResponseEntity.ok("成功注册 " + registeredCount + " 个扩展实现");
        } catch (Exception e) {
            log.error("扫描并注册扩展实现失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("扫描并注册扩展实现失败: " + e.getMessage());
        }
    }

    /**
     * 验证扩展实现的有效性
     */
    @PostMapping("/validate")
    public ResponseEntity<String> validateExtension(@RequestBody ExtensionEntity extension) {
        log.debug("验证扩展实现: {}", extension.getName());
        try {
            boolean isValid = extensionService.validateExtension(extension);
            if (isValid) {
                log.debug("扩展实现验证通过: {}", extension.getName());
                return ResponseEntity.ok("扩展实现验证通过");
            } else {
                log.warn("扩展实现验证失败: {}", extension.getName());
                return ResponseEntity.badRequest().body("扩展实现验证失败");
            }
        } catch (Exception e) {
            log.error("验证扩展实现时发生异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("验证扩展实现失败: " + e.getMessage());
        }
    }

    /**
     * 获取扩展实现的调用统计信息
     */
    @GetMapping("/{id}/statistics")
    public ResponseEntity<String> getExtensionStatistics(@PathVariable Long id) {
        log.debug("获取扩展实现统计信息，ID: {}", id);
        try {
            String statistics = extensionService.getExtensionStatistics(id);
            return ResponseEntity.ok(statistics);
        } catch (IllegalArgumentException e) {
            log.error("获取扩展实现统计信息失败: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            log.error("获取扩展实现统计信息时发生异常，ID: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 重置扩展实现的调用统计信息
     */
    @PostMapping("/{id}/statistics/reset")
    public ResponseEntity<String> resetExtensionStatistics(@PathVariable Long id) {
        log.debug("重置扩展实现统计信息，ID: {}", id);
        try {
            extensionService.resetExtensionStatistics(id);
            log.info("重置扩展实现统计信息成功，ID: {}", id);
            return ResponseEntity.ok("统计信息已重置");
        } catch (IllegalArgumentException e) {
            log.error("重置扩展实现统计信息失败: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            log.error("重置扩展实现统计信息时发生异常，ID: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * 获取扩展实现统计信息
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getExtensionStats() {
        log.debug("获取扩展实现统计信息");
        try {
            Map<String, Object> stats = new HashMap<>();
            stats.put("totalCount", extensionService.getTotalExtensionCount());
            stats.put("statsByStatus", extensionService.getExtensionStatsByStatus());
            stats.put("statsByExtPoint", extensionService.getExtensionStatsByExtPoint());
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("获取扩展实现统计信息失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}