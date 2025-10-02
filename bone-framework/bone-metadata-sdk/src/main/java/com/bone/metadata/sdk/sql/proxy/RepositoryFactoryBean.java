package com.bone.metadata.sdk.sql.proxy;

import com.bone.metadata.sdk.BaseRepository;
import com.bone.metadata.sdk.domain.annotation.Param;
import com.bone.metadata.sdk.domain.annotation.SqlType;
import com.bone.metadata.sdk.domain.query.CompiledQuery;
import com.bone.metadata.sdk.extension.ExtensionCoordinator;
import com.bone.metadata.sdk.query.SqlBuilder;
import com.bone.metadata.sdk.sql.executor.SqlExecutor;
import com.bone.metadata.sdk.sql.processor.ProcessedSql;
import com.bone.metadata.sdk.sql.processor.SqlProcessor;
import com.bone.metadata.sdk.sql.processor.SqlProcessorFactory;
import com.bone.metadata.sdk.sql.template.SqlFragmentLoader;
import com.bone.metadata.sdk.sql.template.SqlTemplate;
import com.bone.metadata.sdk.sql.template.SqlTemplateLoader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.GenericTypeResolver;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Proxy;
import java.time.temporal.Temporal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Repository 代理工厂 Bean，基于业界最佳实践实现
 * 支持注解和类路径 SQL 模板加载，集成多源回退机制
 */
@Slf4j
public class RepositoryFactoryBean<T, E, ID> implements FactoryBean<T>, InitializingBean, ApplicationContextAware {
    private static final String TEMPLATE_ID_FORMAT = "%s.%s";
    private static final int MAX_SQL_LOG_LENGTH = 500;

    private final Class<T> repositoryInterface;
    private final Class<E> entityClass;
    private final Class<ID> idClass;

    private ApplicationContext applicationContext;
    private SqlTemplateLoader sqlTemplateLoader;
    private SqlExecutor sqlExecutor;
    private SqlProcessorFactory sqlProcessorFactory;
    private SqlBuilder sqlBuilder;
    private ExtensionCoordinator extensionCoordinator;
    private SqlFragmentLoader sqlFragmentLoader;

    private final ConcurrentHashMap<Method, MethodHandler> methodHandlers = new ConcurrentHashMap<>(64);
    private final ConcurrentHashMap<Method, Class<?>> listElementTypeCache = new ConcurrentHashMap<>(32);
    private final ConcurrentHashMap<Method, Class<?>> optionalElementTypeCache = new ConcurrentHashMap<>(32);
    private final ConcurrentHashMap<Method, MethodHandle> defaultMethodHandles = new ConcurrentHashMap<>(16);
    private final Set<MethodSignature> baseRepoMethodSigs = ConcurrentHashMap.newKeySet();
    private final ParameterNameDiscoverer paramNameDiscoverer = new DefaultParameterNameDiscoverer();

    private volatile T proxy;

    public RepositoryFactoryBean(Class<T> repositoryInterface, Class<E> entityClass, Class<ID> idClass) {
        this.repositoryInterface = Objects.requireNonNull(repositoryInterface, "repositoryInterface must not be null");
        this.entityClass = Objects.requireNonNull(entityClass, "entityClass must not be null");
        this.idClass = Objects.requireNonNull(idClass, "idClass must not be null");

        // 预加载 BaseRepository 方法签名
        ReflectionUtils.doWithMethods(BaseRepository.class,
                method -> baseRepoMethodSigs.add(MethodSignature.of(method)));
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        initializeDependencies();
        initializeHandlers();
        this.proxy = createProxy();

        log.info("Repository代理初始化完成: {}", repositoryInterface.getName());
    }

    /**
     * 初始化依赖组件
     */
    private void initializeDependencies() {
        this.sqlExecutor = getBean(SqlExecutor.class, sqlExecutor);
        this.sqlTemplateLoader = getBean(SqlTemplateLoader.class, sqlTemplateLoader);
        this.sqlProcessorFactory = getBean(SqlProcessorFactory.class, sqlProcessorFactory);
        this.sqlBuilder = getBean(SqlBuilder.class, sqlBuilder);
        this.extensionCoordinator = getBean(ExtensionCoordinator.class, extensionCoordinator);
        this.sqlFragmentLoader = getBean(SqlFragmentLoader.class, sqlFragmentLoader);

        Assert.notNull(sqlExecutor, "SqlExecutor 不能为空");
        Assert.notNull(sqlTemplateLoader, "SqlTemplateLoader 不能为空");
        Assert.notNull(sqlProcessorFactory, "SqlProcessorFactory 不能为空");
        Assert.notNull(sqlBuilder, "SqlBuilder 不能为空");
        Assert.notNull(extensionCoordinator, "ExtensionCoordinator 不能为空");
        Assert.notNull(sqlFragmentLoader, "SqlFragmentLoader 不能为空");
    }

