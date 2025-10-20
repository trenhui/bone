package com.bone.engine.extension.metadata;

import java.util.List;
import java.util.Map;

/**
 * 扩展点元数据收集器
 * <p>
 * 负责收集、存储和提供扩展点及其实现的元数据信息
 * </p>
 * 
 * @since 1.0.0
 */
public interface ExtPointMetadataCollector {
    
    /**
     * 初始化元数据收集
     */
    void initialize();
    
    /**
     * 刷新元数据
     */
    void refresh();
    
    /**
     * 获取指定扩展点的元数据
     * 
     * @param interfaceClass 扩展点接口类
     * @return 扩展点元数据，如果不存在返回null
     */
    ExtPointMetadata getExtPointMetadata(Class<?> interfaceClass);
    
    /**
     * 根据接口名称获取扩展点元数据
     * 
     * @param interfaceName 扩展点接口全限定名
     * @return 扩展点元数据，如果不存在返回null
     */
    ExtPointMetadata getExtPointMetadata(String interfaceName);
    
    /**
     * 获取所有扩展点元数据
     * 
     * @return 所有扩展点元数据的映射，键为接口全限定名
     */
    Map<String, ExtPointMetadata> getAllExtPointMetadata();
    
    /**
     * 注册扩展点实现元数据
     * 
     * @param interfaceClass 扩展点接口类
     * @param implClass 实现类
     * @param metadata 实现元数据
     */
    void registerExtensionImpl(Class<?> interfaceClass, Class<?> implClass, ExtImplMetadata metadata);
    
    /**
     * 获取扩展点的所有实现类元数据
     * 
     * @param interfaceClass 扩展点接口类
     * @return 实现类元数据列表
     */
    List<ExtImplMetadata> getExtensionImpls(Class<?> interfaceClass);
    
    /**
     * 获取指定实现类的元数据
     * 
     * @param implClass 实现类
     * @return 实现类元数据，如果不存在返回null
     */
    ExtImplMetadata getExtensionImplMetadata(Class<?> implClass);
    
    /**
     * 获取默认实现的元数据
     * 
     * @param interfaceClass 扩展点接口类
     * @return 默认实现的元数据，如果不存在返回null
     */
    ExtImplMetadata getDefaultImplementation(Class<?> interfaceClass);
    
    /**
     * 获取推荐实现的元数据
     * 
     * @param interfaceClass 扩展点接口类
     * @return 推荐实现的元数据，如果不存在返回null
     */
    ExtImplMetadata getRecommendedImplementation(Class<?> interfaceClass);
    
    /**
     * 按照优先级排序的实现列表
     * 
     * @param interfaceClass 扩展点接口类
     * @return 按优先级排序的实现元数据列表
     */
    List<ExtImplMetadata> getSortedImplementations(Class<?> interfaceClass);
    
    /**
     * 根据条件筛选实现
     * 
     * @param interfaceClass 扩展点接口类
     * @param conditions 筛选条件
     * @return 符合条件的实现元数据列表
     */
    List<ExtImplMetadata> filterImplementations(Class<?> interfaceClass, Map<String, String> conditions);
    
    /**
     * 检查扩展点是否存在
     * 
     * @param interfaceClass 扩展点接口类
     * @return 是否存在
     */
    boolean containsExtPoint(Class<?> interfaceClass);
    
    /**
     * 获取扩展点数量
     * 
     * @return 扩展点数量
     */
    int getExtPointCount();
    
    /**
     * 获取所有实现类数量
     * 
     * @return 实现类数量
     */
    int getExtensionImplCount();
    
    /**
     * 关闭元数据收集器
     */
    void close();
}