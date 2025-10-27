package com.bone.engine.extension.studio.controller;

import com.bone.engine.extension.studio.controller.common.ApiResponse;
import com.bone.engine.extension.studio.model.ExtPointEntity;
import com.bone.engine.extension.studio.model.ExtensionEntity;
import com.bone.engine.extension.studio.service.ExtPointService;
import lombok.extern.slf4j.Slf4j;
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
 * 扩展点控制器，提供REST API
 */
@RestController
@RequestMapping("/api/ext-points")
@Slf4j
public class ExtPointController {

    @Autowired
    private ExtPointService extPointService;

    /**
     * 获取扩展点列表（分页）
     * @param page 页码，从0开始
     * @param size 每页大小
     * @param sortBy 排序字段
     * @param direction 排序方向（asc/desc）
     * @param keyword 搜索关键词
     * @param domain 领域过滤
     * @param category 分类过滤
     * @return 扩展点列表响应
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<ExtPointEntity>>> getExtPoints(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "desc") String direction,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String domain,
            @RequestParam(required = false) String category) {
        
        log.debug("获取扩展点列表，页码: {}, 每页大小: {}, 排序字段: {}, 排序方向: {}, 关键词: {}, 领域: {}, 分类: {}",
                page, size, sortBy, direction, keyword, domain, category);
        
        try {
            // 验证参数
            if (page < 0) {
                page = 0;
            }
            if (size < 1 || size > 100) {
                size = 10;
            }
            
            Sort.Direction sortDirection = direction.equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC;
            Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sortBy));
            
            Page<ExtPointEntity> extPoints;
            if (keyword != null) {
                extPoints = extPointService.searchExtPoints(keyword, pageable);
                log.debug("关键词搜索扩展点成功，共找到 {} 条记录", extPoints.getTotalElements());
            } else if (domain != null && category != null) {
                List<ExtPointEntity> resultList = extPointService.findExtPointsByDomainAndCategory(domain, category);
                // 简单分页处理
                int start = Math.min((int)pageable.getOffset(), resultList.size());
                int end = Math.min((start + pageable.getPageSize()), resultList.size());
                Page<ExtPointEntity> pageResult = new org.springframework.data.domain.PageImpl<>(
                        resultList.subList(start, end), pageable, resultList.size());
                extPoints = pageResult;
                log.debug("按领域和分类过滤扩展点成功，领域: {}, 分类: {}, 共找到 {} 条记录", 
                        domain, category, extPoints.getTotalElements());
            } else if (domain != null) {
                List<ExtPointEntity> resultList = extPointService.findExtPointsByDomain(domain);
                // 简单分页处理
                int start = Math.min((int)pageable.getOffset(), resultList.size());
                int end = Math.min((start + pageable.getPageSize()), resultList.size());
                Page<ExtPointEntity> pageResult = new org.springframework.data.domain.PageImpl<>(
                        resultList.subList(start, end), pageable, resultList.size());
                extPoints = pageResult;
                log.debug("按领域过滤扩展点成功，领域: {}, 共找到 {} 条记录", domain, extPoints.getTotalElements());
            } else if (category != null) {
                List<ExtPointEntity> resultList = extPointService.findExtPointsByCategory(category);
                // 简单分页处理
                int start = Math.min((int)pageable.getOffset(), resultList.size());
                int end = Math.min((start + pageable.getPageSize()), resultList.size());
                Page<ExtPointEntity> pageResult = new org.springframework.data.domain.PageImpl<>(
                        resultList.subList(start, end), pageable, resultList.size());
                extPoints = pageResult;
                log.debug("按分类过滤扩展点成功，分类: {}, 共找到 {} 条记录", category, extPoints.getTotalElements());
            } else {
                extPoints = extPointService.findAllExtPoints(pageable);
                log.debug("获取所有扩展点成功，共找到 {} 条记录", extPoints.getTotalElements());
            }
            
            return ResponseEntity.ok(
                    ApiResponse.success("获取扩展点列表成功", extPoints)
                            .withMeta("totalElements", extPoints.getTotalElements())
                            .withMeta("totalPages", extPoints.getTotalPages())
                            .withMeta("currentPage", page)
                            .withMeta("pageSize", size)
            );
        } catch (Exception e) {
            log.error("获取扩展点列表失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("获取扩展点列表失败：" + e.getMessage()));
        }
    }

    /**
     * 获取单个扩展点详情
     * @param id 扩展点ID
     * @return 扩展点详情响应
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ExtPointEntity>> getExtPointById(@PathVariable Long id) {
        log.debug("获取扩展点详情，ID: {}", id);
        try {
            if (id == null || id <= 0) {
                log.warn("无效的扩展点ID: {}", id);
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("无效的扩展点ID"));
            }
            
            Optional<ExtPointEntity> extPoint = extPointService.findExtPointById(id);
            if (extPoint.isPresent()) {
                log.debug("获取扩展点详情成功，ID: {}, 名称: {}", id, extPoint.get().getName());
                return ResponseEntity.ok(
                        ApiResponse.success("获取扩展点详情成功", extPoint.get())
                );
            } else {
                log.warn("扩展点不存在，ID: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("扩展点不存在"));
            }
        } catch (Exception e) {
            log.error("获取扩展点详情失败，ID: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("获取扩展点详情失败：" + e.getMessage()));
        }
    }

    /**
     * 根据接口名称获取扩展点
     * @param interfaceName 接口全限定名
     * @return 扩展点详情响应
     */
    @GetMapping("/by-interface/{interfaceName}")
    public ResponseEntity<ApiResponse<ExtPointEntity>> getExtPointByInterfaceName(@PathVariable String interfaceName) {
        log.debug("获取扩展点详情，接口名: {}", interfaceName);
        try {
            if (interfaceName == null || interfaceName.trim().isEmpty()) {
                log.warn("无效的接口名称");
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("接口名称不能为空"));
            }
            
            Optional<ExtPointEntity> extPoint = extPointService.findExtPointByInterfaceName(interfaceName);
            if (extPoint.isPresent()) {
                log.debug("获取扩展点详情成功，接口名: {}, 扩展点ID: {}", 
                        interfaceName, extPoint.get().getId());
                return ResponseEntity.ok(
                        ApiResponse.success("获取扩展点详情成功", extPoint.get())
                );
            } else {
                log.warn("扩展点不存在，接口名: {}", interfaceName);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("扩展点不存在"));
            }
        } catch (Exception e) {
            log.error("获取扩展点详情失败，接口名: {}", interfaceName, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("获取扩展点详情失败：" + e.getMessage()));
        }
    }

    /**
     * 创建扩展点
     * @param extPoint 扩展点实体对象
     * @return 创建结果响应
     */
    @PostMapping
    public ResponseEntity<ApiResponse<ExtPointEntity>> createExtPoint(@RequestBody ExtPointEntity extPoint) {
        log.debug("创建扩展点请求，接口名: {}", extPoint != null ? extPoint.getInterfaceName() : null);
        try {
            // 验证请求参数
            if (extPoint == null) {
                log.warn("扩展点数据不能为空");
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("扩展点数据不能为空"));
            }
            
            if (extPoint.getInterfaceName() == null || extPoint.getInterfaceName().trim().isEmpty()) {
                log.warn("接口名称不能为空");
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("接口名称不能为空"));
            }
            
            if (extPoint.getName() == null || extPoint.getName().trim().isEmpty()) {
                log.warn("扩展点名称不能为空");
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("扩展点名称不能为空"));
            }
            
            // 检查接口名是否已存在
            Optional<ExtPointEntity> existingExtPoint = extPointService.findExtPointByInterfaceName(extPoint.getInterfaceName());
            if (existingExtPoint.isPresent()) {
                log.warn("扩展点已存在，接口名: {}", extPoint.getInterfaceName());
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(ApiResponse.error("扩展点已存在"));
            }
            
            ExtPointEntity savedExtPoint = extPointService.saveExtPoint(extPoint);
            log.info("创建扩展点成功，ID: {}, 名称: {}", savedExtPoint.getId(), savedExtPoint.getName());
            
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("创建扩展点成功", savedExtPoint));
        } catch (Exception e) {
            log.error("创建扩展点失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("创建扩展点失败：" + e.getMessage()));
        }
    }

    /**
     * 更新扩展点
     * @param id 扩展点ID
     * @param extPoint 扩展点更新数据
     * @return 更新结果响应
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ExtPointEntity>> updateExtPoint(@PathVariable Long id, @RequestBody ExtPointEntity extPoint) {
        log.debug("更新扩展点请求，ID: {}", id);
        try {
            // 验证参数
            if (id == null || id <= 0) {
                log.warn("无效的扩展点ID: {}", id);
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("无效的扩展点ID"));
            }
            
            if (extPoint == null) {
                log.warn("扩展点数据不能为空");
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("扩展点数据不能为空"));
            }
            
            // 确保ID一致性
            if (extPoint.getId() != null && !extPoint.getId().equals(id)) {
                log.warn("路径ID与请求体ID不一致，路径ID: {}, 请求体ID: {}", id, extPoint.getId());
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("路径ID与请求体ID不一致"));
            }
            
            ExtPointEntity updatedExtPoint = extPointService.updateExtPoint(id, extPoint);
            log.info("更新扩展点成功，ID: {}, 名称: {}", updatedExtPoint.getId(), updatedExtPoint.getName());
            
            return ResponseEntity.ok(
                    ApiResponse.success("更新扩展点成功", updatedExtPoint)
            );
        } catch (Exception e) {
            log.error("更新扩展点失败，ID: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("更新扩展点失败：" + e.getMessage()));
        }
    }

    /**
     * 删除扩展点
     * @param id 扩展点ID
     * @return 删除结果响应
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteExtPoint(@PathVariable Long id) {
        log.debug("删除扩展点请求，ID: {}", id);
        try {
            // 验证参数
            if (id == null || id <= 0) {
                log.warn("无效的扩展点ID: {}", id);
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("无效的扩展点ID"));
            }
            
            // 检查扩展点是否存在
            Optional<ExtPointEntity> existingExtPoint = extPointService.findExtPointById(id);
            if (!existingExtPoint.isPresent()) {
                log.warn("扩展点不存在，ID: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("扩展点不存在"));
            }
            
            // 检查是否有关联的扩展实现
            List<ExtensionEntity> extensions = extPointService.findExtensionsByExtPointId(id);
            if (!extensions.isEmpty()) {
                log.warn("扩展点存在关联的扩展实现，无法删除，ID: {}", id);
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(ApiResponse.error("扩展点存在关联的扩展实现，请先删除相关扩展实现"));
            }
            
            extPointService.deleteExtPoint(id);
            log.info("删除扩展点成功，ID: {}", id);
            
            return ResponseEntity.noContent()
                    .build();
        } catch (Exception e) {
            log.error("删除扩展点失败，ID: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("删除扩展点失败：" + e.getMessage()));
        }
    }

    /**
     * 启用/禁用扩展点
     * @param id 扩展点ID
     * @param enabled 是否启用
     * @return 更新结果响应
     */
    @PatchMapping("/{id}/enable")
    public ResponseEntity<ApiResponse<ExtPointEntity>> enableExtPoint(@PathVariable Long id, @RequestParam boolean enabled) {
        log.debug("{}{}扩展点请求，ID: {}", enabled ? "启用" : "禁用", id);
        try {
            // 验证参数
            if (id == null || id <= 0) {
                log.warn("无效的扩展点ID: {}", id);
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("无效的扩展点ID"));
            }
            
            ExtPointEntity updatedExtPoint = extPointService.enableExtPoint(id, enabled);
            log.info("{}扩展点成功，ID: {}, 名称: {}", 
                    enabled ? "启用" : "禁用", 
                    updatedExtPoint.getId(), 
                    updatedExtPoint.getName());
            
            return ResponseEntity.ok(
                    ApiResponse.success(enabled ? "启用扩展点成功" : "禁用扩展点成功", updatedExtPoint)
            );
        } catch (Exception e) {
            log.error("{}扩展点失败，ID: {}", enabled ? "启用" : "禁用", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error((enabled ? "启用" : "禁用") + "扩展点失败：" + e.getMessage()));
        }
    }

    /**
     * 获取扩展点的详细文档信息
     * @param id 扩展点ID
     * @return 扩展点文档详情响应
     */
    @GetMapping("/{id}/doc")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getExtPointDoc(@PathVariable Long id) {
        log.info("获取扩展点文档详情，扩展点ID: {}", id);
        try {
            // 验证参数
            if (id == null || id <= 0) {
                log.warn("无效的扩展点ID: {}", id);
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("无效的扩展点ID"));
            }
            
            Optional<ExtPointEntity> extPointOpt = extPointService.findExtPointById(id);
            if (!extPointOpt.isPresent()) {
                log.warn("扩展点不存在，ID: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("扩展点不存在"));
            }
            
            ExtPointEntity extPoint = extPointOpt.get();
            Map<String, Object> docInfo = new HashMap<>();
            docInfo.put("id", extPoint.getId());
            docInfo.put("name", extPoint.getName());
            docInfo.put("domain", extPoint.getDomain());
            docInfo.put("category", extPoint.getCategory());
            docInfo.put("interfaceName", extPoint.getInterfaceName());
            docInfo.put("version", extPoint.getVersion());
            docInfo.put("description", extPoint.getDescription());
            docInfo.put("deprecated", extPoint.isDeprecated());
            docInfo.put("deprecatedSince", extPoint.getDeprecatedSince());
            docInfo.put("deprecatedIn", extPoint.getDeprecatedIn());
            
            // 添加基本的文档结构
            docInfo.put("parametersInfo", "");
            docInfo.put("returnsInfo", "");
            docInfo.put("scenarios", "");
            
            log.debug("获取扩展点文档详情成功，ID: {}", id);
            return ResponseEntity.ok(
                    ApiResponse.success("获取扩展点文档详情成功", docInfo)
            );
        } catch (Exception e) {
            log.error("获取扩展点文档详情失败: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("获取扩展点文档详情失败：" + e.getMessage()));
        }
    }
    
    /**
     * 获取扩展点的所有扩展实现
     * @param id 扩展点ID
     * @return 扩展实现列表响应
     */
    @GetMapping("/{id}/extensions")
    public ResponseEntity<ApiResponse<List<ExtensionEntity>>> getExtensionsByExtPoint(@PathVariable Long id) {
        log.debug("获取扩展点的扩展实现，ID: {}", id);
        try {
            // 验证参数
            if (id == null || id <= 0) {
                log.warn("无效的扩展点ID: {}", id);
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("无效的扩展点ID"));
            }
            
            // 检查扩展点是否存在
            Optional<ExtPointEntity> extPointOpt = extPointService.findExtPointById(id);
            if (!extPointOpt.isPresent()) {
                log.warn("扩展点不存在，ID: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("扩展点不存在"));
            }
            
            List<ExtensionEntity> extensions = extPointService.findExtensionsByExtPointId(id);
            log.debug("获取扩展点的扩展实现成功，ID: {}, 共 {} 个实现", id, extensions.size());
            
            return ResponseEntity.ok(
                    ApiResponse.success("获取扩展点的扩展实现成功", extensions)
                            .withMeta("count", extensions.size())
                            .withMeta("extPointName", extPointOpt.get().getName())
            );
        } catch (IllegalArgumentException e) {
            log.error("获取扩展点的扩展实现失败: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("参数错误：" + e.getMessage()));
        } catch (Exception e) {
            log.error("获取扩展点的扩展实现时发生异常，ID: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("获取扩展点的扩展实现失败：" + e.getMessage()));
        }
    }

    /**
     * 扫描并注册扩展点
     * @return 扫描注册结果响应
     */
    @PostMapping("/scan")
    public ResponseEntity<ApiResponse<Map<String, Object>>> scanAndRegisterExtPoints() {
        log.info("开始扫描并注册扩展点");
        try {
            int registeredCount = extPointService.scanAndRegisterExtPoints();
            log.info("扫描并注册扩展点完成，成功注册 {} 个扩展点", registeredCount);
            
            Map<String, Object> result = new HashMap<>();
            result.put("registeredCount", registeredCount);
            result.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(
                    ApiResponse.success("扫描并注册扩展点成功", result)
            );
        } catch (Exception e) {
            log.error("扫描并注册扩展点失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("扫描并注册扩展点失败：" + e.getMessage()));
        }
    }

    /**
     * 获取所有可用的领域列表
     * @return 领域列表响应
     */
    @GetMapping("/domains")
    public ResponseEntity<ApiResponse<List<String>>> getAllDomains() {
        log.debug("获取所有扩展点领域");
        try {
            List<String> domains = extPointService.findAllDomains();
            log.debug("获取扩展点领域成功，共 {} 个领域", domains.size());
            
            return ResponseEntity.ok(
                    ApiResponse.success("获取扩展点领域成功", domains)
                            .withMeta("count", domains.size())
            );
        } catch (Exception e) {
            log.error("获取扩展点领域失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("获取扩展点领域失败：" + e.getMessage()));
        }
    }

    /**
     * 获取所有可用的分类列表
     * @return 分类列表响应
     */
    @GetMapping("/categories")
    public ResponseEntity<ApiResponse<List<String>>> getAllCategories() {
        log.debug("获取所有扩展点分类");
        try {
            List<String> categories = extPointService.findAllCategories();
            log.debug("获取扩展点分类成功，共 {} 个分类", categories.size());
            
            return ResponseEntity.ok(
                    ApiResponse.success("获取扩展点分类成功", categories)
                            .withMeta("count", categories.size())
            );
        } catch (Exception e) {
            log.error("获取扩展点分类失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("获取扩展点分类失败：" + e.getMessage()));
        }
    }
    
    /**
     * 获取扩展点统计信息
     * @return 扩展点统计信息响应
     */
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getExtPointStats() {
        log.debug("获取扩展点统计信息");
        try {
            Map<String, Object> stats = new HashMap<>();
            stats.put("totalCount", extPointService.getTotalExtPointCount());
            stats.put("domainStats", extPointService.getExtPointStatsByDomain());
            stats.put("categoryStats", extPointService.getExtPointStatsByCategory());
            stats.put("timestamp", System.currentTimeMillis());
            
            log.debug("获取扩展点统计信息成功");
            return ResponseEntity.ok(
                    ApiResponse.success("获取扩展点统计信息成功", stats)
            );
        } catch (Exception e) {
            log.error("获取扩展点统计信息失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("获取扩展点统计信息失败：" + e.getMessage()));
        }
    }
}