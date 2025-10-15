package com.bone.tool.codegen.domain.service;

import com.bone.core.model.PageParam;
import com.bone.core.model.PageResult;
import com.bone.tool.codegen.application.dto.DataSourceConfigQueryRequest;
import com.bone.tool.codegen.application.dto.DataSourceConfigSaveRequest;
import com.bone.tool.codegen.domain.entity.DataSourceConfig;
import com.bone.tool.codegen.domain.repository.DataSourceConfigRepository;
import com.bone.metadata.sdk.query.criteria.Criteria;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import org.springframework.beans.factory.annotation.Autowired;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


/**
 * 数据源配置 领域服务实现类
 * <p>
 * 负责数据源配置相关的核心业务逻辑处理
 */
@Service
@Validated
public class DataSourceConfigService {

    private static final Logger log = LoggerFactory.getLogger(DataSourceConfigService.class);

    @Autowired
    private DataSourceConfigRepository dataSourceConfigRepository;
    
    // 数据源连接缓存，避免频繁创建连接
    private final Map<Long, Connection> connectionCache = new ConcurrentHashMap<>();

    /**
     * 创建数据源配置
     *
     * @param createReqVO 创建信息
     * @return 配置ID
     */
    public Long createDataSourceConfig(DataSourceConfigSaveRequest createReqVO) {
        // 转换为领域实体并保存
        DataSourceConfig config = new DataSourceConfig();
        config.setName(createReqVO.getName());
        config.setUrl(createReqVO.getUrl());
        config.setUsername(createReqVO.getUsername());
        config.setPassword(createReqVO.getPassword());
        
        // 验证配置
        validateDataSourceConfig(config);
        
        // 测试连接
        if (!testConnection(config)) {
            throw new RuntimeException("数据源连接测试失败，请检查配置是否正确");
        }
        
        // 使用Repository的save方法
        Long id = dataSourceConfigRepository.save(config);
        log.info("创建数据源配置成功: {}", config.getName());
        return id;
    }

    /**
     * 更新数据源配置
     *
     * @param updateReqVO 更新信息
     */
    public void updateDataSourceConfig(DataSourceConfigSaveRequest updateReqVO) {
        // 获取配置ID并校验存在性
        Long id = updateReqVO.getId();
        validateDataSourceConfigExists(id);
        
        // 转换为领域实体并更新
        DataSourceConfig config = new DataSourceConfig();
        config.setId(id);
        config.setName(updateReqVO.getName());
        config.setUrl(updateReqVO.getUrl());
        config.setUsername(updateReqVO.getUsername());
        config.setPassword(updateReqVO.getPassword());
        
        // 验证配置
        validateDataSourceConfig(config);
        
        // 测试连接
        if (!testConnection(config)) {
            throw new RuntimeException("数据源连接测试失败，请检查配置是否正确");
        }
        
        // 使用Repository的update方法
        dataSourceConfigRepository.update(config);
        
        // 清除缓存的连接
        clearCachedConnection(id);
        
        log.info("更新数据源配置成功: {}", config.getName());
    }
    


    /**
     * 删除数据源配置
     *
     * @param id 配置ID
     */
    public void deleteDataSourceConfig(Long id) {
        // 校验数据源配置存在
        validateDataSourceConfigExists(id);
        
        // 执行删除操作
        dataSourceConfigRepository.deleteById(id);
        
        // 清除缓存的连接
        clearCachedConnection(id);
        
        log.info("删除数据源配置成功: {}", id);
    }

    /**
     * 获取数据源配置
     *
     * @param id 配置ID
     * @return 数据源配置
     */
    public DataSourceConfig getDataSourceConfig(Long id) {
        // 获取数据源配置详情
        DataSourceConfig config = dataSourceConfigRepository.findById(id);
        if (config == null) {
            throw new RuntimeException("数据源配置不存在");
        }
        return config;
    }

    /**
     * 分页获取数据源配置列表
     *
     * @param pageParam 分页参数
     * @return 数据源配置分页结果
     */
    public PageResult<DataSourceConfig> getDataSourceConfigPage(PageParam pageParam) {
        return getDataSourceConfigPage(null, pageParam);
    }

    /**
     * 根据查询条件获取数据源配置列表
     *
     * @param request 查询条件
     * @return 数据源配置列表
     */
    public List<DataSourceConfig> getDataSourceConfigList(DataSourceConfigQueryRequest request) {
        // 简化实现，直接使用空Criteria返回所有数据
        Criteria<DataSourceConfig> criteria = Criteria.<DataSourceConfig>builder();
        return dataSourceConfigRepository.findByCriteria(criteria);
    }

    /**
     * 获取所有数据源配置列表
     * 
     * @return 数据源配置列表
     */
    public List<DataSourceConfig> getDataSourceConfigList() {
        // 使用Criteria获取所有数据源配置
        Criteria<DataSourceConfig> criteria = Criteria.<DataSourceConfig>builder();
        return dataSourceConfigRepository.findByCriteria(criteria);
    }
    
    /**
     * 获取所有数据源配置列表（与getDataSourceConfigList相同，提供别名以兼容接口调用）
     * 
     * @return 数据源配置列表
     */
    public List<DataSourceConfig> getAllDataSourceConfigs() {
        return getDataSourceConfigList();
    }
    
