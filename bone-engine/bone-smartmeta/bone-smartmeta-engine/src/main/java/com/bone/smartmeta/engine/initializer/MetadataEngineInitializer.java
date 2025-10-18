package com.bone.smartmeta.engine.initializer;

import com.bone.smartmeta.engine.MetadataEngine;
import com.bone.smartmeta.engine.metadata.EntityMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;

import java.util.List;

/**
 * 元数据引擎初始化器
 * 负责在应用启动时初始化元数据
 */
public class MetadataEngineInitializer implements InitializingBean {

    private static final Logger logger = LoggerFactory.getLogger(MetadataEngineInitializer.class);
    
    private final MetadataEngine metadataEngine;
    private final List<EntityMetadata> initialEntityMetadata;

    @Autowired
    public MetadataEngineInitializer(MetadataEngine metadataEngine, 
                                   List<EntityMetadata> initialEntityMetadata) {
        this.metadataEngine = metadataEngine;
        this.initialEntityMetadata = initialEntityMetadata;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        logger.info("Initializing metadata engine");
        
        if (!CollectionUtils.isEmpty(initialEntityMetadata)) {
            logger.info("Registering {} initial entity metadata entries", initialEntityMetadata.size());
            
            for (EntityMetadata entityMetadata : initialEntityMetadata) {
                try {
                    // 使用metadataEngine注册实体元数据
                    metadataEngine.registerEntityMetadata(entityMetadata);
                    logger.debug("Successfully registered entity metadata");
                } catch (Exception e) {
                    logger.error("Failed to register entity metadata", e);
                    throw e;
                }
            }
            
            logger.info("Completed metadata engine initialization");
        } else {
            logger.info("No initial entity metadata provided");
        }
    }
    
    /**
     * 重新初始化元数据
     */
    public void reinitialize() throws Exception {
        logger.info("Reinitializing metadata engine");
        afterPropertiesSet();
    }
    
    /**
     * 获取已初始化的元数据数量
     */
    public int getInitializedMetadataCount() {
        return !CollectionUtils.isEmpty(initialEntityMetadata) ? initialEntityMetadata.size() : 0;
    }
}