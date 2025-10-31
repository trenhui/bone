package com.bone.metadata.sdk.sql.proxy;

import com.bone.metadata.sdk.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.context.annotation.ScannedGenericBeanDefinition;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.type.ClassMetadata;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

class ClassPathRepositoryScanner extends ClassPathBeanDefinitionScanner {
    private static final Map<String, Class<?>[]> GENERIC_CACHE = new ConcurrentHashMap<>();
    private static final Logger logger = LoggerFactory.getLogger(ClassPathRepositoryScanner.class);

    public ClassPathRepositoryScanner(BeanDefinitionRegistry registry) {
        super(registry, false);
    }

    protected void registerFilters() {
        // 放宽过滤条件：只要接口名以"Repository"结尾
        addIncludeFilter((metadataReader, factory) -> {
            String className = metadataReader.getClassMetadata().getClassName();
            boolean isInterface = metadataReader.getClassMetadata().isInterface();
            boolean isRepository = className.endsWith("Repository");

            if (isInterface && isRepository) {
                logger.debug("Found potential Repository interface: {}", className);
                return true;
            }
            return false;
        });

        // 添加排除过滤器，排除非接口和非Repository类
        addExcludeFilter((metadataReader, factory) -> !metadataReader.getClassMetadata().isInterface());
    }

    @Override
    protected Set<BeanDefinitionHolder> doScan(String... basePackages) {
        logger.info("Scanning for Repository interfaces in packages: {}", Arrays.toString(basePackages));

        // 先调用父类的doScan方法
        Set<BeanDefinitionHolder> holders = super.doScan(basePackages);

        // 如果没有找到任何接口，尝试更宽松的扫描
        if (holders.isEmpty()) {
            logger.warn("No Repository interfaces found with standard scanning. Trying alternative approach...");
            holders = alternativeScan(basePackages);
        }

        logger.info("Found {} Repository interfaces", holders.size());
        holders.forEach(holder ->
                logger.info("Repository interface: {}", holder.getBeanDefinition().getBeanClassName()));

        // 处理找到的Bean定义
        holders.forEach(this::processBeanDefinition);
        return holders;
    }

    /**
     * 备用的扫描方法，使用更宽松的条件
     */
    private Set<BeanDefinitionHolder> alternativeScan(String... basePackages) {
        Set<BeanDefinitionHolder> holders = new HashSet<>();

        for (String basePackage : basePackages) {
            try {
                // 使用资源加载器获取包下的所有类
                String packageSearchPath = "classpath*:" +
                        basePackage.replace('.', '/') + "/**/*.class";

                Resource[] resources = ((ResourcePatternResolver) getResourceLoader())
                        .getResources(packageSearchPath);

                logger.info("Found {} resources in package {}", resources.length, basePackage);

                for (Resource resource : resources) {
                    if (resource.isReadable()) {
                        try {
                            MetadataReader metadataReader = getMetadataReaderFactory()
                                    .getMetadataReader(resource);

                            ClassMetadata metadata = metadataReader.getClassMetadata();
                            if (metadata.isInterface() && metadata.getClassName().endsWith("Repository")) {
                                logger.info("Alternative scan found Repository: {}", metadata.getClassName());

                                // 创建Bean定义
                                ScannedGenericBeanDefinition definition =
                                        new ScannedGenericBeanDefinition(metadataReader);
                                definition.setResource(resource);
                                definition.setSource(resource);

                                // 创建Bean定义持有者
                                BeanDefinitionHolder holder = new BeanDefinitionHolder(
                                        definition,
                                        StringUtils.uncapitalize(metadata.getClassName().substring(
                                                metadata.getClassName().lastIndexOf('.') + 1))
                                );

                                holders.add(holder);
                            }
                        } catch (Throwable ex) {
                            logger.debug("Failed to read class: {}", resource.getDescription(), ex);
                        }
                    }
                }
            } catch (IOException ex) {
                logger.error("Failed to scan package: {}", basePackage, ex);
            }
        }

        return holders;
    }

    private void processBeanDefinition(BeanDefinitionHolder holder) {
        GenericBeanDefinition definition = (GenericBeanDefinition) holder.getBeanDefinition();
        String repoInterfaceName = definition.getBeanClassName();
        try {
            Class<?> repoInterface = Class.forName(repoInterfaceName);

            // 检查是否直接或间接实现了Repository接口
            boolean isRepository = false;
            Class<?>[] interfaces = repoInterface.getInterfaces();
            for (Class<?> iface : interfaces) {
                if (iface.getName().equals(Repository.class.getName())) {
                    isRepository = true;
                    break;
                }
            }

            // 如果没有直接实现，检查是否继承自其他实现了Repository的接口
            if (!isRepository) {
                isRepository = checkIndirectRepositoryImplementation(repoInterface);
            }

            if (!isRepository) {
                logger.warn("Interface {} is not a Repository (does not implement Repository interface)",
                        repoInterfaceName);
                return;
            }

            Class<?>[] genericTypes = resolveGenericTypes(repoInterface);
            if (genericTypes == null || genericTypes.length != 2) {
                logger.error("Invalid Repository interface: {} (must extend Repository<T, ID>)", repoInterfaceName);
                return;
            }

            definition.getConstructorArgumentValues().addGenericArgumentValue(repoInterface);
            definition.getConstructorArgumentValues().addGenericArgumentValue(genericTypes[0]);
            definition.getConstructorArgumentValues().addGenericArgumentValue(genericTypes[1]);
            definition.setBeanClass(RepositoryFactoryBean.class);
            definition.setAutowireMode(AbstractBeanDefinition.AUTOWIRE_BY_TYPE);
            definition.setLazyInit(true);

            logger.info("Registered Repository: {} (entity: {}, id: {})",
                    repoInterfaceName, genericTypes[0].getSimpleName(), genericTypes[1].getSimpleName());
        } catch (ClassNotFoundException e) {
            logger.error("Failed to load Repository interface: {}", repoInterfaceName, e);
        }
    }

    /**
     * 检查接口是否间接实现了Repository接口
     */
    private boolean checkIndirectRepositoryImplementation(Class<?> repoInterface) {
        // 检查所有父接口
        for (Class<?> parentInterface : repoInterface.getInterfaces()) {
            if (parentInterface.getName().equals(Repository.class.getName())) {
                return true;
            }

            // 递归检查父接口的父接口
            if (checkIndirectRepositoryImplementation(parentInterface)) {
                return true;
            }
        }
        return false;
    }

    private Class<?>[] resolveGenericTypes(Class<?> repoInterface) {
        return GENERIC_CACHE.computeIfAbsent(repoInterface.getName(), key -> {
            // 检查直接实现的泛型接口
            for (Type genericInterface : repoInterface.getGenericInterfaces()) {
                if (genericInterface instanceof ParameterizedType pt) {
                    if (pt.getRawType().getTypeName().equals(Repository.class.getName())) {
                        Type[] actualTypes = pt.getActualTypeArguments();
                        if (actualTypes.length == 2 && actualTypes[0] instanceof Class && actualTypes[1] instanceof Class) {
                            return new Class<?>[]{(Class<?>) actualTypes[0], (Class<?>) actualTypes[1]};
                        }
                    }
                }
            }

            // 如果没有直接实现，检查父接口
            for (Class<?> parentInterface : repoInterface.getInterfaces()) {
                Class<?>[] parentTypes = resolveGenericTypes(parentInterface);
                if (parentTypes != null) {
                    return parentTypes;
                }
            }

            return null;
        });
    }
}