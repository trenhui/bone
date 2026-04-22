package com.bone.engine.extension.studio.controller;

import com.bone.engine.extension.studio.controller.common.ApiResponse;
import com.bone.engine.extension.studio.domain.model.Extension;
import com.bone.engine.extension.studio.service.ExtensionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/extensions")
public class ExtensionController {
    
    private static final Logger log = LoggerFactory.getLogger(ExtensionController.class);

    @Autowired
    private ExtensionService extensionService;
    
    @GetMapping
    public ResponseEntity<ApiResponse<List<Extension>>> getExtensions(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long extPointId,
            @RequestParam(required = false) String tenantCode) {
        
        log.debug("getExtensions called");
        
        try {
            List<Extension> extensions;
            if (keyword != null) {
                extensions = extensionService.searchExtensions(keyword);
            } else if (extPointId != null && tenantCode != null) {
                extensions = extensionService.findExtensionsByExtPointId(extPointId);
            } else if (extPointId != null) {
                extensions = extensionService.findExtensionsByExtPointId(extPointId);
            } else if (tenantCode != null) {
                extensions = extensionService.findExtensionsByTenantCode(tenantCode);
            } else {
                extensions = extensionService.findAllExtensions();
            }
            
            ApiResponse<List<Extension>> response = ApiResponse.success("获取扩展实现列表成功", extensions)
                    .withMeta("totalElements", extensions.size());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("获取扩展实现列表失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("获取扩展实现列表失败：" + e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Extension>> getExtensionById(@PathVariable Long id) {
        log.debug("getExtensionById called for: {}", id);
        
        try {
            if (id == null || id <= 0) {
                log.warn("无效的扩展实现ID: {}", id);
                return ResponseEntity.badRequest().body(ApiResponse.error("无效的扩展实现ID"));
            }
            
            Extension extension = extensionService.findExtensionById(id);
            if (extension != null) {
                log.debug("获取扩展实现详情成功，ID: {}, 名称: {}", id, extension.getName());
                return ResponseEntity.ok(ApiResponse.success("获取扩展实现详情成功", extension));
            } else {
                log.warn("扩展实现不存在，ID: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("扩展实现不存在"));
            }
        } catch (Exception e) {
            log.error("获取扩展实现详情失败，ID: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("获取扩展实现详情失败：" + e.getMessage()));
        }
    }
    
    @GetMapping("/{id}/doc")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getExtensionDoc(@PathVariable Long id) {
        log.debug("getExtensionDoc called for: {}", id);
        
        try {
            if (id == null || id <= 0) {
                log.warn("无效的扩展实现ID: {}", id);
                return ResponseEntity.badRequest().body(ApiResponse.error("无效的扩展实现ID"));
            }
            
            Extension extension = extensionService.findExtensionById(id);
            if (extension != null) {
                Map<String, Object> docInfo = new HashMap<>();
                docInfo.put("id", extension.getId());
                docInfo.put("name", extension.getName());
                docInfo.put("className", extension.getClassName());
                docInfo.put("tenantCode", extension.getTenantCode());
                docInfo.put("bizCode", extension.getBizCode() != null ? extension.getBizCode() : "");
                docInfo.put("priority", extension.getPriority());
                docInfo.put("enabled", extension.isEnabled());
                docInfo.put("description", extension.getDescription() != null ? extension.getDescription() : "");
                docInfo.put("createTime", extension.getCreateTime());
                docInfo.put("updateTime", extension.getUpdateTime());
                
                return ResponseEntity.ok(ApiResponse.success("获取扩展实现文档详情成功", docInfo));
            } else {
                log.warn("扩展实现不存在，ID: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("扩展实现不存在"));
            }
        } catch (Exception e) {
            log.error("获取扩展实现文档详情失败，ID: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("获取扩展实现文档详情失败：" + e.getMessage()));
        }
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Extension>> createExtension(@RequestBody Extension extension) {
        log.debug("createExtension called");
        
        try {
            if (extension == null) {
                log.warn("扩展实现数据不能为空");
                return ResponseEntity.badRequest().body(ApiResponse.error("扩展实现数据不能为空"));
            }
            
            if (extension.getName() == null || extension.getName().trim().isEmpty()) {
                log.warn("扩展实现名称不能为空");
                return ResponseEntity.badRequest().body(ApiResponse.error("扩展实现名称不能为空"));
            }
            
            if (extension.getClassName() == null || extension.getClassName().trim().isEmpty()) {
                log.warn("扩展实现类名不能为空");
                return ResponseEntity.badRequest().body(ApiResponse.error("扩展实现类名不能为空"));
            }
            
            Extension savedExtension = extensionService.saveExtension(extension);
            log.info("创建扩展实现成功，ID: {}, 名称: {}", savedExtension.getId(), savedExtension.getName());
            
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("创建扩展实现成功", savedExtension));
        } catch (IllegalArgumentException e) {
            log.error("创建扩展实现失败: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error("参数错误：" + e.getMessage()));
        } catch (Exception e) {
            log.error("创建扩展实现时发生异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("创建扩展实现失败：" + e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Extension>> updateExtension(@PathVariable Long id, @RequestBody Extension extension) {
        log.debug("updateExtension called for: {}", id);
        
        try {
            if (id == null || id <= 0) {
                log.warn("无效的扩展实现ID: {}", id);
                return ResponseEntity.badRequest().body(ApiResponse.error("无效的扩展实现ID"));
            }
            
            if (extension == null) {
                log.warn("扩展实现数据不能为空");
                return ResponseEntity.badRequest().body(ApiResponse.error("扩展实现数据不能为空"));
            }
            
            Extension updatedExtension = extensionService.updateExtension(id, extension);
            if (updatedExtension != null) {
                log.info("更新扩展实现成功，ID: {}, 名称: {}", updatedExtension.getId(), updatedExtension.getName());
                
                return ResponseEntity.ok(ApiResponse.success("更新扩展实现成功", updatedExtension));
            } else {
                log.warn("扩展实现不存在，ID: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("扩展实现不存在"));
            }
        } catch (IllegalArgumentException e) {
            log.error("更新扩展实现失败: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error("参数错误：" + e.getMessage()));
        } catch (Exception e) {
            log.error("更新扩展实现时发生异常，ID: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("更新扩展实现失败：" + e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteExtension(@PathVariable Long id) {
        log.debug("deleteExtension called for: {}", id);
        
        try {
            if (id == null || id <= 0) {
                log.warn("无效的扩展实现ID: {}", id);
                return ResponseEntity.badRequest().body(ApiResponse.error("无效的扩展实现ID"));
            }
            
            Extension extension = extensionService.findExtensionById(id);
            if (extension == null) {
                log.warn("扩展实现不存在，ID: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("扩展实现不存在"));
            }
            
            extensionService.deleteExtension(id);
            log.info("删除扩展实现成功，ID: {}", id);
            
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            log.error("删除扩展实现失败: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error("参数错误：" + e.getMessage()));
        } catch (Exception e) {
            log.error("删除扩展实现时发生异常，ID: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("删除扩展实现失败：" + e.getMessage()));
        }
    }

    @PatchMapping("/{id}/enable")
    public ResponseEntity<ApiResponse<Extension>> enableExtension(@PathVariable Long id, @RequestParam boolean enabled) {
        log.debug("enableExtension called for: {}, enabled: {}", id, enabled);
        
        try {
            if (id == null || id <= 0) {
                log.warn("无效的扩展实现ID: {}", id);
                return ResponseEntity.badRequest().body(ApiResponse.error("无效的扩展实现ID"));
            }
            
            Extension updatedExtension = extensionService.enableExtension(id, enabled);
            if (updatedExtension != null) {
                log.info("{}扩展实现成功，ID: {}, 名称: {}", 
                        enabled ? "启用" : "禁用", updatedExtension.getId(), updatedExtension.getName());
                
                return ResponseEntity.ok(
                        ApiResponse.success(enabled ? "启用扩展实现成功" : "禁用扩展实现成功", updatedExtension));
            } else {
                log.warn("扩展实现不存在，ID: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("扩展实现不存在"));
            }
        } catch (IllegalArgumentException e) {
            log.error("{}扩展实现失败: {}", enabled ? "启用" : "禁用", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("参数错误：" + e.getMessage()));
        } catch (Exception e) {
            log.error("{}扩展实现时发生异常，ID: {}", enabled ? "启用" : "禁用", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error((enabled ? "启用" : "禁用") + "扩展实现失败：" + e.getMessage()));
        }
    }

    @PatchMapping("/{id}/priority")
    public ResponseEntity<ApiResponse<Extension>> updateExtensionPriority(@PathVariable Long id, @RequestParam Integer priority) {
        log.debug("updateExtensionPriority called for: {}, priority: {}", id, priority);
        
        try {
            if (id == null || id <= 0) {
                log.warn("无效的扩展实现ID: {}", id);
                return ResponseEntity.badRequest().body(ApiResponse.error("无效的扩展实现ID"));
            }
            
            if (priority == null || priority < 0) {
                log.warn("无效的优先级值: {}", priority);
                return ResponseEntity.badRequest().body(ApiResponse.error("优先级必须大于等于0"));
            }
            
            Extension updatedExtension = extensionService.updateExtensionPriority(id, priority);
            if (updatedExtension != null) {
                log.info("更新扩展实现优先级成功，ID: {}, 名称: {}, 优先级: {}", 
                        updatedExtension.getId(), updatedExtension.getName(), updatedExtension.getPriority());
                
                return ResponseEntity.ok(ApiResponse.success("更新扩展实现优先级成功", updatedExtension));
            } else {
                log.warn("扩展实现不存在，ID: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("扩展实现不存在"));
            }
        } catch (IllegalArgumentException e) {
            log.error("更新扩展实现优先级失败: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error("参数错误：" + e.getMessage()));
        } catch (Exception e) {
            log.error("更新扩展实现优先级时发生异常，ID: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("更新扩展实现优先级失败：" + e.getMessage()));
        }
    }

    @PostMapping("/scan")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> scanAndRegisterExtensions() {
        log.debug("scanAndRegisterExtensions called");
        
        try {
            Map<String, Integer> result = new HashMap<>();
            result.put("registeredCount", extensionService.registerExtensions());
            result.put("failedCount", 0);
            
            log.info("扫描并注册扩展实现成功，结果: {}", result);
            
            return ResponseEntity.ok(ApiResponse.success("扫描并注册扩展实现成功", result));
        } catch (Exception e) {
            log.error("扫描并注册扩展实现失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("扫描并注册扩展实现失败：" + e.getMessage()));
        }
    }

    @PostMapping("/validate")
    public ResponseEntity<ApiResponse<Map<String, Object>>> validateExtension(@RequestBody Extension extension) {
        log.debug("validateExtension called");
        
        try {
            if (extension == null) {
                log.warn("扩展实现数据不能为空");
                return ResponseEntity.badRequest().body(ApiResponse.error("扩展实现数据不能为空"));
            }
            
            if (extension.getName() == null || extension.getName().trim().isEmpty()) {
                log.warn("扩展实现名称不能为空");
                return ResponseEntity.badRequest().body(ApiResponse.error("扩展实现名称不能为空"));
            }
            
            boolean isValid = extensionService.validateExtension(extension);
            Map<String, Object> result = new HashMap<>();
            result.put("valid", isValid);
            result.put("name", extension.getName());
            
            if (isValid) {
                log.debug("扩展实现验证通过: {}", extension.getName());
                return ResponseEntity.ok(ApiResponse.success("扩展实现验证通过", result));
            } else {
                log.warn("扩展实现验证失败: {}", extension.getName());
                return ResponseEntity.badRequest().body(ApiResponse.error("扩展实现验证失败"));
            }
        } catch (IllegalArgumentException e) {
            log.error("验证扩展实现失败: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error("参数错误：" + e.getMessage()));
        } catch (Exception e) {
            log.error("验证扩展实现时发生异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("验证扩展实现失败：" + e.getMessage()));
        }
    }

    @GetMapping("/{id}/statistics")
    public ResponseEntity<String> getExtensionStatistics(@PathVariable Long id) {
        log.debug("getExtensionStatistics called for: {}", id);
        
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

    @PostMapping("/{id}/statistics/reset")
    public ResponseEntity<ApiResponse<Void>> resetExtensionStatistics(@PathVariable Long id) {
        log.debug("resetExtensionStatistics called for: {}", id);
        
        try {
            if (id == null || id <= 0) {
                log.warn("无效的扩展实现ID: {}", id);
                return ResponseEntity.badRequest().body(ApiResponse.error("无效的扩展实现ID"));
            }
            
            extensionService.resetExtensionStatistics(id);
            log.info("重置扩展实现统计信息成功，ID: {}", id);
            
            return ResponseEntity.ok(ApiResponse.success("重置扩展实现统计信息成功", null));
        } catch (IllegalArgumentException e) {
            log.error("重置扩展实现统计信息失败: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error("参数错误：" + e.getMessage()));
        } catch (Exception e) {
            log.error("重置扩展实现统计信息时发生异常，ID: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("重置扩展实现统计信息失败：" + e.getMessage()));
        }
    }
    
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getExtensionStats() {
        log.debug("getExtensionStats called");
        
        try {
            Map<String, Object> stats = new HashMap<>();
            stats.put("totalCount", extensionService.getTotalExtensionCount());
            stats.put("statsByStatus", extensionService.getExtensionStatsByStatus());
            stats.put("statsByExtPoint", extensionService.getExtensionStatsByExtPoint());
            log.debug("获取扩展实现统计信息成功");
            
            return ResponseEntity.ok(ApiResponse.success("获取扩展实现统计信息成功", stats));
        } catch (Exception e) {
            log.error("获取扩展实现统计信息失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("获取扩展实现统计信息失败：" + e.getMessage()));
        }
    }
}
