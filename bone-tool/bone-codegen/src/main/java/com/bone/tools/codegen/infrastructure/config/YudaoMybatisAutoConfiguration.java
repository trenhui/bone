package com.bone.tools.codegen.infrastructure.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.bone.tools.codegen.infrastructure.config.DefaultDBFieldHandler;

/**
 * MyBaits 配置类
 *
 * @author 芋道源码
 */
@Configuration // 目的：先于 MyBatis Plus 自动配置，避免 @MapperScan 可能扫描不到 Mapper 打印 warn 日志
@MapperScan(value = "com.bone.lowcode.codegen.domain.mapper", annotationClass = Mapper.class) // Mapper 懒加载，目前仅用于单元测试
public class YudaoMybatisAutoConfiguration {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor mybatisPlusInterceptor = new MybatisPlusInterceptor();
        mybatisPlusInterceptor.addInnerInterceptor(new PaginationInnerInterceptor()); // 分页插件
        return mybatisPlusInterceptor;
    }

    @Bean
    public MetaObjectHandler defaultMetaObjectHandler(){
        return new DefaultDBFieldHandler(); // 自动填充参数类
    }



}
