package com.bone.engine.extension.studio.controller;

import com.bone.engine.extension.studio.model.ExtPointEntity;
import com.bone.engine.extension.studio.model.ExtensionEntity;
import com.bone.engine.extension.studio.service.ExtPointService;
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
 * 扩展点控制器，提供REST API
 */
@RestController
@RequestMapping("/api/ext-points")
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
        Optional<ExtPointEntity> extPoint = extPointService.findExtPointById(id);
        return extPoint.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 根据接口名称获取扩展点
     */
    @GetMapping("/by-interface/{interfaceName}")
    public ResponseEntity<ExtPointEntity> getExtPointByInterfaceName(@PathVariable String interfaceName) {
        Optional<ExtPointEntity> extPoint = extPointService.findExtPointByInterfaceName(interfaceName);
        return extPoint.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
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
        List<ExtensionEntity> extensions = extPointService.findExtensionsByExtPointId(id);
        return ResponseEntity.ok(extensions);
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
        List<String> domains = extPointService.findAllDomains();
        return ResponseEntity.ok(domains);
    }

    /**
     * 获取所有可用的分类列表
     */
    @GetMapping("/categories")
    public ResponseEntity<List<String>> getAllCategories() {
        List<String> categories = extPointService.findAllCategories();
        return ResponseEntity.ok(categories);
    }
}