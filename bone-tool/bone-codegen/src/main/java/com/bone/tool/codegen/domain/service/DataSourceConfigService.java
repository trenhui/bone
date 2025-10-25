package com.bone.tool.codegen.domain.service;

import com.bone.core.model.PageParam;
import com.bone.core.model.PageResult;
import com.bone.tool.codegen.application.dto.DataSourceConfigQueryRequest;
import com.bone.tool.codegen.application.dto.DataSourceConfigSaveRequest;
import com.bone.tool.codegen.domain.entity.Datasource;
import com.bone.tool.codegen.domain.repository.DataSourceConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Optional;

/**
 * 数据源配置领域服务实现类
 * <p>
 * 负责数据源配置相关的核心业务逻辑处理，包括数据源的创建、更新、删除和连接管理
 * </p>
 * 
 * @author bone-team
 */
@Service
@Validated
public class DataSourceConfigService {

    private static final Logger logger = LoggerFactory.getLogger(DataSourceConfigService.class);

    // 使用final修饰注入的字段确保不可变性
    private final DataSourceConfigRepository dataSourceConfigRepository;
    
    // 数据源连接缓存，避免频繁创建连接
    private final Map<Long, Connection> connectionCache = new ConcurrentHashMap<>();

    /**
     * 构造函数 - 依赖注入
     * 
     * @param dataSourceConfigRepository 数据源配置仓库
     * @throws IllegalArgumentException 当依赖项为null时抛出
     */
    @Autowired
    public DataSourceConfigService(DataSourceConfigRepository dataSourceConfigRepository) {
        // 首先进行依赖验证
        Assert.notNull(dataSourceConfigRepository, "数据源配置仓库不能为空");
        // 然后进行赋值
        this.dataSourceConfigRepository = dataSourceConfigRepository;
    }

