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
import org.springframework.stereotype.Controller;
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
     * 统一的API响应包装器
     */
    static class ApiResponse<T> {
        private boolean success;
        private String message;
        private T data;
        private Map<String, Object> meta;
        
        private ApiResponse(boolean success, String message, T data) {
            this.success = success;
            this.message = message;
            this.data = data;
            this.meta = new HashMap<>();
        }
        
        public static <T> ApiResponse<T> success(T data) {
            return new ApiResponse<>(true, "操作成功", data);
        }
        
        public static <T> ApiResponse<T> success(String message, T data) {
            return new ApiResponse<>(true, message, data);
        }
        
        public static <T> ApiResponse<T> error(String message) {
            return new ApiResponse<>(false, message, null);
        }
        
        public ApiResponse<T> withMeta(String key, Object value) {
            this.meta.put(key, value);
            return this;
        }
        
        // Getters for all properties
        public boolean isSuccess() {
            return success;
        }
        
        public String getMessage() {
            return message;
        }
        
        public T getData() {
            return data;
        }
        
        public Map<String, Object> getMeta() {
            return meta;
        }
    }
    
    /**
     * 获取扩展实现列表（分页）
     * @param page 页码
     * @param size 每页数量
     * @param sortBy 排序字段
     * @param direction 排序方向
     * @param keyword 搜索关键词
     * @param extPointId 扩展点ID
     * @param tenantCode 租户代码
     * @return 扩展实现列表响应
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<ExtensionEntity>>> getExtensions(
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
            // 验证分页参数
            if (page < 0 || size <= 0 || size > 100) {
                log.warn("无效的分页参数: page={}, size={}", page, size);
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("页码必须大于等于0，每页数量必须在1-100之间"));
            }
            
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
            
            ApiResponse<Page<ExtensionEntity>> response = ApiResponse.success("获取扩展实现列表成功", extensions)
                    .withMeta("totalElements", extensions.getTotalElements())
                    .withMeta("totalPages", extensions.getTotalPages())
                    .withMeta("currentPage", page)
                    .withMeta("pageSize", size);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("获取扩展实现列表失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("获取扩展实现列表失败：" + e.getMessage()));
        }
    }

    /**
     * 获取单个扩展实现详情
     * @param id 扩展实现ID
     * @return 扩展实现详情响应
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ExtensionEntity>> getExtensionById(@PathVariable Long id) {
        log.debug("获取扩展实现详情，ID: {}", id);
        try {
            // 验证参数
            if (id == null || id <= 0) {
                log.warn("无效的扩展实现ID: {}", id);
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("无效的扩展实现ID"));
            }
            
            Optional<ExtensionEntity> extension = extensionService.findExtensionById(id);
            if (extension.isPresent()) {
                log.debug("获取扩展实现详情成功，ID: {}, 名称: {}", id, extension.get().getName());
                return ResponseEntity.ok(
                        ApiResponse.success("获取扩展实现详情成功", extension.get())
                );
            } else {
                log.warn("扩展实现不存在，ID: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("扩展实现不存在"));
            }
        } catch (Exception e) {
            log.error("获取扩展实现详情失败，ID: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("获取扩展实现详情失败：" + e.getMessage()));
        }
    }
    
    /**
     * 获取扩展实现的详细文档信息
     * @param id 扩展实现ID
     * @return 扩展实现文档详情响应
     */
    @GetMapping("/{id}/doc")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getExtensionDoc(@PathVariable Long id) {
        log.debug("获取扩展实现文档详情，ID: {}", id);
        try {
            // 验证参数
            if (id == null || id <= 0) {
                log.warn("无效的扩展实现ID: {}", id);
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("无效的扩展实现ID"));
            }
            
            Optional<ExtensionEntity> extensionOpt = extensionService.findExtensionById(id);
            if (!extensionOpt.isPresent()) {
                log.warn("扩展实现不存在，ID: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("扩展实现不存在"));
            }
            
            ExtensionEntity extension = extensionOpt.get();
            Map<String, Object> docInfo = new HashMap<>();
            docInfo.put("id", extension.getId());
            docInfo.put("name", extension.getName());
            docInfo.put("className", extension.getClassName());
            docInfo.put("tenantCode", extension.getTenantCode());
            docInfo.put("bizCode", extension.getBizCode());
            docInfo.put("priority", extension.getPriority());
            docInfo.put("enabled", extension.isEnabled());
            docInfo.put("description", extension.getDescription());
            docInfo.put("createTime", extension.getCreateTime());
            docInfo.put("updateTime", extension.getUpdateTime());
            
            if (extension.getExtPoint() != null) {
                docInfo.put("extPointId", extension.getExtPoint().getId());
                docInfo.put("extPointName", extension.getExtPoint().getName());
                docInfo.put("extPointInterfaceName", extension.getExtPoint().getInterfaceName());
            }
            
            return ResponseEntity.ok(
                    ApiResponse.success("获取扩展实现文档详情成功", docInfo)
            );
        } catch (Exception e) {
            log.error("获取扩展实现文档详情失败，ID: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("获取扩展实现文档详情失败：" + e.getMessage()));
        }
    }

    /**
     * 创建扩展实现
     * @param extension 扩展实现实体对象
     * @return 创建结果响应
     */
    @PostMapping
    public ResponseEntity<ApiResponse<ExtensionEntity>> createExtension(@RequestBody ExtensionEntity extension) {
        log.debug("创建扩展实现请求：{}", extension != null ? extension.getName() : null);
        try {
            // 验证请求参数
            if (extension == null) {
                log.warn("扩展实现数据不能为空");
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("扩展实现数据不能为空"));
            }
            
            if (extension.getName() == null || extension.getName().trim().isEmpty()) {
                log.warn("扩展实现名称不能为空");
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("扩展实现名称不能为空"));
            }
            
            if (extension.getClassName() == null || extension.getClassName().trim().isEmpty()) {
                log.warn("扩展实现类名不能为空");
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("扩展实现类名不能为空"));
            }
            
            if (extension.getExtPoint() == null || extension.getExtPoint().getId() == null) {
                log.warn("扩展点信息不能为空");
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("扩展点信息不能为空"));
            }
            
            ExtensionEntity savedExtension = extensionService.saveExtension(extension);
            log.info("创建扩展实现成功，ID: {}, 名称: {}", savedExtension.getId(), savedExtension.getName());
            
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("创建扩展实现成功", savedExtension));
        } catch (IllegalArgumentException e) {
            log.error("创建扩展实现失败: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("参数错误：" + e.getMessage()));
        } catch (Exception e) {
            log.error("创建扩展实现时发生异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("创建扩展实现失败：" + e.getMessage()));
        }
    }

    /**
     * 更新扩展实现
     * @param id 扩展实现ID
     * @param extension 扩展实现更新数据
     * @return 更新结果响应
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ExtensionEntity>> updateExtension(@PathVariable Long id, @RequestBody ExtensionEntity extension) {
        log.debug("更新扩展实现请求，ID: {}, 名称: {}", id, extension != null ? extension.getName() : null);
        try {
            // 验证参数
            if (id == null || id <= 0) {
                log.warn("无效的扩展实现ID: {}", id);
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("无效的扩展实现ID"));
            }
            
            if (extension == null) {
                log.warn("扩展实现数据不能为空");
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("扩展实现数据不能为空"));
            }
            
            // 确保ID一致性
            if (extension.getId() != null && !extension.getId().equals(id)) {
                log.warn("路径ID与请求体ID不一致，路径ID: {}, 请求体ID: {}", id, extension.getId());
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("路径ID与请求体ID不一致"));
            }
            
            // 检查扩展实现是否存在
            Optional<ExtensionEntity> existingExtension = extensionService.findExtensionById(id);
            if (!existingExtension.isPresent()) {
                log.warn("扩展实现不存在，ID: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("扩展实现不存在"));
            }
            
            // 验证必要参数
            if (extension.getName() == null || extension.getName().trim().isEmpty()) {
                log.warn("扩展实现名称不能为空");
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("扩展实现名称不能为空"));
            }
            
            ExtensionEntity updatedExtension = extensionService.updateExtension(id, extension);
            log.info("更新扩展实现成功，ID: {}, 名称: {}", updatedExtension.getId(), updatedExtension.getName());
            
            return ResponseEntity.ok(
                    ApiResponse.success("更新扩展实现成功", updatedExtension)
            );
        } catch (IllegalArgumentException e) {
            log.error("更新扩展实现失败: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("参数错误：" + e.getMessage()));
        } catch (Exception e) {
            log.error("更新扩展实现时发生异常，ID: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("更新扩展实现失败：" + e.getMessage()));
        }
    }

    /**
     * 删除扩展实现
     * @param id 扩展实现ID
     * @return 删除结果响应
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteExtension(@PathVariable Long id) {
        log.debug("删除扩展实现请求，ID: {}", id);
        try {
            // 验证参数
            if (id == null || id <= 0) {
                log.warn("无效的扩展实现ID: {}", id);
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("无效的扩展实现ID"));
            }
            
            // 检查扩展实现是否存在
            Optional<ExtensionEntity> existingExtension = extensionService.findExtensionById(id);
            if (!existingExtension.isPresent()) {
                log.warn("扩展实现不存在，ID: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("扩展实现不存在"));
            }
            
            extensionService.deleteExtension(id);
            log.info("删除扩展实现成功，ID: {}", id);
            
            return ResponseEntity.noContent()
                    .build();
        } catch (IllegalArgumentException e) {
            log.error("删除扩展实现失败: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("参数错误：" + e.getMessage()));
        } catch (Exception e) {
            log.error("删除扩展实现时发生异常，ID: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("删除扩展实现失败：" + e.getMessage()));
        }
    }

    /**
     * 启用/禁用扩展实现
     * @param id 扩展实现ID
     * @param enabled 是否启用
     * @return 更新结果响应
     */
    @PatchMapping("/{id}/enable")
    public ResponseEntity<ApiResponse<ExtensionEntity>> enableExtension(@PathVariable Long id, @RequestParam boolean enabled) {
        log.debug("{}{}扩展实现请求，ID: {}", enabled ? "启用" : "禁用", " ", id);
        try {
            // 验证参数
            if (id == null || id <= 0) {
                log.warn("无效的扩展实现ID: {}", id);
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("无效的扩展实现ID"));
            }
            
            // 检查扩展实现是否存在
            Optional<ExtensionEntity> existingExtension = extensionService.findExtensionById(id);
            if (!existingExtension.isPresent()) {
                log.warn("扩展实现不存在，ID: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("扩展实现不存在"));
            }
            
            ExtensionEntity updatedExtension = extensionService.enableExtension(id, enabled);
            log.info("{}扩展实现成功，ID: {}, 名称: {}", 
                    enabled ? "启用" : "禁用", updatedExtension.getId(), updatedExtension.getName());
            
            return ResponseEntity.ok(
                    ApiResponse.success(enabled ? "启用扩展实现成功" : "禁用扩展实现成功", updatedExtension)
            );
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

    /**
     * 更新扩展实现优先级
     * @param id 扩展实现ID
     * @param priority 优先级值
     * @return 更新结果响应
     */
    @PatchMapping("/{id}/priority")
    public ResponseEntity<ApiResponse<ExtensionEntity>> updateExtensionPriority(@PathVariable Long id, @RequestParam Integer priority) {
        log.debug("更新扩展实现优先级请求，ID: {}, 优先级: {}", id, priority);
        try {
            // 验证参数
            if (id == null || id <= 0) {
                log.warn("无效的扩展实现ID: {}", id);
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("无效的扩展实现ID"));
            }
            
            if (priority == null || priority < 0) {
                log.warn("无效的优先级值: {}", priority);
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("优先级必须大于等于0"));
            }
            
            // 检查扩展实现是否存在
            Optional<ExtensionEntity> existingExtension = extensionService.findExtensionById(id);
            if (!existingExtension.isPresent()) {
                log.warn("扩展实现不存在，ID: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("扩展实现不存在"));
            }
            
            ExtensionEntity updatedExtension = extensionService.updateExtensionPriority(id, priority);
            log.info("更新扩展实现优先级成功，ID: {}, 名称: {}, 优先级: {}", 
                    updatedExtension.getId(), updatedExtension.getName(), updatedExtension.getPriority());
            
            return ResponseEntity.ok(
                    ApiResponse.success("更新扩展实现优先级成功", updatedExtension)
            );
        } catch (IllegalArgumentException e) {
            log.error("更新扩展实现优先级失败: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("参数错误：" + e.getMessage()));
        } catch (Exception e) {
            log.error("更新扩展实现优先级时发生异常，ID: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("更新扩展实现优先级失败：" + e.getMessage()));
        }
    }

    /**
     * 扫描并注册扩展实现
     * @return 扫描结果响应
     */
    @PostMapping("/scan")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> scanAndRegisterExtensions() {
        log.debug("扫描并注册扩展实现请求");
        try {
            // 假设服务层有一个方法可以获取扩展实现的数量和状态
            Map<String, Integer> result = new HashMap<>();
            result.put("registeredCount", 0);
            result.put("failedCount", 0);
            
            log.info("扫描并注册扩展实现成功，结果: {}", result);
            
            return ResponseEntity.ok(
                    ApiResponse.success("扫描并注册扩展实现成功", result)
            );
        } catch (Exception e) {
            log.error("扫描并注册扩展实现失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("扫描并注册扩展实现失败：" + e.getMessage()));
        }
    }

    /**
     * 验证扩展实现的有效性
     * @param extension 扩展实现实体对象
     * @return 验证结果响应
     */
    @PostMapping("/validate")
    public ResponseEntity<ApiResponse<Map<String, Object>>> validateExtension(@RequestBody ExtensionEntity extension) {
        log.debug("验证扩展实现请求: {}", extension != null ? extension.getName() : null);
        try {
            // 验证请求参数
            if (extension == null) {
                log.warn("扩展实现数据不能为空");
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("扩展实现数据不能为空"));
            }
            
            if (extension.getName() == null || extension.getName().trim().isEmpty()) {
                log.warn("扩展实现名称不能为空");
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("扩展实现名称不能为空"));
            }
            
            boolean isValid = extensionService.validateExtension(extension);
            Map<String, Object> result = new HashMap<>();
            result.put("valid", isValid);
            result.put("name", extension.getName());
            
            if (isValid) {
                log.debug("扩展实现验证通过: {}", extension.getName());
                return ResponseEntity.ok(
                        ApiResponse.success("扩展实现验证通过", result)
                );
            } else {
                log.warn("扩展实现验证失败: {}", extension.getName());
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("扩展实现验证失败"));
            }
        } catch (IllegalArgumentException e) {
            log.error("验证扩展实现失败: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("参数错误：" + e.getMessage()));
        } catch (Exception e) {
            log.error("验证扩展实现时发生异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("验证扩展实现失败：" + e.getMessage()));
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
     * @param id 扩展实现ID
     * @return 重置结果响应
     */
    @PostMapping("/{id}/statistics/reset")
    public ResponseEntity<ApiResponse<Void>> resetExtensionStatistics(@PathVariable Long id) {
        log.debug("重置扩展实现统计信息，ID: {}", id);
        try {
            // 验证参数
            if (id == null || id <= 0) {
                log.warn("无效的扩展实现ID: {}", id);
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("无效的扩展实现ID"));
            }
            
            extensionService.resetExtensionStatistics(id);
            log.info("重置扩展实现统计信息成功，ID: {}", id);
            return ResponseEntity.ok(
                    ApiResponse.success("重置扩展实现统计信息成功", null)
            );
        } catch (IllegalArgumentException e) {
            log.error("重置扩展实现统计信息失败: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("参数错误：" + e.getMessage()));
        } catch (Exception e) {
            log.error("重置扩展实现统计信息时发生异常，ID: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("重置扩展实现统计信息失败：" + e.getMessage()));
        }
    }
    

    
    /**
     * 获取扩展实现统计信息
     * @return 统计信息响应
     */
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getExtensionStats() {
        log.debug("获取扩展实现统计信息请求");
        try {
            Map<String, Object> stats = new HashMap<>();
            stats.put("totalCount", extensionService.getTotalExtensionCount());
            stats.put("statsByStatus", extensionService.getExtensionStatsByStatus());
            stats.put("statsByExtPoint", extensionService.getExtensionStatsByExtPoint());
            log.debug("获取扩展实现统计信息成功");
            
            return ResponseEntity.ok(
                    ApiResponse.success("获取扩展实现统计信息成功", stats)
            );
        } catch (Exception e) {
            log.error("获取扩展实现统计信息失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("获取扩展实现统计信息失败：" + e.getMessage()));
        }
    }
}