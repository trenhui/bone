package com.bone.procurement.config;

import io.swagger.annotations.ApiOperation;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import springfox.documentation.builders.ApiInfoBuilder;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.service.Contact;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

/**
 * Swagger配置类
 * 用于自动生成和可视化API文档
 * 
 * @author bone
 */
@Configuration
@EnableSwagger2
public class SwaggerConfig {

    /**
     * 创建API文档的Bean
     * @return Docket对象
     */
    @Bean
    public Docket createRestApi() {
        return new Docket(DocumentationType.SWAGGER_2)
                // 设置API信息
                .apiInfo(apiInfo())
                // 选择需要生成文档的接口
                .select()
                // 扫描带有@ApiOperation注解的方法
                .apis(RequestHandlerSelectors.withMethodAnnotation(ApiOperation.class))
                // 或者扫描整个控制器包
                // .apis(RequestHandlerSelectors.basePackage("com.bone.procurement.controller"))
                // 匹配所有路径
                .paths(PathSelectors.any())
                .build()
                // 启用默认的响应消息
                .useDefaultResponseMessages(true);
    }

    /**
     * 构建API信息
     * @return ApiInfo对象
     */
    private ApiInfo apiInfo() {
        return new ApiInfoBuilder()
                // 标题
                .title("采购订单管理系统 API")
                // 描述
                .description("采购订单管理系统的RESTful API文档，提供订单的创建、查询、审批等功能")
                // 服务条款URL
                .termsOfServiceUrl("http://localhost:8080/")
                // 联系人信息
                .contact(new Contact("bone", "http://localhost:8080", "admin@bone.com"))
                // 版本
                .version("1.0.0")
                .build();
    }
}