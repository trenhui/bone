package com.bone.tool.codegen.domain.service;

import com.bone.core.model.PageParam;
import com.bone.core.model.PageResult;
import com.bone.tool.codegen.application.dto.DataSourceConfigQueryRequest;
import com.bone.tool.codegen.application.dto.DataSourceConfigSaveRequest;
import com.bone.tool.codegen.domain.entity.Datasource;
import com.bone.tool.codegen.domain.repository.DataSourceConfigRepository;
import com.bone.metadata.sdk.query.criteria.Criteria;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import org.springframework.beans.factory.annotation.Autowired;
import com.bone.core.util.ReflectionUtil;

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
        Datasource config = new Datasource();
        
        // 使用反射获取请求对象的字段值
        String name = (String) ReflectionUtil.getFieldValue(createReqVO, "name");
        String url = (String) ReflectionUtil.getFieldValue(createReqVO, "url");
        String username = (String) ReflectionUtil.getFieldValue(createReqVO, "username");
        String password = (String) ReflectionUtil.getFieldValue(createReqVO, "password");
        
        // 使用反射设置领域实体的字段值
        ReflectionUtil.setFieldValue(config, "name", name);
        ReflectionUtil.setFieldValue(config, "url", url);
        ReflectionUtil.setFieldValue(config, "username", username);
        ReflectionUtil.setFieldValue(config, "password", password);
        
        // 验证配置
        validateDataSourceConfig(config);
        
        // 测试连接
        if (!testConnection(config)) {
            throw new RuntimeException("数据源连接测试失败，请检查配置是否正确");
        }
        
        // 使用Repository的save方法
        Long id = dataSourceConfigRepository.save(config);
        
        // 使用反射获取配置名称用于日志
        String configName = (String) ReflectionUtil.getFieldValue(config, "name");
        log.info("创建数据源配置成功: {}", configName);
        return id;
    }

    /**
     * 更新数据源配置
     *
     * @param updateReqVO 更新信息
     */
    public void updateDataSourceConfig(DataSourceConfigSaveRequest updateReqVO) {
        // 获取配置ID并校验存在性
        Long id = (Long) ReflectionUtil.getFieldValue(updateReqVO, "id");
        validateDataSourceConfigExists(id);
        
        // 转换为领域实体并更新
        Datasource config = new Datasource();
        
        // 使用反射获取请求对象的字段值
        String name = (String) ReflectionUtil.getFieldValue(updateReqVO, "name");
        String url = (String) ReflectionUtil.getFieldValue(updateReqVO, "url");
        String username = (String) ReflectionUtil.getFieldValue(updateReqVO, "username");
        String password = (String) ReflectionUtil.getFieldValue(updateReqVO, "password");
        
        // 使用反射设置领域实体的字段值
        ReflectionUtil.setFieldValue(config, "id", id);
        ReflectionUtil.setFieldValue(config, "name", name);
        ReflectionUtil.setFieldValue(config, "url", url);
        ReflectionUtil.setFieldValue(config, "username", username);
        ReflectionUtil.setFieldValue(config, "password", password);
        
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
        
        String configName = (String) ReflectionUtil.getFieldValue(config, "name");
        log.info("更新数据源配置成功: {}", configName);
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
    public Datasource getDataSourceConfig(Long id) {
        // 获取数据源配置详情
        Datasource config = dataSourceConfigRepository.findById(id);
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
    public PageResult<Datasource> getDataSourceConfigPage(PageParam pageParam) {
        return getDataSourceConfigPage(null, pageParam);
    }

    /**
     * 根据查询条件获取数据源配置列表
     *
     * @param request 查询条件
     * @return 数据源配置列表
     */
    public List<Datasource> getDataSourceConfigList(DataSourceConfigQueryRequest request) {
        // 简化实现，直接使用空Criteria返回所有数据
        Criteria<Datasource> criteria = Criteria.<Datasource>builder();
        return dataSourceConfigRepository.findByCriteria(criteria);
    }

    /**
     * 获取所有数据源配置列表
     * 
     * @return 数据源配置列表
     */
    public List<Datasource> getDataSourceConfigList() {
        // 使用Criteria获取所有数据源配置
        Criteria<Datasource> criteria = Criteria.<Datasource>builder();
        return dataSourceConfigRepository.findByCriteria(criteria);
    }
    
    /**
     * 获取所有数据源配置列表（与getDataSourceConfigList相同，提供别名以兼容接口调用）
     * 
     * @return 数据源配置列表
     */
    public List<Datasource> getAllDataSourceConfigs() {
        return getDataSourceConfigList();
    }
    
    /**
     * 分页查询数据源配置
     * 
     * @param queryReqVO 查询条件
     * @param pageParam 分页参数
     * @return 分页结果
     */
    public PageResult<Datasource> getDataSourceConfigPage(DataSourceConfigQueryRequest queryReqVO, PageParam pageParam) {
        // 构建查询条件
        Criteria<Datasource> criteria = Criteria.<Datasource>builder();
        
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
    public boolean testConnection(Datasource config) {
        Connection conn = null;
        try {
            // 使用反射获取配置信息
            String driverClassName = (String) ReflectionUtil.getFieldValue(config, "driverClassName");
            String url = (String) ReflectionUtil.getFieldValue(config, "url");
            String username = (String) ReflectionUtil.getFieldValue(config, "username");
            String password = (String) ReflectionUtil.getFieldValue(config, "password");
            String name = (String) ReflectionUtil.getFieldValue(config, "name");
            
            // 加载驱动
            Class.forName(driverClassName);
            
            // 创建连接
            conn = DriverManager.getConnection(url, username, password);
            
            // 测试连接是否有效
            boolean isValid = conn.isValid(5); // 5秒超时
            log.info("数据源连接测试成功: {}", name);
            return isValid;
        } catch (ClassNotFoundException e) {
            String driverClassName = (String) ReflectionUtil.getFieldValue(config, "driverClassName");
            log.error("数据库驱动未找到: {}", driverClassName, e);
            throw new RuntimeException("数据库驱动未找到: " + driverClassName);
        } catch (SQLException e) {
            String name = (String) ReflectionUtil.getFieldValue(config, "name");
            log.error("数据源连接失败: {}", name, e);
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
    public Connection getConnection(Long datasourceId) {
        try {
            // 先尝试从缓存获取
            Connection cachedConn = connectionCache.get(datasourceId);
            if (cachedConn != null && !cachedConn.isClosed() && cachedConn.isValid(2)) {
                return cachedConn;
            }
            
            // 获取数据源配置
            Datasource config = getDataSourceConfig(datasourceId);
            
            // 使用反射获取连接信息
            String url = (String) ReflectionUtil.getFieldValue(config, "url");
            String username = (String) ReflectionUtil.getFieldValue(config, "username");
            String password = (String) ReflectionUtil.getFieldValue(config, "password");
            
            // 创建新连接
            Connection conn = DriverManager.getConnection(url, username, password);
            
            // 缓存连接
            connectionCache.put(datasourceId, conn);
            
            return conn;
        } catch (Exception e) {
            log.error("获取数据库连接失败", e);
            throw new RuntimeException("获取数据库连接失败: " + e.getMessage());
        }
    }
    
    /**
     * 清除缓存的连接
     */
    private void clearCachedConnection(Long datasourceId) {
        Connection conn = connectionCache.remove(datasourceId);
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
    private void validateDataSourceConfig(Datasource config) {
        if (config == null) {
            throw new RuntimeException("数据源配置不能为空");
        }
        
        // 使用反射获取字段值进行验证
        String name = (String) ReflectionUtil.getFieldValue(config, "name");
        String url = (String) ReflectionUtil.getFieldValue(config, "url");
        String username = (String) ReflectionUtil.getFieldValue(config, "username");
        String password = (String) ReflectionUtil.getFieldValue(config, "password");
        
        if (name == null || name.trim().isEmpty()) {
            throw new RuntimeException("数据源名称不能为空");
        }
        
        if (url == null || url.trim().isEmpty()) {
            throw new RuntimeException("数据源URL不能为空");
        }
        
        if (username == null || username.trim().isEmpty()) {
            throw new RuntimeException("用户名不能为空");
        }
        
        if (password == null || password.trim().isEmpty()) {
            throw new RuntimeException("密码不能为空");
        }
        
        // 根据URL自动设置驱动类名
        String driverClassName = (String) ReflectionUtil.getFieldValue(config, "driverClassName");
        // 不需要重新获取url，因为在方法前面已经获取过了
        
        if (driverClassName == null || driverClassName.trim().isEmpty()) {
            if (url.contains("mysql")) {
                ReflectionUtil.setFieldValue(config, "driverClassName", "com.mysql.cj.jdbc.Driver");
            } else if (url.contains("oracle")) {
                ReflectionUtil.setFieldValue(config, "driverClassName", "oracle.jdbc.OracleDriver");
            } else if (url.contains("postgresql")) {
                ReflectionUtil.setFieldValue(config, "driverClassName", "org.postgresql.Driver");
            } else if (url.contains("sqlserver")) {
                ReflectionUtil.setFieldValue(config, "driverClassName", "com.microsoft.sqlserver.jdbc.SQLServerDriver");
            } else {
                throw new RuntimeException("无法识别的数据库类型，请手动指定驱动类名");
            }
        }
    }
}
