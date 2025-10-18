package com.bone.procurement.controller;

import com.bone.procurement.config.DynamicModelConfig;
import com.bone.procurement.exception.BusinessException;
import com.bone.procurement.service.DynamicModelDataService;
import com.bone.procurement.service.DynamicModelManager;
import com.bone.smartmeta.engine.metadata.EntityMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 动态模型控制器
 * 提供动态模型的管理和数据操作的REST API
 * 遵循Workday、Salesforce和Coupa等最佳实践设计
 */
@RestController
@RequestMapping("/api/dynamic-models")
public class DynamicModelController {

    private static final Logger log = LoggerFactory.getLogger(DynamicModelController.class);

    @Autowired
    private DynamicModelManager dynamicModelManager;

    @Autowired
    private DynamicModelDataService dynamicModelDataService;

    @Autowired
    private DynamicModelConfig dynamicModelConfig;

    /**
     * 获取所有动态模型定义
     */
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getAllModels() {
        log.info("获取所有注册的动态模型");
        try {
            List<Map<String, Object>> models = new ArrayList<>();
            
            for (EntityMetadata metadata : dynamicModelManager.getAllRegisteredModels()) {
                Map<String, Object> modelInfo = new HashMap<>();
                modelInfo.put("name", metadata.getApiName());
                modelInfo.put("label", metadata.getLabel());
                modelInfo.put("description", metadata.getDescription());
                modelInfo.put("fieldCount", metadata.getFields().size());
                models.add(modelInfo);
            }
            log.info("获取动态模型成功，总数: {}", models.size());
            return ResponseEntity.ok(models);
        } catch (Exception e) {
            log.error("获取动态模型失败", e);
            return ResponseEntity.internalServerError().body(Collections.emptyList());
        }
    }

