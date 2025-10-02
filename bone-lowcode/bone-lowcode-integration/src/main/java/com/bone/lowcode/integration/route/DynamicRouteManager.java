//package com.bone.lowcode.integration.route;
//
//import com.bone.lowcode.integration.config.InterfaceType;
//import com.bone.lowcode.integration.processor.InputRequestProcessor;
//import com.bone.lowcode.integration.processor.OutputResponseProcessor;
//import com.bone.lowcode.integration.config.PartnerConfig;
//import com.bone.lowcode.integration.config.InterfaceConfig;
//import org.apache.camel.CamelContext;
//import org.apache.camel.Exchange;
//import org.apache.camel.builder.RouteBuilder;
//import org.apache.camel.model.dataformat.JsonLibrary;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.CommandLineRunner;
//import org.springframework.http.HttpStatus;
//import org.springframework.stereotype.Component;
//
//import java.util.List;
//import java.util.Map;
//import java.util.concurrent.*;
//
//@Component
//public class DynamicRouteManager implements CommandLineRunner {
//
//    private static final Logger logger = LoggerFactory.getLogger(DynamicRouteManager.class);
//
//    private final CamelContext camelContext;
//    private final RouteConfigService routeConfigService;
//    private final InputRequestProcessor inputRequestProcessor;
//    private final OutputResponseProcessor outputResponseProcessor;
//    private final ExecutorService executorService;
//
//    // 维护每个PartnerConfig的路由映射：组合键 "partnerName-interfaceName" -> 路由ID
//    private final Map<String, Map<String, String>> partnerRouteMap = new ConcurrentHashMap<>();
//
//    // 缓存共享路由的目标URI映射
//    private final Map<String, String> routeTargetUriMap = new ConcurrentHashMap<>();
//
//    @Autowired
//    public DynamicRouteManager(CamelContext camelContext, RouteConfigService routeConfigService, InputRequestProcessor inputRequestProcessor, OutputResponseProcessor outputResponseProcessor, DynamicThreadPoolConfigService dynamicThreadPoolConfigService) {
//        this.camelContext = camelContext;
//        this.routeConfigService = routeConfigService;
//        this.inputRequestProcessor = inputRequestProcessor;
//        this.outputResponseProcessor = outputResponseProcessor;
//        this.executorService = dynamicThreadPoolConfigService.getExecutorService();
//    }
//
//    @Override
//    public void run(String... args) {
//        logger.info("Starting dynamic route loading...");
//        loadRoutes();
//    }
//
//    /**
//     * 异步加载所有PartnerConfig的路由
//     */
//    private void loadRoutes() {
//        try {
//            List<PartnerConfig> partnerConfigs = routeConfigService.getPartnerConfigs();
//            // 顺序执行每个 PartnerConfig 的处理逻辑
//            for (PartnerConfig config : partnerConfigs) {
//                addRoutesForPartner(config);
//            }
//
//            logger.info("All dynamic routes loaded successfully.");
//        } catch (Exception e) {
//            logger.error("Error while loading dynamic routes", e);
//        }
//    }
//
//    /**
//     * 为特定PartnerConfig添加所有路由
//     */
//    private void addRoutesForPartner(PartnerConfig partnerConfig) {
//        try {
//            Map<String, String> interfaceRouteMap = new ConcurrentHashMap<>();
//            for (InterfaceConfig interfaceConfig : partnerConfig.getInterfaces()) {
//                String routeId = addSingleRoute(partnerConfig, interfaceConfig);
//                interfaceRouteMap.put(getUniqueKey(partnerConfig.getPartnerCode(), interfaceConfig), routeId);
//            }
//            partnerRouteMap.put(partnerConfig.getPartnerCode(), interfaceRouteMap);
//            logger.info("Successfully added routes for partner: {}", partnerConfig.getPartnerCode());
//        } catch (Exception e) {
//            logger.error("Error adding routes for partner: {}", partnerConfig.getPartnerCode(), e);
//        }
//    }
//
//    /**
//     * 添加单个接口的路由
//     */
//    public void addRouteForInterface(PartnerConfig partnerConfig, InterfaceConfig interfaceConfig) {
//        try {
//            String routeId = addSingleRoute(partnerConfig, interfaceConfig);
//            partnerRouteMap.computeIfAbsent(partnerConfig.getPartnerCode(), key -> new ConcurrentHashMap<>()).put(getUniqueKey(partnerConfig.getPartnerCode(), interfaceConfig), routeId);
//            logger.info("Successfully added route for interface: {} in partner: {}", interfaceConfig.getInterfaceCode(), partnerConfig.getPartnerCode());
//        } catch (Exception e) {
//            logger.error("Error adding route for interface: {} in partner: {}", interfaceConfig.getInterfaceCode(), partnerConfig.getPartnerCode(), e);
//        }
//    }
//
//    /**
//     * 删除特定PartnerConfig的所有路由
//     */
//    public void clearRoutesForPartner(String getPartnerCode) {
//        Map<String, String> routeMap = partnerRouteMap.remove(getPartnerCode);
//        if (routeMap == null) {
//            logger.warn("No routes found for partner: {}", getPartnerCode);
//            return;
//        }
//        routeMap.values().forEach(this::removeSingleRoute);
//        logger.info("Successfully cleared all routes for partner: {}", getPartnerCode);
//    }
//
//    /**
//     * 删除特定接口的路由
//     */
//    public void removeRouteForInterface(String getPartnerCode, InterfaceConfig interfaceConfig) {
//        String uniqueKey = getUniqueKey(getPartnerCode, interfaceConfig);
//        Map<String, String> routeMap = partnerRouteMap.get(getPartnerCode);
//        if (routeMap == null || !routeMap.containsKey(uniqueKey)) {
//            logger.warn("No route found for interface: {} in partner: {}", interfaceConfig.getInterfaceCode(), getPartnerCode);
//            return;
//        }
//        removeSingleRoute(routeMap.remove(uniqueKey));
//        logger.info("Successfully removed route for interface: {} in partner: {}", interfaceConfig.getInterfaceCode(), getPartnerCode);
//    }
//
//    /**
//     * 添加单个路由的核心逻辑
//     */
//    private String addSingleRoute(PartnerConfig partnerConfig, InterfaceConfig interfaceConfig) {
//        String sharedRouteId = getUniqueKey(partnerConfig.getPartnerCode(), interfaceConfig);
//        try {
//            camelContext.addRoutes(new RouteBuilder() {
//                @Override
//                public void configure() {
//                    onException(Exception.class)
//                            .handled(true)
//                            .log("Error: ${exception.message}")
//                            .setBody(simple("{\"error\":\"${exception.message}\"}"))
//                            .setHeader("CamelHttpResponseCode", constant(HttpStatus.INTERNAL_SERVER_ERROR.value()));
//
//                    from(interfaceConfig.getSourceURI())
//                            .routeId(sharedRouteId)
//                            .log("Processing request for partner: ${header.partnerName}, Interface: ${header.interfaceName}")
//                            .setHeader("flowId", constant("dataConversionFlow"))
//                            .setHeader("interfaceConfig", constant(interfaceConfig))
//                            .process(inputRequestProcessor)
//                            .marshal().json(JsonLibrary.Jackson)
//                            .process(exchange -> setTargetUriHeader(exchange, interfaceConfig))
//                            .toD("${header.targetURI}?bridgeEndpoint=true&throwExceptionOnFailure=false")
//                            .process(outputResponseProcessor)
//                            .log("Response processed successfully.");
//                }
//            });
//
//            String routeId = partnerConfig.getPartnerCode() + "-" + interfaceConfig.getInterfaceCode();
//            routeTargetUriMap.put(routeId, interfaceConfig.getTargetURI());
//            logger.info("Successfully added route: {}", sharedRouteId);
//        } catch (Exception e) {
//            logger.error("Error adding route: {}", sharedRouteId, e);
//        }
//        return sharedRouteId;
//    }
//
//
//    /**
//     * 设置 targetURI 到 Exchange Header
//     */
//    private void setTargetUriHeader(Exchange exchange, InterfaceConfig interfaceConfig) {
//
//        if (interfaceConfig.getInterfaceType() == InterfaceType.OUTBOUND) {
//            String partnerCode = exchange.getIn().getHeader("partnerCode", String.class);
//            if (partnerCode == null) {
//                throw new IllegalArgumentException("Missing partnerCode in headers");
//            }
//            String routeId = partnerCode + "-" + interfaceConfig.getInterfaceCode();
//            String targetURI = routeTargetUriMap.get(routeId);
//            exchange.getIn().setHeader("targetURI", targetURI);
//            if (targetURI == null) {
//                throw new IllegalArgumentException("Missing target URI for route: " + routeId);
//            }
//        } else {
//            exchange.getIn().setHeader("targetURI", interfaceConfig.getTargetURI());
//        }
//    }
//
//    /**
//     * 生成唯一键
//     */
//    private String getUniqueKey(String partnerName, InterfaceConfig interfaceConfig) {
//        return interfaceConfig.getInterfaceType()== InterfaceType.OUTBOUND?
//                "shared-" + interfaceConfig.getInterfaceCode():partnerName+"-"+interfaceConfig.getInterfaceCode();
//    }
//
//    /**
//     * 删除单个路由
//     */
//    private void removeSingleRoute(String routeId) {
//        try {
//            camelContext.getRouteController().stopRoute(routeId);
//            camelContext.removeRoute(routeId);
//            logger.info("Successfully removed route: {}", routeId);
//        } catch (Exception e) {
//            logger.error("Error removing route: {}", routeId, e);
//        }
//    }
//}