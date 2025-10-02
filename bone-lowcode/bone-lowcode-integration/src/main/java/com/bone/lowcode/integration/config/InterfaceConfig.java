//package com.bone.lowcode.integration.config;
//
//import lombok.Data;
//
//@Data
//public class InterfaceConfig {
//    private String interfaceCode;         // 接口英文名称 (如 "acknowledgeClaim", "finalizeClaim" 等)
//    private String interfaceName;         // 接口中文名称 (如 "案件签收", "案件结案" 等)
//    private InterfaceType interfaceType;  // 表示接口调用类型的枚举。
//    private String protocol;              // 协议类型（FTP、HTTP、HTTPS、WebService 等）
//    private String sourceURI;             // 接收URI 地址
//    private AuthConfig authConfig;        // 安全认证配置
//    private DataFormat inputFormat;       // 输入参数格式 (JSON, XML, Java, CSV)
//    private String targetURI;             // 目标URI 地址
//    private DataFormat outputFormat;      // 输出参数格式 (JSON, XML, Java, CSV)
//    private String inputAdmFilePath;      // AtlasMap 输入映射文件路径
//    private String outputAdmFilePath;     // AtlasMap 输出映射文件路径
//}