    /**
     * 获取指定动态模型的详细定义
     */
    @GetMapping("/{modelName}")
    public ResponseEntity<EntityMetadata> getModel(@PathVariable String modelName) {
        log.info("获取动态模型定义: {}", modelName);
        try {
            EntityMetadata metadata = dynamicModelManager.getModelByName(modelName);
            if (metadata == null) {
                log.warn("动态模型不存在: {}", modelName);
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(metadata);
        } catch (Exception e) {
            log.error("获取动态模型定义失败: {}", modelName, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 创建新的动态模型
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createModel(@RequestBody Map<String, Object> request) {
        log.info("开始创建新的动态模型");
        try {
            String modelName = (String) request.get("name");
            String label = (String) request.get("label");
            Map<String, Map<String, Object>> fieldDefs = (Map<String, Map<String, Object>>) request.get("fields");
            
            if (modelName == null || modelName.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Collections.singletonMap("error", "模型名称不能为空"));
            }
            
            // 转换字段定义
            Map<String, DynamicModelConfig.DynamicFieldDefinition> fields = new HashMap<>();
            if (fieldDefs != null) {
                for (Map.Entry<String, Map<String, Object>> entry : fieldDefs.entrySet()) {
                    String fieldName = entry.getKey();
                    Map<String, Object> fieldProps = entry.getValue();
                    
                    DynamicModelConfig.DynamicFieldDefinition fieldDef = new DynamicModelConfig.DynamicFieldDefinition();
                    fieldDef.setType((String) fieldProps.getOrDefault("type", "string"));
                    fieldDef.setLabel((String) fieldProps.get("label"));
                    fieldDef.setRequired(Boolean.TRUE.equals(fieldProps.get("required")));
                    
                    if (fieldProps.containsKey("maxLength")) {
                        fieldDef.setMaxLength(((Number) fieldProps.get("maxLength")).intValue());
                    }
                    
                    fields.put(fieldName, fieldDef);
                }
            }
            
            // 创建模型
            EntityMetadata metadata = dynamicModelManager.createModel(modelName, label, fields);
            log.info("动态模型创建成功: {}", modelName);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("model", metadata);
            
            return ResponseEntity.status(HttpStatus.CREATED).body(result);
        } catch (Exception e) {
            log.error("动态模型创建失败", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * 复制动态模型
     */
    @PostMapping("/{modelName}/duplicate")
    public ResponseEntity<Map<String, Object>> duplicateModel(@PathVariable String modelName, 
                                                             @RequestBody Map<String, String> request) {
        log.info("复制动态模型: {}", modelName);
        try {
            String newModelName = request.get("newModelName");
            String newModelLabel = request.get("newModelLabel");
            
            if (newModelName == null || newModelName.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Collections.singletonMap("error", "新模型名称不能为空"));
            }
            
            // 复制模型
            EntityMetadata newMetadata = dynamicModelManager.duplicateModel(modelName, newModelName, newModelLabel);
            log.info("模型复制成功: {} -> {}", modelName, newModelName);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("newModel", newMetadata);
            
            return ResponseEntity.status(HttpStatus.CREATED).body(result);
        } catch (BusinessException e) {
            log.warn("复制模型失败: {}", e.getMessage());
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            log.error("复制模型失败", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "复制模型失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }
    
    /**
     * 删除动态模型
     */
    @DeleteMapping("/{modelName}")
    public ResponseEntity<Map<String, Object>> deleteModel(@PathVariable String modelName) {
        log.info("开始删除动态模型: {}", modelName);
        try {
            dynamicModelManager.deleteModel(modelName);
            // 清空相关数据
            dynamicModelDataService.clearModelData(modelName);
            log.info("动态模型删除成功: {}", modelName);
            
            return ResponseEntity.ok(Collections.singletonMap("success", true));
        } catch (Exception e) {
            log.error("动态模型删除失败: {}", modelName, e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * 重新加载所有动态模型
     */
    @PostMapping("/reload")
    public ResponseEntity<String> reloadModels() {
        log.info("重新加载所有动态模型");
        try {
            dynamicModelManager.loadAllDynamicModels();
            log.info("动态模型重新加载成功");
            return ResponseEntity.ok("动态模型重新加载成功");
        } catch (Exception e) {
            log.error("动态模型重新加载失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("动态模型重新加载失败: " + e.getMessage());
        }
    }
    
    /**
     * 重新加载指定动态模型
     */
    @PostMapping("/{modelName}/reload")
    public ResponseEntity<String> reloadModel(@PathVariable String modelName) {
        log.info("重新加载动态模型: {}", modelName);
        try {
            dynamicModelManager.reloadModel(modelName);
            log.info("成功重新加载动态模型: {}", modelName);
            return ResponseEntity.ok("动态模型重新加载成功: " + modelName);
        } catch (Exception e) {
            log.error("重新加载动态模型失败: {}", modelName, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("动态模型重新加载失败: " + e.getMessage());
        }
    }
    
    /**
     * 批量删除动态模型
     */
    @DeleteMapping("/batch")
    public ResponseEntity<Map<String, Object>> batchDeleteModels(@RequestBody List<String> modelNames) {
        log.info("批量删除动态模型，数量: {}", modelNames.size());
        try {
            int successCount = dynamicModelManager.batchDeleteModels(modelNames);
            
            // 清空相关数据
            for (String modelName : modelNames) {
                try {
                    dynamicModelDataService.clearModelData(modelName);
                } catch (Exception e) {
                    log.warn("清空模型数据失败: {}", modelName, e);
                }
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("deletedCount", successCount);
            result.put("totalCount", modelNames.size());
            
            log.info("批量删除动态模型完成，成功数量: {}", successCount);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("批量删除动态模型失败", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "批量删除动态模型失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }
    
    /**
     * 批量重新加载动态模型
     */
    @PostMapping("/batch/reload")
    public ResponseEntity<Map<String, Object>> batchReloadModels(@RequestBody List<String> modelNames) {
        log.info("批量重新加载动态模型，数量: {}", modelNames.size());
        try {
            int successCount = dynamicModelManager.batchReloadModels(modelNames);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("reloadedCount", successCount);
            result.put("totalCount", modelNames.size());
            
            log.info("批量重新加载动态模型完成，成功数量: {}", successCount);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("批量重新加载动态模型失败", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "批量重新加载动态模型失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }
    
    /**
     * 刷新所有动态模型
     */
    @PostMapping("/refresh")
    public ResponseEntity<String> refreshAllModels() {
        log.info("刷新所有动态模型");
        try {
            dynamicModelManager.refreshAllModels();
            log.info("所有动态模型已成功刷新");
            return ResponseEntity.ok("所有动态模型已刷新");
        } catch (Exception e) {
            log.error("刷新动态模型失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("刷新动态模型失败: " + e.getMessage());
        }
    }

    /**
     * 获取动态模型的数据
     */
    @GetMapping("/{modelName}/data")
    public ResponseEntity<?> getModelData(@PathVariable String modelName, 
                                        @RequestParam(required = false) String id) {
        try {
            if (id != null) {
                // 获取单条数据
                log.info("获取模型数据: {}, ID: {}", modelName, id);
                Map<String, Object> data = dynamicModelDataService.getDataById(modelName, id);
                if (data == null) {
                    log.warn("数据不存在: {}, ID: {}", modelName, id);
                    return ResponseEntity.notFound().build();
                }
                return ResponseEntity.ok(data);
            } else {
                // 获取所有数据
                log.info("获取模型所有数据: {}", modelName);
                List<Map<String, Object>> data = dynamicModelDataService.getAllData(modelName);
                log.info("获取数据成功: {}, 总数: {}", modelName, data.size());
                return ResponseEntity.ok(data);
            }
        } catch (IllegalArgumentException e) {
            log.warn("获取模型数据参数错误: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("获取模型数据失败", e);
            return ResponseEntity.badRequest().body(Collections.singletonMap("error", e.getMessage()));
        }
    }

    /**
     * 创建动态模型数据
     */
    @PostMapping("/{modelName}/data")
    public ResponseEntity<?> createModelData(@PathVariable String modelName, 
                                          @RequestBody Map<String, Object> data) {
        log.info("创建模型数据: {}", modelName);
        try {
            Map<String, Object> createdData = dynamicModelDataService.createData(modelName, data);
            log.info("创建数据成功: {}, ID: {}", modelName, createdData.get("id"));
            return ResponseEntity.status(HttpStatus.CREATED).body(createdData);
        } catch (Exception e) {
            log.error("创建模型数据失败: {}", modelName, e);
            return ResponseEntity.badRequest().body(Collections.singletonMap("error", e.getMessage()));
        }
    }

    /**
     * 更新动态模型数据
     */
    @PutMapping("/{modelName}/data/{id}")
    public ResponseEntity<?> updateModelData(@PathVariable String modelName, 
                                          @PathVariable String id, 
                                          @RequestBody Map<String, Object> data) {
        log.info("更新模型数据: {}, ID: {}", modelName, id);
        try {
            Map<String, Object> updatedData = dynamicModelDataService.updateData(modelName, id, data);
            log.info("更新数据成功: {}, ID: {}", modelName, id);
            return ResponseEntity.ok(updatedData);
        } catch (IllegalArgumentException e) {
            log.warn("数据不存在: {}, ID: {}", modelName, id);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("更新模型数据失败: {}, ID: {}", modelName, id, e);
            return ResponseEntity.badRequest().body(Collections.singletonMap("error", e.getMessage()));
        }
    }

    /**
     * 删除动态模型数据
     */
    @DeleteMapping("/{modelName}/data/{id}")
    public ResponseEntity<?> deleteModelData(@PathVariable String modelName, @PathVariable String id) {
        log.info("删除模型数据: {}, ID: {}", modelName, id);
        try {
            boolean deleted = dynamicModelDataService.deleteData(modelName, id);
            if (deleted) {
                log.info("删除数据成功: {}, ID: {}", modelName, id);
                return ResponseEntity.ok().build();
            } else {
                log.warn("数据不存在: {}, ID: {}", modelName, id);
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.error("删除模型数据失败: {}, ID: {}", modelName, id, e);
            return ResponseEntity.badRequest().body(Collections.singletonMap("error", e.getMessage()));
        }
    }

    /**
     * 查询动态模型数据
     */
    @PostMapping("/{modelName}/query")
    public ResponseEntity<?> queryModelData(@PathVariable String modelName, 
                                         @RequestBody Map<String, Object> conditions) {
        log.info("查询模型数据: {}", modelName);
        try {
            List<Map<String, Object>> results = dynamicModelDataService.queryData(modelName, conditions);
            log.info("查询数据成功: {}, 结果数量: {}", modelName, results.size());
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            log.error("查询模型数据失败: {}", modelName, e);
            return ResponseEntity.badRequest().body(Collections.singletonMap("error", e.getMessage()));
        }
    }
    
    /**
     * 批量创建动态模型数据
     */
    @PostMapping("/{modelName}/data/batch")
    public ResponseEntity<List<Map<String, Object>>> batchCreateModelData(@PathVariable String modelName, 
                                                                         @RequestBody List<Map<String, Object>> dataList) {
        log.info("开始批量创建动态模型数据: {}, 数量: {}", modelName, dataList.size());
        try {
            List<Map<String, Object>> createdData = dynamicModelDataService.batchCreateData(modelName, dataList);
            log.info("批量创建动态模型数据成功: {}, 数量: {}", modelName, createdData.size());
            return ResponseEntity.ok(createdData);
        } catch (IllegalArgumentException e) {
            log.warn("批量创建动态模型数据参数错误: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Collections.emptyList());
        } catch (Exception e) {
            log.error("批量创建动态模型数据失败: {}", modelName, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Collections.emptyList());
        }
    }
    
    /**
     * 批量删除动态模型数据
     */
    @DeleteMapping("/{modelName}/data/batch")
    public ResponseEntity<Map<String, Integer>> batchDeleteModelData(@PathVariable String modelName, 
                                                                   @RequestBody List<String> ids) {
        log.info("开始批量删除动态模型数据: {}, 数量: {}", modelName, ids.size());
        try {
            int deletedCount = dynamicModelDataService.batchDeleteData(modelName, ids);
            log.info("批量删除动态模型数据成功: {}, 删除数量: {}", modelName, deletedCount);
            Map<String, Integer> result = new HashMap<>();
            result.put("deletedCount", deletedCount);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            log.warn("批量删除动态模型数据参数错误: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("批量删除动态模型数据失败: {}", modelName, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 验证模型定义
     */
    @PostMapping("/validate")
    public ResponseEntity<Map<String, Object>> validateModelDefinition(@RequestBody Map<String, Object> request) {
        log.info("验证模型定义");
        try {
            String modelName = (String) request.get("name");
            Map<String, Map<String, Object>> fieldDefs = (Map<String, Map<String, Object>>) request.get("fields");
            
            // 转换字段定义格式
            Map<String, DynamicModelConfig.DynamicFieldDefinition> fields = new HashMap<>();
            if (fieldDefs != null) {
                for (Map.Entry<String, Map<String, Object>> entry : fieldDefs.entrySet()) {
                    String fieldName = entry.getKey();
                    Map<String, Object> fieldProps = entry.getValue();
                    
                    DynamicModelConfig.DynamicFieldDefinition fieldDef = new DynamicModelConfig.DynamicFieldDefinition();
                    fieldDef.setType((String) fieldProps.getOrDefault("type", "string"));
                    fieldDef.setLabel((String) fieldProps.get("label"));
                    fieldDef.setRequired(Boolean.TRUE.equals(fieldProps.get("required")));
                    
                    if (fieldProps.containsKey("maxLength")) {
                        fieldDef.setMaxLength(((Number) fieldProps.get("maxLength")).intValue());
                    }
                    if (fieldProps.containsKey("minLength")) {
                        fieldDef.setMinLength(((Number) fieldProps.get("minLength")).intValue());
                    }
                    if (fieldProps.containsKey("minValue")) {
                         Object minValueObj = fieldProps.get("minValue");
                         if (minValueObj instanceof Number) {
                             fieldDef.setMinValue(((Number) minValueObj).doubleValue());
                         }
                     }
                     if (fieldProps.containsKey("maxValue")) {
                         Object maxValueObj = fieldProps.get("maxValue");
                         if (maxValueObj instanceof Number) {
                             fieldDef.setMaxValue(((Number) maxValueObj).doubleValue());
                         }
                     }
                    if (fieldProps.containsKey("pattern")) {
                        fieldDef.setPattern((String) fieldProps.get("pattern"));
                    }
                    
                    fields.put(fieldName, fieldDef);
                }
            }
            
            String validationResult = dynamicModelManager.validateModelDefinition(modelName, fields);
            Map<String, Object> result = new HashMap<>();
            result.put("valid", validationResult == null);
            
            if (validationResult != null) {
                result.put("error", validationResult);
            }
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("验证模型定义失败", e);
            Map<String, Object> error = new HashMap<>();
            error.put("valid", false);
            error.put("error", "验证模型定义失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }
    
    /**
     * 获取模型统计信息
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getModelStats() {
        log.info("获取模型统计信息");
        try {
            Map<String, Object> stats = dynamicModelManager.getModelStats();
            log.info("获取模型统计信息成功");
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("获取模型统计信息失败", e);
            return ResponseEntity.internalServerError().body(Collections.singletonMap("error", "获取模型统计信息失败"));
        }
    }
    
    /**
     * 获取动态模型配置信息
     */
    @GetMapping("/config/info")
    public ResponseEntity<Map<String, Object>> getConfigInfo() {
        log.info("获取动态模型配置信息");
        Map<String, Object> info = new HashMap<>();
        info.put("enabled", dynamicModelConfig.isEnabled());
        info.put("modelCount", dynamicModelConfig.getModels().size());
        info.put("basePackage", dynamicModelConfig.getBasePackage());
        info.put("autoCreateTables", dynamicModelConfig.isAutoCreateTables());
        return ResponseEntity.ok(info);
    }
}