    private <B> B getBean(Class<B> beanType, B existingBean) {
        return existingBean != null ? existingBean : applicationContext.getBean(beanType);
    }

    /**
     * 初始化方法处理器
     */
    private void initializeHandlers() throws RuntimeException {
        BaseRepository<?, ?> baseRepo = createBaseRepository();
        sqlFragmentLoader.cacheSqlFragmentsForInterface(repositoryInterface);

        // 直接流式处理，排除 BaseRepository 的 public 方法
        ReflectionUtils.doWithMethods(repositoryInterface, method -> {
            if (shouldSkipMethod(method)) return;

            try {
                MethodHandler handler = createMethodHandler(method, baseRepo);
                methodHandlers.put(method, handler);
                log.debug("初始化 {} 处理器: {}.{}",
                        handler.getClass().getSimpleName(),
                        repositoryInterface.getName(), method.getName());
            } catch (Exception e) {
                throw new RuntimeException("初始化方法处理器失败: " + method, e);
            }
        });
    }

    private boolean shouldSkipMethod(Method method) {
        return method.getDeclaringClass() == Object.class ||
                isBaseRepositoryPublicMethod(method);
    }

    private boolean isBaseRepositoryPublicMethod(Method method) {
        return baseRepoMethodSigs.contains(MethodSignature.of(method));
    }

    private MethodHandler createMethodHandler(Method method, BaseRepository<?, ?> baseRepo) throws Exception {
        if (method.isDefault()) {
            return new DefaultMethodHandler(lookupDefaultMethodHandle(method));
        }

        String templateId = String.format(TEMPLATE_ID_FORMAT, repositoryInterface.getName(), method.getName());
        SqlTemplate sqlTemplate = sqlTemplateLoader.loadTemplate(method, templateId);
        resolveAndCacheElementType(method);
        return new SqlMethodHandler(sqlTemplate, method);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private BaseRepository<?, ?> createBaseRepository() {
        return new BaseRepository(sqlBuilder, sqlExecutor, entityClass, extensionCoordinator) {
        };
    }

    @SuppressWarnings("unchecked")
    private T createProxy() {
        ClassLoader cl = ClassUtils.getDefaultClassLoader();
        return (T) Proxy.newProxyInstance(
                (cl != null ? cl : repositoryInterface.getClassLoader()),
                new Class<?>[]{repositoryInterface},
                new RepositoryInvocation());
    }

    private Method findBaseRepositoryMethod(Method interfaceMethod) throws NoSuchMethodException {
        return BaseRepository.class.getMethod(interfaceMethod.getName(), interfaceMethod.getParameterTypes());
    }

    /**
     * 方法调用处理器
     */
    private final class RepositoryInvocation implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getDeclaringClass() == Object.class) {
                return handleObjectMethod(proxy, method, args);
            }

