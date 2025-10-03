package com.bone.metadata.sdk.extension.plugin;

/**
 * 统一插件接口 - 包含完整的生命周期管理
 */
public interface Plugin extends Configurable {

    /**
     * 初始化插件 - 由容器在加载后立即调用
     * @param context 插件上下文，提供服务注册和系统集成能力
     */
    void initialize(PluginContext context);

    /**
     * 启动插件 - 激活插件核心功能
     */
    void start();

    /**
     * 停止插件 - 暂停插件功能
     */
    void stop();

    /**
     * 销毁插件 - 容器在卸载前调用，进行资源清理
     */
    void destroy();

    /**
     * 获取插件唯一标识
     * @return 插件ID（建议使用反向域名命名）
     */
    String getId();

    /**
     * 获取插件名称（用户友好的显示名称）
     * @return 插件名称
     */
    String getName();

    /**
     * 获取插件版本
     * @return 语义化版本号
     */
    String getVersion();

    /**
     * 获取插件描述信息
     * @return 插件功能描述
     */
    default String getDescription() {
        return "No description provided";
    }

    /**
     * 验证插件配置
     * @throws PluginConfigurationException 当配置无效时抛出
     */
    default void validateConfig() throws PluginConfigurationException {}

    /**
     * 获取插件类型
     * @return 插件分类标识（如：database, storage, security等）
     */
    default String getPluginType() {
        return "generic";
    }
}