    /**
     * 分页查询数据源配置
     * 
     * @param queryReqVO 查询条件
     * @param pageParam 分页参数
     * @return 分页结果
     */
    public PageResult<DataSourceConfig> getDataSourceConfigPage(DataSourceConfigQueryRequest queryReqVO, PageParam pageParam) {
        // 构建查询条件
        Criteria<DataSourceConfig> criteria = Criteria.<DataSourceConfig>builder();
        
        // 设置分页参数
        if (pageParam != null) {
            criteria.page(pageParam.getPage(), pageParam.getSize());
        }
        
        // 使用Repository的pageByCriteria方法
        return dataSourceConfigRepository.pageByCriteria(criteria);
    }

    /**
     * 校验数据源配置是否存在
     * 
     * @param id 数据源配置ID
     * @throws RuntimeException 当数据源配置不存在时抛出异常
     */
    private void validateDataSourceConfigExists(Long id) {
        if (dataSourceConfigRepository.findById(id) == null) {
            throw new RuntimeException("数据源配置不存在");
        }
    }

    /**
     * 测试数据源连接
     * 
     * @param config 数据源配置
     * @return 是否连接成功
     */
    public boolean testConnection(DataSourceConfig config) {
        Connection conn = null;
        try {
            // 加载驱动
            Class.forName(config.getDriverClassName());
            
            // 创建连接
            conn = DriverManager.getConnection(
                    config.getUrl(),
                    config.getUsername(),
                    config.getPassword()
            );
            
            // 测试连接是否有效
            boolean isValid = conn.isValid(5); // 5秒超时
            log.info("数据源连接测试成功: {}", config.getName());
            return isValid;
        } catch (ClassNotFoundException e) {
            log.error("数据库驱动未找到: {}", config.getDriverClassName(), e);
            throw new RuntimeException("数据库驱动未找到: " + config.getDriverClassName());
        } catch (SQLException e) {
            log.error("数据源连接失败: {}", config.getName(), e);
            throw new RuntimeException("数据源连接失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("测试数据源连接失败", e);
            throw new RuntimeException("测试连接失败: " + e.getMessage());
        } finally {
            if (conn != null) {
                try {
                    conn.close();
                } catch (SQLException e) {
                    log.warn("关闭数据库连接失败", e);
                }
            }
        }
    }
    
    /**
     * 获取数据库连接
     * 用于实际的数据库操作
     */
    public Connection getConnection(Long dataSourceConfigId) {
        try {
            // 先尝试从缓存获取
            Connection cachedConn = connectionCache.get(dataSourceConfigId);
            if (cachedConn != null && !cachedConn.isClosed() && cachedConn.isValid(2)) {
                return cachedConn;
            }
            
            // 获取数据源配置
            DataSourceConfig config = getDataSourceConfig(dataSourceConfigId);
            
            // 创建新连接
            Connection conn = DriverManager.getConnection(
                    config.getUrl(),
                    config.getUsername(),
                    config.getPassword()
            );
            
            // 缓存连接
            connectionCache.put(dataSourceConfigId, conn);
            
            return conn;
        } catch (Exception e) {
            log.error("获取数据库连接失败", e);
            throw new RuntimeException("获取数据库连接失败: " + e.getMessage());
        }
    }
    
    /**
     * 清除缓存的连接
     */
    private void clearCachedConnection(Long dataSourceConfigId) {
        Connection conn = connectionCache.remove(dataSourceConfigId);
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException e) {
                log.warn("关闭缓存连接失败", e);
            }
        }
    }
    
    /**
     * 验证数据源配置
     */
    private void validateDataSourceConfig(DataSourceConfig config) {
        if (config == null) {
            throw new RuntimeException("数据源配置不能为空");
        }
        
        if (config.getName() == null || config.getName().trim().isEmpty()) {
            throw new RuntimeException("数据源名称不能为空");
        }
        
        if (config.getUrl() == null || config.getUrl().trim().isEmpty()) {
            throw new RuntimeException("数据源URL不能为空");
        }
        
        if (config.getUsername() == null || config.getUsername().trim().isEmpty()) {
            throw new RuntimeException("用户名不能为空");
        }
        
        if (config.getPassword() == null || config.getPassword().trim().isEmpty()) {
            throw new RuntimeException("密码不能为空");
        }
        
        // 根据URL自动设置驱动类名
        if (config.getDriverClassName() == null || config.getDriverClassName().trim().isEmpty()) {
            if (config.getUrl().contains("mysql")) {
                config.setDriverClassName("com.mysql.cj.jdbc.Driver");
            } else if (config.getUrl().contains("oracle")) {
                config.setDriverClassName("oracle.jdbc.OracleDriver");
            } else if (config.getUrl().contains("postgresql")) {
                config.setDriverClassName("org.postgresql.Driver");
            } else if (config.getUrl().contains("sqlserver")) {
                config.setDriverClassName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
            } else {
                throw new RuntimeException("无法识别的数据库类型，请手动指定驱动类名");
            }
        }
    }
}
