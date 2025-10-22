package com.bone.engine.extension.studio.controller;

import com.bone.engine.extension.studio.model.ExtPointEntity;
import com.bone.engine.extension.studio.model.ExtensionEntity;
import com.bone.engine.extension.studio.service.ExtPointService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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
     */
    @GetMapping
    public ResponseEntity<Page<ExtPointEntity>> getExtPoints(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "desc") String direction,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String domain,
            @RequestParam(required = false) String category) {
        
        Sort.Direction sortDirection = direction.equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sortBy));
        
        Page<ExtPointEntity> extPoints;
        if (keyword != null) {
            extPoints = extPointService.searchExtPoints(keyword, pageable);
        } else if (domain != null && category != null) {
            List<ExtPointEntity> resultList = extPointService.findExtPointsByDomainAndCategory(domain, category);
            // 简单分页处理
            int start = Math.min((int)pageable.getOffset(), resultList.size());
            int end = Math.min((start + pageable.getPageSize()), resultList.size());
            Page<ExtPointEntity> pageResult = new org.springframework.data.domain.PageImpl<>(
                    resultList.subList(start, end), pageable, resultList.size());
            extPoints = pageResult;
        } else if (domain != null) {
            List<ExtPointEntity> resultList = extPointService.findExtPointsByDomain(domain);
            // 简单分页处理
            int start = Math.min((int)pageable.getOffset(), resultList.size());
            int end = Math.min((start + pageable.getPageSize()), resultList.size());
            Page<ExtPointEntity> pageResult = new org.springframework.data.domain.PageImpl<>(
                    resultList.subList(start, end), pageable, resultList.size());
            extPoints = pageResult;
        } else if (category != null) {
            List<ExtPointEntity> resultList = extPointService.findExtPointsByCategory(category);
            // 简单分页处理
            int start = Math.min((int)pageable.getOffset(), resultList.size());
            int end = Math.min((start + pageable.getPageSize()), resultList.size());
            Page<ExtPointEntity> pageResult = new org.springframework.data.domain.PageImpl<>(
                    resultList.subList(start, end), pageable, resultList.size());
            extPoints = pageResult;
        } else {
            extPoints = extPointService.findAllExtPoints(pageable);
        }
        
        return ResponseEntity.ok(extPoints);
    }

    /**
     * 获取单个扩展点详情
     */
    @GetMapping("/{id}")
    public ResponseEntity<ExtPointEntity> getExtPointById(@PathVariable Long id) {
        log.debug("获取扩展点详情，ID: {}", id);
        try {
            Optional<ExtPointEntity> extPoint = extPointService.findExtPointById(id);
            if (extPoint.isPresent()) {
                return ResponseEntity.ok(extPoint.get());
            } else {
                log.warn("扩展点不存在，ID: {}", id);
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.error("获取扩展点详情失败，ID: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 根据接口名称获取扩展点
     */
    @GetMapping("/by-interface/{interfaceName}")
    public ResponseEntity<ExtPointEntity> getExtPointByInterfaceName(@PathVariable String interfaceName) {
        log.debug("获取扩展点详情，接口名: {}", interfaceName);
        try {
            Optional<ExtPointEntity> extPoint = extPointService.findExtPointByInterfaceName(interfaceName);
            if (extPoint.isPresent()) {
                return ResponseEntity.ok(extPoint.get());
            } else {
                log.warn("扩展点不存在，接口名: {}", interfaceName);
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.error("获取扩展点详情失败，接口名: {}", interfaceName, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 创建扩展点
     */
    @PostMapping
    public ResponseEntity<ExtPointEntity> createExtPoint(@RequestBody ExtPointEntity extPoint) {
        ExtPointEntity savedExtPoint = extPointService.saveExtPoint(extPoint);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedExtPoint);
    }

    /**
     * 更新扩展点
     */
    @PutMapping("/{id}")
    public ResponseEntity<ExtPointEntity> updateExtPoint(@PathVariable Long id, @RequestBody ExtPointEntity extPoint) {
        ExtPointEntity updatedExtPoint = extPointService.updateExtPoint(id, extPoint);
        return ResponseEntity.ok(updatedExtPoint);
    }

    /**
     * 删除扩展点
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteExtPoint(@PathVariable Long id) {
        extPointService.deleteExtPoint(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * 启用/禁用扩展点
     */
    @PatchMapping("/{id}/enable")
    public ResponseEntity<ExtPointEntity> enableExtPoint(@PathVariable Long id, @RequestParam boolean enabled) {
        ExtPointEntity updatedExtPoint = extPointService.enableExtPoint(id, enabled);
        return ResponseEntity.ok(updatedExtPoint);
    }

    /**
     * 获取扩展点的所有扩展实现
     */
    @GetMapping("/{id}/extensions")
    public ResponseEntity<List<ExtensionEntity>> getExtensionsByExtPoint(@PathVariable Long id) {
        log.debug("获取扩展点的扩展实现，ID: {}", id);
        try {
            List<ExtensionEntity> extensions = extPointService.findExtensionsByExtPointId(id);
            log.debug("获取扩展点的扩展实现成功，ID: {}, 共 {} 个实现", id, extensions.size());
            return ResponseEntity.ok(extensions);
        } catch (IllegalArgumentException e) {
            log.error("获取扩展点的扩展实现失败: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            log.error("获取扩展点的扩展实现时发生异常，ID: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 扫描并注册扩展点
     */
    @PostMapping("/scan")
    public ResponseEntity<String> scanAndRegisterExtPoints() {
        int registeredCount = extPointService.scanAndRegisterExtPoints();
        return ResponseEntity.ok("成功注册 " + registeredCount + " 个扩展点");
    }

    /**
     * 获取所有可用的领域列表
     */
    @GetMapping("/domains")
    public ResponseEntity<List<String>> getAllDomains() {
        log.debug("获取所有扩展点领域");
        try {
            List<String> domains = extPointService.findAllDomains();
            log.debug("获取扩展点领域成功，共 {} 个领域", domains.size());
            return ResponseEntity.ok(domains);
        } catch (Exception e) {
            log.error("获取扩展点领域失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 获取所有可用的分类列表
     */
    @GetMapping("/categories")
    public ResponseEntity<List<String>> getAllCategories() {
        log.debug("获取所有扩展点分类");
        try {
            List<String> categories = extPointService.findAllCategories();
            log.debug("获取扩展点分类成功，共 {} 个分类", categories.size());
            return ResponseEntity.ok(categories);
        } catch (Exception e) {
            log.error("获取扩展点分类失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}