            MethodHandler handler = methodHandlers.computeIfAbsent(method, this::createHandlerForMissingMethod);
            return handler.invoke(args != null ? args : new Object[0]);
        }

        private MethodHandler createHandlerForMissingMethod(Method method) {
            try {
                // 运行时处理 BaseRepository 方法
                if (isBaseRepositoryPublicMethod(method)) {
                    Method baseMethod = findBaseRepositoryMethod(method);
                    return new DelegateMethodHandler(toHandle(baseMethod, createBaseRepository()));
                }
                return createMethodHandler(method, createBaseRepository());
            } catch (Exception e) {
                throw new RuntimeException("创建方法处理器失败: " + method, e);
            }
        }

        private Object handleObjectMethod(Object proxy, Method method, Object[] args) {
            String name = method.getName();
            switch (name) {
                case "toString":
                    return repositoryInterface.getName() + " proxy";
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "equals":
                    return proxy == (args != null && args.length == 1 ? args[0] : null);
                default:
                    throw new UnsupportedOperationException("不支持的 Object 方法: " + name);
            }
        }
    }

    /**
     * 方法处理器接口
     */
    private interface MethodHandler {
        Object invoke(Object[] args) throws Throwable;
    }

    /**
     * 默认方法处理器
     */
    private static final class DefaultMethodHandler implements MethodHandler {
        private final MethodHandle handle;

        DefaultMethodHandler(MethodHandle handle) {
            this.handle = handle;
        }

        @Override
        public Object invoke(Object[] args) throws Throwable {
            return handle.invokeWithArguments(args);
        }
    }

    /**
     * 委托方法处理器
     */
    private static final class DelegateMethodHandler implements MethodHandler {
        private final MethodHandle handle;

        DelegateMethodHandler(MethodHandle handle) {
            this.handle = handle;
        }

        @Override
        public Object invoke(Object[] args) throws Throwable {
            return handle.invokeWithArguments(args);
        }
    }

    /**
     * SQL 方法处理器
     */
    private final class SqlMethodHandler implements MethodHandler {
        private final SqlTemplate sqlTemplate;
        private final Method method;

        SqlMethodHandler(SqlTemplate sqlTemplate, Method method) {
            this.sqlTemplate = sqlTemplate;
            this.method = method;
        }

        @Override
        public Object invoke(Object[] args) {
            Map<String, Object> params = extractParameters(method, args);
            SqlProcessor processor = sqlProcessorFactory.getProcessor(sqlTemplate.getSqlTemplateType());
            ProcessedSql processed = processor.process(sqlTemplate.getId(), sqlTemplate.getSql(), params);

            CompiledQuery cq = createCompiledQuery(processed);
            SqlType sqlType = sqlTemplate.getSqlType();
            try {
                return executeQuery(sqlType, cq);
            } catch (RuntimeException ex) {
                throw createExecutionException(processed.getSql(), ex);
            }
        }

        private CompiledQuery createCompiledQuery(ProcessedSql processed) {
            Map<String, Object> queryParams = new HashMap<>(processed.getEffectiveParams());
            queryParams.remove("_repoClass");
            return new CompiledQuery(processed.getSql(), queryParams);
        }

        private Object executeQuery(SqlType sqlType, CompiledQuery query) {
            switch (sqlType) {
                case SELECT, CTE:
                    return handleSelect(query);
                case INSERT:
                    return handleInsert(query);
                case UPDATE:
                case DELETE:
                    return sqlExecutor.executeUpdate(query);
                default:
                    throw new UnsupportedOperationException("不支持的 SQL 类型: " + sqlType);
            }
        }

        private Object handleSelect(CompiledQuery query) {
            Class<?> returnType = method.getReturnType();

            if (List.class.equals(returnType)) {
                return executeListQuery(query);
            }
            if (Optional.class.equals(returnType)) {
                return executeOptionalQuery(query);
            }
            if (isSimpleType(returnType)) {
                return sqlExecutor.queryForObject(query, returnType);
            }
            return executeSingleResultQuery(query, returnType);
        }

        private Object executeListQuery(CompiledQuery query) {
            Class<?> elementType = listElementTypeCache.get(method);
            return isSimpleType(elementType)
                    ? sqlExecutor.queryForList(query.getSql(), query.getParameters(), elementType)
                    : sqlExecutor.executeQuery(query, elementType);
        }

        private Object executeOptionalQuery(CompiledQuery query) {
            Class<?> elementType = optionalElementTypeCache.get(method);
            List<?> results = sqlExecutor.executeQuery(query, elementType);
            if (results.isEmpty()) return Optional.empty();
            if (results.size() == 1) return Optional.of(results.get(0));
            throw new IncorrectResultSizeDataAccessException(1, results.size());
        }

        private Object executeSingleResultQuery(CompiledQuery query, Class<?> returnType) {
            List<?> results = sqlExecutor.executeQuery(query, returnType);
            if (results.isEmpty()) return null;
            if (results.size() == 1) return results.get(0);
            throw new IncorrectResultSizeDataAccessException(1, results.size());
        }

        private Object handleInsert(CompiledQuery query) {
            Class<?> returnType = method.getReturnType();
            if (Long.class.equals(returnType) || long.class.equals(returnType)) {
                return sqlExecutor.executeInsert(query, entityClass);
            }
            return sqlExecutor.executeUpdate(query);
        }

        private IllegalStateException createExecutionException(String sql, RuntimeException ex) {
            String sqlHead = sql.trim().replaceAll("[\\r\\n]+", " ");
            sqlHead = sqlHead.substring(0, Math.min(sqlHead.length(), MAX_SQL_LOG_LENGTH));
            return new IllegalStateException(
                    String.format("SQL执行失败: templateId=%s, type=%s, sqlHead=%s",
                            sqlTemplate.getId(), sqlTemplate.getSqlType(), sqlHead), ex);
        }
    }

    private void resolveAndCacheElementType(Method method) {
        if (List.class.equals(method.getReturnType())) {
            listElementTypeCache.computeIfAbsent(method,
                    m -> GenericTypeResolver.resolveReturnTypeArgument(m, List.class));
        } else if (Optional.class.equals(method.getReturnType())) {
            optionalElementTypeCache.computeIfAbsent(method,
                    m -> GenericTypeResolver.resolveReturnTypeArgument(m, Optional.class));
        }
    }

    private Map<String, Object> extractParameters(Method method, Object[] args) {
        Map<String, Object> params = new LinkedHashMap<>();
        Parameter[] parameters = method.getParameters();
        String[] discoveredNames = paramNameDiscoverer.getParameterNames(method);

        for (int i = 0; i < parameters.length; i++) {
            String name = getParameterName(parameters[i], discoveredNames, i);
            params.put(name, args[i]);
        }

        params.put("_repoClass", repositoryInterface.getName());
        return params;
    }

    private String getParameterName(Parameter parameter, String[] discoveredNames, int index) {
        Param paramAnnotation = parameter.getAnnotation(Param.class);
        if (paramAnnotation != null && !paramAnnotation.value().isBlank()) {
            return paramAnnotation.value();
        }
        return discoveredNames != null && discoveredNames.length > index && discoveredNames[index] != null
                ? discoveredNames[index]
                : "arg" + index;
    }

    private static boolean isSimpleType(Class<?> type) {
        return type.isPrimitive()
                || Number.class.isAssignableFrom(type)
                || CharSequence.class.isAssignableFrom(type)
                || Boolean.class.equals(type)
                || Date.class.isAssignableFrom(type)
                || Temporal.class.isAssignableFrom(type)
                || type == Object.class;
    }

    private MethodHandle toHandle(Method method, Object target) throws NoSuchMethodException, IllegalAccessException {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            return lookup.unreflect(method).bindTo(target);
        } catch (IllegalAccessException e) {
            return MethodHandles.lookup().bind(new ReflectFallback(target, method),
                    ReflectFallback.INVOKE, MethodType.methodType(Object.class, Object[].class));
        }
    }

    private MethodHandle lookupDefaultMethodHandle(Method method) {
        return defaultMethodHandles.computeIfAbsent(method, m -> {
            try {
                Class<?> declaringClass = m.getDeclaringClass();
                MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(declaringClass, MethodHandles.lookup());
                return lookup.findSpecial(declaringClass, m.getName(),
                        MethodType.methodType(m.getReturnType(), m.getParameterTypes()),
                        declaringClass);
            } catch (Throwable t) {
                throw new IllegalStateException("无法解析默认方法句柄: " + m, t);
            }
        });
    }

    private static final class ReflectFallback {
        static final String INVOKE = "invoke";
        private final Object target;
        private final Method method;

        ReflectFallback(Object target, Method method) {
            this.target = target;
            this.method = method;
        }

        public Object invoke(Object[] args) throws Throwable {
            return method.invoke(target, args);
        }
    }

    /**
     * 方法签名类
     */
    private static final class MethodSignature {
        private final String name;
        private final List<Class<?>> parameterTypes;

        static MethodSignature of(Method method) {
            return new MethodSignature(method.getName(), Arrays.asList(resolveParameterTypes(method)));
        }

        private MethodSignature(String name, List<Class<?>> parameterTypes) {
            this.name = name;
            this.parameterTypes = parameterTypes;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof MethodSignature)) return false;
            MethodSignature that = (MethodSignature) o;
            return Objects.equals(name, that.name) && Objects.equals(parameterTypes, that.parameterTypes);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, parameterTypes);
        }

        private static Class<?>[] resolveParameterTypes(Method method) {
            if (method.isBridge()) {
                try {
                    method = method.getDeclaringClass().getDeclaredMethod(method.getName(), method.getParameterTypes());
                } catch (NoSuchMethodException ignored) {
                }
            }
            return method.getParameterTypes();
        }
    }

    @Override
    public T getObject() {
        return proxy;
    }

    @Override
    public Class<?> getObjectType() {
        return repositoryInterface;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    // Setter 方法
    public void setSqlTemplateLoader(SqlTemplateLoader sqlTemplateLoader) {
        this.sqlTemplateLoader = sqlTemplateLoader;
    }

    public void setSqlExecutor(SqlExecutor sqlExecutor) {
        this.sqlExecutor = sqlExecutor;
    }

    public void setSqlProcessorFactory(SqlProcessorFactory sqlProcessorFactory) {
        this.sqlProcessorFactory = sqlProcessorFactory;
    }

    public void setSqlBuilder(SqlBuilder sqlBuilder) {
        this.sqlBuilder = sqlBuilder;
    }

    public void setExtensionCoordinator(ExtensionCoordinator extensionCoordinator) {
        this.extensionCoordinator = extensionCoordinator;
    }
}