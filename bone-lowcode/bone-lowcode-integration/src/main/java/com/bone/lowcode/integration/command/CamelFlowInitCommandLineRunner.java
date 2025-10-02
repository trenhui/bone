package com.bone.lowcode.integration.command;

import com.bone.lowcode.integration.application.service.ICamelRouteService;
import com.bone.lowcode.integration.flow.visitor.camel.builder.BuilderFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 所有bean初始化完成之后加载camel流程
 */
@Component
public class CamelFlowInitCommandLineRunner implements CommandLineRunner {
    @Resource
    private ICamelRouteService iCamelRouteService;
    @Resource
    private BuilderFactory builderFactory;

    @Override
    public void run(String... args) throws Exception {
        builderFactory.runAfterApplicationStarted();
        iCamelRouteService.buildAllCamelFlow();
    }
}