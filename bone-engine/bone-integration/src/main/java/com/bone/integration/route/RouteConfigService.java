//package com.bone.lowcode.integration.route;
//
//import com.bone.lowcode.integration.config.PartnerConfig;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.core.io.Resource;
//import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
//import org.springframework.stereotype.Service;
//
//import java.io.IOException;
//import java.io.InputStream;
//import java.util.ArrayList;
//import java.util.List;
//import java.util.Objects;
//
//@Service
//public class RouteConfigService {
//
//    private static final Logger logger = LoggerFactory.getLogger(RouteConfigService.class);
//    private static final String INTEGRATION_CONFIG_PATH = "classpath:partner/*.yaml";
//    private final ObjectMapper yamlMapper;
//    private final PathMatchingResourcePatternResolver resourceResolver;
//
//    public RouteConfigService() {
//        this.yamlMapper = new ObjectMapper(new YAMLFactory());
//        this.resourceResolver = new PathMatchingResourcePatternResolver();
//    }
//
//    /**
//     * Loads all YAML files from the 'integration' directory and parses them into PartnerConfig objects.
//     *
//     * @return a list of parsed PartnerConfig objects
//     */
//    public List<PartnerConfig> getPartnerConfigs() {
//        List<PartnerConfig> routeConfigs = new ArrayList<>();
//
//        try {
//            // Get all YAML resources from the specified path
//            Resource[] resources = loadYamlResources();
//            if (resources.length == 0) {
//                logger.warn("No YAML configuration files found in the integration directory.");
//                return routeConfigs;
//            }
//
//            // Process each YAML file
//            for (Resource resource : resources) {
//                loadAndParseConfig(resource, routeConfigs);
//            }
//
//        } catch (IOException e) {
//            logger.error("Error while accessing the YAML configuration files.", e);
//        }
//
//        logger.info("Loaded {} integration configurations.", routeConfigs.size());
//        return routeConfigs;
//    }
//
//    /**
//     * Loads YAML resources using the resource resolver.
//     *
//     * @return an array of Resource objects representing the YAML files.
//     * @throws IOException if there's an error accessing the resources
//     */
//    private Resource[] loadYamlResources() throws IOException {
//        logger.debug("Loading YAML files from path: {}", INTEGRATION_CONFIG_PATH);
//        return resourceResolver.getResources(INTEGRATION_CONFIG_PATH);
//    }
//
//    /**
//     * Loads and parses a single YAML configuration file and adds it to the list.
//     *
//     * @param resource     the YAML resource to be parsed
//     * @param routeConfigs the list to which parsed configurations are added
//     */
//    private void loadAndParseConfig(Resource resource, List<PartnerConfig> routeConfigs) {
//        Objects.requireNonNull(resource, "Resource cannot be null");
//
//        try (InputStream inputStream = resource.getInputStream()) {
//            logger.debug("Processing YAML file: {}", resource.getFilename());
//
//            // Parse the YAML into PartnerConfig object
//            PartnerConfig config = yamlMapper.readValue(inputStream, PartnerConfig.class);
//            routeConfigs.add(config);
//
//            logger.info("Successfully loaded config for partner: {}", config.getPartnerName());
//
//        } catch (IOException e) {
//            logger.error("Error parsing YAML file: {}", resource.getFilename(), e);
//        }
//    }
//}