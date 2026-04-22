package com.bone.engine.extension.studio.controller;

import com.bone.engine.extension.studio.controller.common.ApiResponse;
import com.bone.engine.extension.studio.domain.model.ExtPoint;
import com.bone.engine.extension.studio.domain.model.Extension;
import com.bone.engine.extension.studio.service.ExtPointService;
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
@RequestMapping("/api/ext-points")
public class ExtPointController {

    private static final Logger log = LoggerFactory.getLogger(ExtPointController.class);

    @Autowired
    private ExtPointService extPointService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ExtPoint>>> getExtPoints(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String domain,
            @RequestParam(required = false) String category) {
        
        log.debug("getExtPoints called");
        
        try {
            List<ExtPoint> extPoints;
            if (keyword != null) {
                extPoints = extPointService.searchExtPoints(keyword);
            } else if (domain != null && category != null) {
                extPoints = extPointService.findExtPointsByDomain(domain);
            } else if (domain != null) {
                extPoints = extPointService.findExtPointsByDomain(domain);
            } else if (category != null) {
                extPoints = extPointService.findExtPointsByCategory(category);
            } else {
                extPoints = extPointService.findAllExtPoints();
            }
            
            return ResponseEntity.ok(ApiResponse.success("获取扩展点列表成功", extPoints));
        } catch (Exception e) {
            log.error("获取扩展点列表失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("获取扩展点列表失败：" + e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ExtPoint>> getExtPointById(@PathVariable Long id) {
        log.debug("getExtPointById called for: {}", id);
        
        try {
            if (id == null || id <= 0) {
                return ResponseEntity.badRequest().body(ApiResponse.error("无效的扩展点ID"));
            }
            
            ExtPoint extPoint = extPointService.findExtPointById(id);
            if (extPoint != null) {
                return ResponseEntity.ok(ApiResponse.success("获取扩展点详情成功", extPoint));
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("扩展点不存在"));
            }
        } catch (Exception e) {
            log.error("获取扩展点详情失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("获取扩展点详情失败：" + e.getMessage()));
        }
    }

    @GetMapping("/by-interface/{interfaceName}")
    public ResponseEntity<ApiResponse<ExtPoint>> getExtPointByInterfaceName(@PathVariable String interfaceName) {
        log.debug("getExtPointByInterfaceName called for: {}", interfaceName);
        
        try {
            if (interfaceName == null || interfaceName.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("接口名称不能为空"));
            }
            
            ExtPoint extPoint = extPointService.findExtPointByInterfaceName(interfaceName);
            if (extPoint != null) {
                return ResponseEntity.ok(ApiResponse.success("获取扩展点详情成功", extPoint));
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("扩展点不存在"));
            }
        } catch (Exception e) {
            log.error("获取扩展点详情失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("获取扩展点详情失败：" + e.getMessage()));
        }
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ExtPoint>> createExtPoint(@RequestBody ExtPoint extPoint) {
        log.debug("createExtPoint called");
        
        try {
            if (extPoint == null) {
                return ResponseEntity.badRequest().body(ApiResponse.error("扩展点数据不能为空"));
            }
            
            ExtPoint savedExtPoint = extPointService.saveExtPoint(extPoint);
            log.info("创建扩展点成功");
            
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("创建扩展点成功", savedExtPoint));
        } catch (Exception e) {
            log.error("创建扩展点失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("创建扩展点失败：" + e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ExtPoint>> updateExtPoint(@PathVariable Long id, @RequestBody ExtPoint extPoint) {
        log.debug("updateExtPoint called for: {}", id);
        
        try {
            if (id == null || id <= 0) {
                return ResponseEntity.badRequest().body(ApiResponse.error("无效的扩展点ID"));
            }
            
            if (extPoint == null) {
                return ResponseEntity.badRequest().body(ApiResponse.error("扩展点数据不能为空"));
            }
            
            ExtPoint updatedExtPoint = extPointService.updateExtPoint(id, extPoint);
            if (updatedExtPoint != null) {
                return ResponseEntity.ok(ApiResponse.success("更新扩展点成功", updatedExtPoint));
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("扩展点不存在"));
            }
        } catch (Exception e) {
            log.error("更新扩展点失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("更新扩展点失败：" + e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteExtPoint(@PathVariable Long id) {
        log.debug("deleteExtPoint called for: {}", id);
        
        try {
            if (id == null || id <= 0) {
                return ResponseEntity.badRequest().body(ApiResponse.error("无效的扩展点ID"));
            }
            
            List<Extension> extensions = extPointService.findExtensionsByExtPointId(id);
            if (!extensions.isEmpty()) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(ApiResponse.error("扩展点存在关联的扩展实现，请先删除相关扩展实现"));
            }
            
            extPointService.deleteExtPoint(id);
            log.info("删除扩展点成功");
            
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("删除扩展点失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("删除扩展点失败：" + e.getMessage()));
        }
    }

    @PatchMapping("/{id}/enable")
    public ResponseEntity<ApiResponse<ExtPoint>> enableExtPoint(@PathVariable Long id, @RequestParam boolean enabled) {
        log.debug("enableExtPoint called for: {}, enabled: {}", id, enabled);
        
        try {
            if (id == null || id <= 0) {
                return ResponseEntity.badRequest().body(ApiResponse.error("无效的扩展点ID"));
            }
            
            ExtPoint updatedExtPoint = extPointService.enableExtPoint(id, enabled);
            if (updatedExtPoint != null) {
                return ResponseEntity.ok(
                        ApiResponse.success((enabled ? "启用" : "禁用") + "扩展点成功", updatedExtPoint));
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("扩展点不存在"));
            }
        } catch (Exception e) {
            log.error("启用/禁用扩展点失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error((enabled ? "启用" : "禁用") + "扩展点失败：" + e.getMessage()));
        }
    }

    @GetMapping("/{id}/doc")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getExtPointDoc(@PathVariable Long id) {
        log.debug("getExtPointDoc called for: {}", id);
        
        try {
            if (id == null || id <= 0) {
                return ResponseEntity.badRequest().body(ApiResponse.error("无效的扩展点ID"));
            }
            
            ExtPoint extPoint = extPointService.findExtPointById(id);
            if (extPoint != null) {
                Map<String, Object> docInfo = new HashMap<>();
                docInfo.put("id", extPoint.getId());
                docInfo.put("name", extPoint.getName());
                docInfo.put("domain", extPoint.getDomain());
                docInfo.put("category", extPoint.getCategory());
                docInfo.put("interfaceName", extPoint.getInterfaceName());
                docInfo.put("description", extPoint.getDescription());
                docInfo.put("enabled", extPoint.isEnabled());
                docInfo.put("parametersInfo", "");
                docInfo.put("returnsInfo", "");
                docInfo.put("scenarios", "");
                
                return ResponseEntity.ok(ApiResponse.success("获取扩展点文档详情成功", docInfo));
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("扩展点不存在"));
            }
        } catch (Exception e) {
            log.error("获取扩展点文档详情失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("获取扩展点文档详情失败：" + e.getMessage()));
        }
    }

    @GetMapping("/{id}/extensions")
    public ResponseEntity<ApiResponse<List<Extension>>> getExtensionsByExtPoint(@PathVariable Long id) {
        log.debug("getExtensionsByExtPoint called for: {}", id);
        
        try {
            if (id == null || id <= 0) {
                return ResponseEntity.badRequest().body(ApiResponse.error("无效的扩展点ID"));
            }
            
            ExtPoint extPoint = extPointService.findExtPointById(id);
            if (extPoint == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("扩展点不存在"));
            }
            
            List<Extension> extensions = extPointService.findExtensionsByExtPointId(id);
            return ResponseEntity.ok(
                    ApiResponse.success("获取扩展点的扩展实现成功", extensions)
                            .withMeta("count", extensions.size())
                            .withMeta("extPointName", extPoint.getName()));
        } catch (Exception e) {
            log.error("获取扩展点的扩展实现失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("获取扩展点的扩展实现失败：" + e.getMessage()));
        }
    }

    @PostMapping("/scan")
    public ResponseEntity<ApiResponse<Map<String, Object>>> scanAndRegisterExtPoints() {
        log.debug("scanAndRegisterExtPoints called");
        
        try {
            int registeredCount = extPointService.scanAndRegisterExtPoints();
            Map<String, Object> result = new HashMap<>();
            result.put("registeredCount", registeredCount);
            result.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(ApiResponse.success("扫描并注册扩展点成功", result));
        } catch (Exception e) {
            log.error("扫描并注册扩展点失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("扫描并注册扩展点失败：" + e.getMessage()));
        }
    }

    @GetMapping("/domains")
    public ResponseEntity<ApiResponse<List<String>>> getAllDomains() {
        log.debug("getAllDomains called");
        
        try {
            List<String> domains = extPointService.findAllDomains();
            return ResponseEntity.ok(
                    ApiResponse.success("获取扩展点领域成功", domains)
                            .withMeta("count", domains.size()));
        } catch (Exception e) {
            log.error("获取扩展点领域失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("获取扩展点领域失败：" + e.getMessage()));
        }
    }

    @GetMapping("/categories")
    public ResponseEntity<ApiResponse<List<String>>> getAllCategories() {
        log.debug("getAllCategories called");
        
        try {
            List<String> categories = extPointService.findAllCategories();
            return ResponseEntity.ok(
                    ApiResponse.success("获取扩展点分类成功", categories)
                            .withMeta("count", categories.size()));
        } catch (Exception e) {
            log.error("获取扩展点分类失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("获取扩展点分类失败：" + e.getMessage()));
        }
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getExtPointStats() {
        log.debug("getExtPointStats called");
        
        try {
            Map<String, Object> stats = new HashMap<>();
            stats.put("totalCount", extPointService.getTotalExtPointCount());
            stats.put("domainStats", extPointService.getExtPointStatsByDomain());
            stats.put("categoryStats", extPointService.getExtPointStatsByCategory());
            stats.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(ApiResponse.success("获取扩展点统计信息成功", stats));
        } catch (Exception e) {
            log.error("获取扩展点统计信息失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("获取扩展点统计信息失败：" + e.getMessage()));
        }
    }
}
