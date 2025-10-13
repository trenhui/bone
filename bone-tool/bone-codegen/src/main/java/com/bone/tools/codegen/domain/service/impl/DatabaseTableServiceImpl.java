package com.bone.tools.codegen.domain.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
// 移除错误的DbType导入，后续使用反射处理
import com.baomidou.mybatisplus.generator.config.DataSourceConfig;
import com.baomidou.mybatisplus.generator.config.GlobalConfig;
import com.baomidou.mybatisplus.generator.config.StrategyConfig;
import com.baomidou.mybatisplus.generator.config.builder.ConfigBuilder;
import com.baomidou.mybatisplus.generator.config.po.TableInfo;
import com.baomidou.mybatisplus.generator.config.rules.DateType;
import com.baomidou.mybatisplus.generator.query.SQLQuery;
import com.bone.tools.codegen.domain.service.DataSourceConfigService;
import com.bone.tools.codegen.domain.service.DatabaseTableService;
import com.bone.tools.codegen.domain.entity.DataSourceConfigDO;
import com.bone.tools.codegen.infrastructure.util.JdbcUtils;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.bone.tools.codegen.domain.enums.ErrorCodeConstants.DATA_SOURCE_CONFIG_NOT_OK;

/**
 * 数据库表 领域服务实现类
 * <p>
 * 实现数据库表相关的核心业务逻辑，包括获取表列表、获取表信息等操作
 *
 * @author bone-team
 */
@Service
public class DatabaseTableServiceImpl implements DatabaseTableService {

    @Resource
    private DataSourceConfigService dataSourceConfigService;

    @Override
    public List<TableInfo> getTableList(Long dataSourceConfigId, String nameLike, String commentLike) {
        // 获取数据源配置ID对应的所有表信息
        List<TableInfo> tables = getTableList0(dataSourceConfigId, null);
        
        // 根据名称和注释进行过滤
        return tables.stream()
                .filter(tableInfo -> (StrUtil.isEmpty(nameLike) || tableInfo.getName().contains(nameLike))
                        && (StrUtil.isEmpty(commentLike) || tableInfo.getComment().contains(commentLike)))
                .collect(Collectors.toList());
    }

    @Override
    public TableInfo getTable(Long dataSourceConfigId, String name) {
        // 校验参数
        Assert.notEmpty(name, "表名不能为空");
        
        // 获取指定名称的表信息
        TableInfo tableInfo = CollUtil.getFirst(getTableList0(dataSourceConfigId, name));
        if (tableInfo == null) {
            throw new RuntimeException(String.format("表名%s不存在", name));
        }
        return tableInfo;
    }

    /**
     * 获取表信息列表的内部实现方法
     * 
     * @param dataSourceConfigId 数据源配置ID
     * @param name 指定表名，为null时查询所有表
     * @return 表信息列表
     */
    private List<TableInfo> getTableList0(Long dataSourceConfigId, String name) {
        // 获取并校验数据源配置
        DataSourceConfigDO config = dataSourceConfigService.getDataSourceConfig(dataSourceConfigId);
        
        // 获取连接信息（使用getter方法而不是反射）
        String url = config.getUrl();
        String username = config.getUsername();
        String password = config.getPassword();
        // 由于DataSourceConfigDO没有getDbType方法，使用URL来简单判断数据库类型
        String dbType = null;
        if (url != null) {
            if (url.contains("sqlserver")) {
                dbType = "SQL_SERVER";
            }
        }
        
        // 校验数据源连接
        boolean success = JdbcUtils.isConnectionOK(url, username, password);
        if (!success) {
            throw new RuntimeException(DATA_SOURCE_CONFIG_NOT_OK.getMsg());
        }
        // 使用 MyBatis Plus Generator 解析表结构
        DataSourceConfig.Builder dataSourceConfigBuilder = new DataSourceConfig.Builder(url, username, password);
        
        // 判断数据库类型是否为SQL Server
        boolean isSqlServer = "SQL_SERVER".equals(dbType);
        if (isSqlServer) { 
            // 特殊处理：SQLServer jdbc 非标准，参见 https://github.com/baomidou/mybatis-plus/issues/5419
            dataSourceConfigBuilder.databaseQueryClass(SQLQuery.class);
        }
        // 构建策略配置
        StrategyConfig.Builder strategyConfig = new StrategyConfig.Builder()
                .enableSkipView(); // 忽略视图，业务上一般用不到
        
        if (StrUtil.isNotEmpty(name)) {
            // 查询指定表名
            strategyConfig.addInclude(name);
        } else {
            // 查询所有表（排除系统表）
            // 移除工作流和定时任务前缀的表名
            strategyConfig.addExclude("ACT_[\\S\\s]+|QRTZ_[\\S\\s]+|FLW_[\\S\\s]+");
            // 移除 ORACLE 相关的系统表
            strategyConfig.addExclude("IMPDP_[\\S\\s]+|ALL_[\\S\\s]+|HS_[\\S\\s]+");
            strategyConfig.addExclude("[\\S\\s]+\\$[\\S\\s]+|[\\S\\s]+\\$"); // 表里不能有 $，一般有都是系统的表
        }

        // 构建全局配置
        GlobalConfig globalConfig = new GlobalConfig.Builder()
                .dateType(DateType.TIME_PACK) // 只使用 LocalDateTime 类型，不使用 LocalDate
                .build();
        
        // 创建配置构建器并解析表结构
        ConfigBuilder builder = new ConfigBuilder(
                null, 
                dataSourceConfigBuilder.build(), 
                strategyConfig.build(),
                null, 
                globalConfig, 
                null);
        
        // 获取表信息列表并按名称排序
        List<TableInfo> tables = builder.getTableInfoList();
        tables.sort(Comparator.comparing(TableInfo::getName));
        
        return tables;
    }

}
