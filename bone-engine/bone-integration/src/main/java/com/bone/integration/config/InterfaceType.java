//package com.bone.lowcode.integration.config;
//
///**
// * 表示接口调用类型的枚举。
// * 用于集成平台中区分接口调用的方向和使用场景，帮助开发者明确接口的职责和边界。
// * <p>
// * 适用场景：
// * - OUTBOUND：系统主动调用外部系统或第三方服务。
// * - INBOUND：外部系统调用本系统提供的服务。
// * - INTERNAL：系统内部模块或微服务之间的交互。
// * </p>
// */
//public enum InterfaceType {
//
//    /**
//     * 系统主动调用外部系统或第三方服务的接口。
//     * 适用场景：远程 REST API 调用、调用第三方支付服务、调用外部身份验证服务等。
//     */
//    OUTBOUND("系统主动调用外部系统或第三方服务"),
//
//    /**
//     * 外部系统调用本系统提供的服务接口。
//     * 适用场景：开放的 REST API、Webhook 回调接口、外部应用的请求入口等。
//     */
//    INBOUND("外部系统调用本系统提供的服务接口"),
//
//    /**
//     * 系统内部模块或微服务之间的交互接口。
//     * 适用场景：服务之间的 RPC 调用、模块化系统中的内部 API 调用等。
//     */
//    INTERNAL("系统内部模块或微服务之间的交互接口");
//
//    private final String description;
//
//    /**
//     * 构造枚举类型，提供详细描述信息。
//     *
//     * @param description 枚举值的描述信息
//     */
//    InterfaceType(String description) {
//        this.description = description;
//    }
//
//    /**
//     * 获取接口类型的描述信息。
//     *
//     * @return 描述信息
//     */
//    public String getDescription() {
//        return description;
//    }
//
//    /**
//     * 返回格式化的字符串表示，便于日志和调试中查看详细信息。
//     *
//     * @return 枚举类型名称和描述的结合
//     */
//    @Override
//    public String toString() {
//        return String.format("%s - %s", this.name(), this.description);
//    }
//
//    /**
//     * 根据描述信息反向查找枚举值。
//     * 适用于配置解析场景，例如从配置文件中读取描述并映射到枚举值。
//     *
//     * @param description 描述信息
//     * @return 匹配的枚举值
//     * @throws IllegalArgumentException 如果未找到匹配的枚举值
//     */
//    public static InterfaceType fromDescription(String description) {
//        for (InterfaceType type : InterfaceType.values()) {
//            if (type.description.equals(description)) {
//                return type;
//            }
//        }
//        throw new IllegalArgumentException("未知的接口类型描述: " + description);
//    }
//}
