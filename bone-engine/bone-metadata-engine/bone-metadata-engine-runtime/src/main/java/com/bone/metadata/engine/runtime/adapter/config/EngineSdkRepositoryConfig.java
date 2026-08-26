package com.bone.metadata.engine.runtime.adapter.config;

import com.bone.metadata.engine.runtime.adapter.po.MetaEntityPo;
import com.bone.metadata.engine.runtime.adapter.po.MetaFieldPo;
import com.bone.metadata.sdk.domain.annotation.EnableSqlRepositories;
import org.springframework.context.annotation.Configuration;

/**
 * 引擎适配器层 SDK Repository 装配配置。
 *
 * <p>启用 {@code bone-metadata-sdk} 的 {@code @EnableSqlRepositories}，让 SDK 在运行时为 {@code MetaEntityPo}
 * / {@code MetaFieldPo} 生成 {@code Repository} 代理 bean， 供 {@code SdkMetadataRepository} 注入使用。
 *
 * <p>注：本配置类由宿主（{@code bone-metadata-server}，模式 B）的 {@code @ComponentScan} 自动扫描， 实际数据源由宿主
 * 提供。引擎作为库本身不含数据源。
 */
@Configuration
@EnableSqlRepositories(basePackageClasses = {MetaEntityPo.class, MetaFieldPo.class})
public class EngineSdkRepositoryConfig {}