    /**
     * 创建数据源配置
     *
     * @param createRequest 创建请求对象，包含配置信息
     * @return 创建的数据源配置ID
     * @throws IllegalArgumentException 当请求参数无效时抛出
     * @throws RuntimeException 当创建失败时抛出
     */
    public Long createDataSourceConfig(DataSourceConfigSaveRequest createRequest) {
        // 参数验证
        validateDataSourceConfigRequest(createRequest);

        logger.info("创建数据源配置，名称: {}, 类型: {}", createRequest.getName(), createRequest.getType());
        
        try {
            // 创建数据源配置实体
            Datasource config = mapToDatasourceEntity(createRequest);
            
            // 验证配置
            validateDatasourceEntity(config);
            
            // 测试连接是否有效
            testDatasourceConnection(config);
            
            // 保存配置
            Long id = dataSourceConfigRepository.save(config);
            logger.info("创建数据源配置成功，ID: {}", id);
            return id;
        } catch (IllegalArgumentException e) {
            logger.warn("创建数据源配置参数错误: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("创建数据源配置失败: {}", e.getMessage(), e);
            throw new RuntimeException("创建数据源配置失败: " + e.getMessage(), e);
        }
    }

    /**
     * 更新数据源配置
     *
     * @param updateRequest 更新请求对象，包含更新后的配置信息
     * @throws IllegalArgumentException 当请求参数无效时抛出
     * @throws RuntimeException 当数据源不存在或更新失败时抛出
     */
    public void updateDataSourceConfig(DataSourceConfigSaveRequest updateRequest) {
        // 参数验证
        validateDataSourceConfigRequest(updateRequest);
        validateIdParameter(updateRequest.getId());
        
        Long id = updateRequest.getId();
        logger.info("更新数据源配置，ID: {}", id);
        
        // 验证数据源配置存在
        validateDatasourceExists(id);
        
        try {
            // 创建并设置更新对象
            Datasource config = mapToDatasourceEntity(updateRequest);
            config.setId(id);
            
            // 验证配置
            validateDatasourceEntity(config);
            
            // 测试连接
            testDatasourceConnection(config);
            
            // 更新配置
            dataSourceConfigRepository.update(config);
            
            // 清除缓存的连接
            clearCachedConnection(id);
            
            logger.info("更新数据源配置成功，ID: {}", id);
        } catch (IllegalArgumentException e) {
            logger.warn("更新数据源配置参数错误: {}", e.getMessage());
            throw e;
        } catch (RuntimeException e) {
            logger.error("更新数据源配置失败，ID: {}: {}", id, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 删除数据源配置
     *
     * @param id 配置ID
     * @throws IllegalArgumentException 当ID无效时抛出
     * @throws RuntimeException 当数据源不存在或删除失败时抛出
     */
    public void deleteDataSourceConfig(Long id) {
        // 参数验证
        validateIdParameter(id);
        
        logger.info("删除数据源配置，ID: {}", id);
        
        // 校验数据源配置存在
        validateDatasourceExists(id);
        
        try {
            // 执行删除操作
            dataSourceConfigRepository.deleteById(id);
            
            // 清除缓存的连接
            clearCachedConnection(id);
            
            logger.info("删除数据源配置成功，ID: {}", id);
        } catch (Exception e) {
            logger.error("删除数据源配置失败，ID: {}: {}", id, e.getMessage(), e);
            throw new RuntimeException("删除数据源配置失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取数据源配置
     *
     * @param id 配置ID
     * @return 数据源配置
     * @throws IllegalArgumentException 当ID无效时抛出
     * @throws RuntimeException 当数据源不存在时抛出
     */
    public Datasource getDataSourceConfig(Long id) {
        // 参数验证
        validateIdParameter(id);
        
        logger.debug("获取数据源配置详情，ID: {}", id);
        
        // 获取数据源配置详情
        return dataSourceConfigRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("数据源配置不存在，ID: " + id));
    }

    /**
     * 分页获取数据源配置列表
     *
     * @param pageParam 分页参数
     * @return 数据源配置分页结果
     * @throws IllegalArgumentException 当分页参数无效时抛出
     */
    public PageResult<Datasource> getDataSourceConfigPage(PageParam pageParam) {
        validatePageParam(pageParam);
        return getDataSourceConfigPage(null, pageParam);
    }

    /**
     * 根据查询条件获取数据源配置列表
     *
     * @param request 查询条件
     * @return 数据源配置列表
     */
    public List<Datasource> getDataSourceConfigList(DataSourceConfigQueryRequest request) {
        logger.debug("根据查询条件获取数据源配置列表");
        return dataSourceConfigRepository.findAll();
    }

    /**
     * 获取所有数据源配置列表
     * 
     * @return 数据源配置列表
     */
    public List<Datasource> getDataSourceConfigList() {
        logger.debug("获取所有数据源配置列表");
        return dataSourceConfigRepository.findAll();
    }
    
    /**
     * 分页查询数据源配置
     * 
     * @param queryRequest 查询条件
     * @param pageParam 分页参数
     * @return 分页结果
     * @throws IllegalArgumentException 当分页参数无效时抛出
     */
    public PageResult<Datasource> getDataSourceConfigPage(DataSourceConfigQueryRequest queryRequest, PageParam pageParam) {
        validatePageParam(pageParam);
        
        logger.debug("分页查询数据源配置，页码: {}, 每页大小: {}", pageParam.getPageNo(), pageParam.getPageSize());
        
        // 获取所有数据源配置
        List<Datasource> allConfigs = getDataSourceConfigList(queryRequest);
        
        // 计算总数
        long total = allConfigs.size();
        
        // 执行分页
        List<Datasource> pagedConfigs = paginateList(allConfigs, pageParam);
        
        // 返回分页结果
        return PageResult.of(pagedConfigs, total, pageParam.getPageNo(), pageParam.getPageSize());
    }

    /**
     * 测试数据源连接
     * 
     * @param config 数据源配置
     * @return 是否连接成功
     * @throws RuntimeException 当连接测试失败时抛出
     */
    public boolean testConnection(Datasource config) {
        Assert.notNull(config, "数据源配置不能为空");
        
        try {
            return doTestConnection(config);
        } catch (SQLException e) {
            logger.error("数据源连接测试失败: {}", e.getMessage(), e);
            throw new RuntimeException("数据源连接测试失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 获取数据库连接
     * 用于实际的数据库操作
     * 
     * @param datasourceId 数据源ID
     * @return 数据库连接
     * @throws RuntimeException 当获取连接失败时抛出
     */
    public Connection getConnection(Long datasourceId) {
        validateIdParameter(datasourceId);
        
        try {
            // 先尝试从缓存获取
            Connection cachedConn = connectionCache.get(datasourceId);
            if (isValidConnection(cachedConn)) {
                logger.debug("使用缓存的数据库连接，数据源ID: {}", datasourceId);
                return cachedConn;
            }
            
            // 获取数据源配置
            Datasource config = getDataSourceConfig(datasourceId);
            
            // 创建新连接
            Connection conn = createNewConnection(config);
            
            // 缓存连接
            connectionCache.put(datasourceId, conn);
            
            logger.debug("创建新的数据库连接并缓存，数据源ID: {}", datasourceId);
            return conn;
        } catch (Exception e) {
            logger.error("获取数据库连接失败，数据源ID: {}", datasourceId, e);
            throw new RuntimeException("获取数据库连接失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 校验数据源配置是否存在
     * 
     * @param id 数据源配置ID
     * @throws RuntimeException 当数据源配置不存在时抛出异常
     */
    private void validateDatasourceExists(Long id) {
        dataSourceConfigRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("数据源配置不存在，ID: " + id));
    }
    
    /**
     * 清除缓存的连接
     * 
     * @param datasourceId 数据源ID
     */
    private void clearCachedConnection(Long datasourceId) {
        Assert.notNull(datasourceId, "数据源ID不能为空");
        
        Connection conn = connectionCache.remove(datasourceId);
        if (conn != null) {
            closeQuietly(conn);
            logger.debug("已关闭并移除缓存的数据库连接，数据源ID: {}", datasourceId);
        }
    }
    
    /**
     * 验证数据源配置
     * 
     * @param config 数据源配置对象
     * @throws IllegalArgumentException 当配置无效时抛出
     */
    private void validateDatasourceEntity(Datasource config) {
        Assert.notNull(config, "数据源配置不能为空");
        
        // 验证必要字段
        Assert.hasText(config.getConfigName(), "数据源名称不能为空");
        Assert.hasText(config.getType(), "数据源类型不能为空");
        Assert.hasText(config.getUrl(), "数据库连接URL不能为空");
        Assert.hasText(config.getUsername(), "用户名不能为空");
        Assert.hasText(config.getPassword(), "密码不能为空");
        Assert.hasText(config.getDriverClassName(), "驱动类名不能为空");
    }
    
    /**
     * 验证保存请求参数
     * 
     * @param request 保存请求对象
     * @throws IllegalArgumentException 当请求参数无效时抛出
     */
    private void validateDataSourceConfigRequest(DataSourceConfigSaveRequest request) {
        Assert.notNull(request, "请求参数不能为空");
        Assert.hasText(request.getName(), "数据源名称不能为空");
        Assert.hasText(request.getType(), "数据源类型不能为空");
        Assert.hasText(request.getUrl(), "数据库连接URL不能为空");
        Assert.hasText(request.getUsername(), "用户名不能为空");
        Assert.hasText(request.getPassword(), "密码不能为空");
        Assert.hasText(request.getDriverClassName(), "驱动类名不能为空");
    }
    
    /**
     * 验证ID参数
     * 
     * @param id 要验证的ID
     * @throws IllegalArgumentException 当ID无效时抛出
     */
    private void validateIdParameter(Long id) {
        Assert.notNull(id, "数据源ID不能为空");
        Assert.isTrue(id > 0, "数据源ID必须大于0");
    }
    
    /**
     * 验证分页参数
     * 
     * @param pageParam 分页参数
     * @throws IllegalArgumentException 当分页参数无效时抛出
     */
    private void validatePageParam(PageParam pageParam) {
        Assert.notNull(pageParam, "分页参数不能为空");
        Assert.isTrue(pageParam.getPageNo() > 0, "页码必须大于0");
        Assert.isTrue(pageParam.getPageSize() > 0, "每页大小必须大于0");
        Assert.isTrue(pageParam.getPageSize() <= 100, "每页大小不能超过100");
    }
    
    /**
     * 静默关闭连接
     * 
     * @param conn 数据库连接
     */
    private void closeQuietly(Connection conn) {
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException e) {
                logger.warn("关闭数据库连接失败", e);
            }
        }
    }
    
    /**
     * 将保存请求映射为实体对象
     * 
     * @param request 保存请求对象
     * @return 数据源配置实体
     */
    private Datasource mapToDatasourceEntity(DataSourceConfigSaveRequest request) {
        Datasource config = new Datasource();
        config.setConfigName(request.getName());
        config.setType(request.getType());
        config.setUrl(request.getUrl());
        config.setUsername(request.getUsername());
        config.setPassword(request.getPassword());
        config.setDriverClassName(request.getDriverClassName());
        return config;
    }
    
    /**
     * 执行连接测试的内部方法
     * 
     * @param config 数据源配置
     * @return 是否连接成功
     * @throws SQLException 当SQL执行出错时抛出
     * @throws IllegalArgumentException 当参数无效时抛出
     */
    private boolean doTestConnection(Datasource config) throws SQLException {
        String name = Optional.ofNullable(config.getConfigName()).orElse("未命名数据源");
        logger.debug("测试数据源连接: {}", name);
        
        Connection conn = null;
        try {
            // 获取连接信息
            String driverClassName = config.getDriverClassName();
            String url = config.getUrl();
            String username = config.getUsername();
            String password = config.getPassword();
            
            // 验证连接参数
            validateConnectionParams(driverClassName, url, username);
            
            // 加载驱动
            loadDriverClass(driverClassName);
            
            // 创建连接
            conn = DriverManager.getConnection(url, username, password);
            
            // 测试连接是否有效
            boolean isValid = conn.isValid(5); // 5秒超时
            
            if (isValid) {
                logger.info("数据源连接测试成功: {}", name);
            } else {
                logger.warn("数据源连接测试失败: {}", name);
            }
            
            return isValid;
        } finally {
            closeQuietly(conn);
        }
    }
    
    /**
     * 测试数据源连接并在失败时抛出异常
     * 
     * @param config 数据源配置
     * @throws RuntimeException 当连接测试失败时抛出
     */
    private void testDatasourceConnection(Datasource config) {
        if (!testConnection(config)) {
            throw new RuntimeException("数据源连接测试失败，请检查配置信息");
        }
    }
    
    /**
     * 验证连接参数
     * 
     * @param driverClassName 驱动类名
     * @param url 连接URL
     * @param username 用户名
     * @throws IllegalArgumentException 当参数无效时抛出
     */
    private void validateConnectionParams(String driverClassName, String url, String username) {
        Assert.hasText(driverClassName, "驱动类名不能为空");
        Assert.hasText(url, "连接URL不能为空");
        Assert.hasText(username, "用户名不能为空");
    }
    
    /**
     * 加载数据库驱动类
     * 
     * @param driverClassName 驱动类名
     * @throws RuntimeException 当驱动加载失败时抛出
     */
    private void loadDriverClass(String driverClassName) {
        try {
            Class.forName(driverClassName);
        } catch (ClassNotFoundException e) {
            logger.warn("驱动加载失败: {}", driverClassName, e);
            throw new RuntimeException("数据库驱动加载失败，请检查驱动类名: " + driverClassName, e);
        }
    }
    
    /**
     * 创建新的数据库连接
     * 
     * @param config 数据源配置
     * @return 数据库连接
     * @throws SQLException 当创建连接失败时抛出
     * @throws ClassNotFoundException 当驱动类找不到时抛出
     */
    private Connection createNewConnection(Datasource config) throws SQLException, ClassNotFoundException {
        // 加载驱动
        Class.forName(config.getDriverClassName());
        
        // 创建连接
        return DriverManager.getConnection(
            config.getUrl(), config.getUsername(), config.getPassword());
    }
    
    /**
     * 检查连接是否有效
     * 
     * @param connection 数据库连接
     * @return 连接是否有效
     */
    private boolean isValidConnection(Connection connection) {
        try {
            return connection != null && !connection.isClosed() && connection.isValid(2);
        } catch (SQLException e) {
            logger.warn("检查连接有效性失败", e);
            return false;
        }
    }
    
    /**
     * 对列表进行分页
     * 
     * @param <T> 列表元素类型
     * @param list 完整列表
     * @param pageParam 分页参数
     * @return 分页后的列表
     */
    private <T> List<T> paginateList(List<T> list, PageParam pageParam) {
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }
        
        int start = (pageParam.getPageNo() - 1) * pageParam.getPageSize();
        int end = Math.min(start + pageParam.getPageSize(), list.size());
        
        if (start >= list.size()) {
            return Collections.emptyList();
        }
        
        return list.subList(start, end);
    }
}