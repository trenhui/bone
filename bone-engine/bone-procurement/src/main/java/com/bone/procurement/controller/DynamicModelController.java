package com.bone.procurement.controller;

import com.bone.procurement.config.DynamicModelConfig;
import com.bone.procurement.service.DynamicModelDataService;
import com.bone.procurement.service.DynamicModelManager;
import com.bone.smartmeta.engine.metadata.EntityMetadata;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 动态模型控制器
 * 提供动态模型的管理和数据操作的REST API
 */
@RestController
@RequestMapping("/api/dynamic-models")
public class DynamicModelController {

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
        List<Map<String, Object>> models = new ArrayList<>();
        
        for (EntityMetadata metadata : dynamicModelManager.getAllRegisteredModels()) {
            Map<String, Object> modelInfo = new HashMap<>();
            modelInfo.put("name", metadata.getApiName());
            modelInfo.put("label", metadata.getLabel());
            modelInfo.put("description", metadata.getDescription());
            modelInfo.put("fieldCount", metadata.getFields().size());
            models.add(modelInfo);
        }
        
        return ResponseEntity.ok(models);
    }

    /**
     * 获取指定动态模型的详细定义
     */
    @GetMapping("/{modelName}")
    public ResponseEntity<EntityMetadata> getModel(@PathVariable String modelName) {
        EntityMetadata metadata = dynamicModelManager.getModelByName(modelName);
        if (metadata == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(metadata);
    }

    /**
     * 创建新的动态模型
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createModel(@RequestBody Map<String, Object> request) {
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
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("model", metadata);
            
            return ResponseEntity.status(HttpStatus.CREATED).body(result);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * 删除动态模型
     */
    @DeleteMapping("/{modelName}")
    public ResponseEntity<Map<String, Object>> deleteModel(@PathVariable String modelName) {
        try {
            dynamicModelManager.deleteModel(modelName);
            // 清空相关数据
            dynamicModelDataService.clearModelData(modelName);
            
            return ResponseEntity.ok(Collections.singletonMap("success", true));
        } catch (Exception e) {
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
    public ResponseEntity<Map<String, Object>> reloadModels() {
        try {
            dynamicModelManager.loadAllDynamicModels();
            return ResponseEntity.ok(Collections.singletonMap("success", true));
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
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
                Map<String, Object> data = dynamicModelDataService.getDataById(modelName, id);
                if (data == null) {
                    return ResponseEntity.notFound().build();
                }
                return ResponseEntity.ok(data);
            } else {
                // 获取所有数据
                List<Map<String, Object>> data = dynamicModelDataService.getAllData(modelName);
                return ResponseEntity.ok(data);
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Collections.singletonMap("error", e.getMessage()));
        }
    }

    /**
     * 创建动态模型数据
     */
    @PostMapping("/{modelName}/data")
    public ResponseEntity<?> createModelData(@PathVariable String modelName, 
                                          @RequestBody Map<String, Object> data) {
        try {
            Map<String, Object> createdData = dynamicModelDataService.createData(modelName, data);
            return ResponseEntity.status(HttpStatus.CREATED).body(createdData);
        } catch (Exception e) {
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
        try {
            Map<String, Object> updatedData = dynamicModelDataService.updateData(modelName, id, data);
            return ResponseEntity.ok(updatedData);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Collections.singletonMap("error", e.getMessage()));
        }
    }

    /**
     * 删除动态模型数据
     */
    @DeleteMapping("/{modelName}/data/{id}")
    public ResponseEntity<?> deleteModelData(@PathVariable String modelName, @PathVariable String id) {
        try {
            boolean deleted = dynamicModelDataService.deleteData(modelName, id);
            if (deleted) {
                return ResponseEntity.ok().build();
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Collections.singletonMap("error", e.getMessage()));
        }
    }

    /**
     * 查询动态模型数据
     */
    @PostMapping("/{modelName}/query")
    public ResponseEntity<?> queryModelData(@PathVariable String modelName, 
                                         @RequestBody Map<String, Object> conditions) {
        try {
            List<Map<String, Object>> results = dynamicModelDataService.queryData(modelName, conditions);
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Collections.singletonMap("error", e.getMessage()));
        }
    }

    /**
     * 获取动态模型配置信息
     */
    @GetMapping("/config/info")
    public ResponseEntity<Map<String, Object>> getConfigInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("enabled", dynamicModelConfig.isEnabled());
        info.put("modelCount", dynamicModelConfig.getModels().size());
        info.put("basePackage", dynamicModelConfig.getBasePackage());
        info.put("autoCreateTables", dynamicModelConfig.isAutoCreateTables());
        return ResponseEntity.ok(info);
    }
}