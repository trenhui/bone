package com.bone.tpa.config;

import com.alibaba.druid.pool.DruidDataSource;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Configuration
public class DatasourceTuisongConfig {
    private static final String MAPPER_LOCATION = "classpath*:/mapper/masterdb/*.xml";
    private static final String DOMAIN_PACKAGE = "com.bone.tpa.sdk.masterdb.model";



    @Value("${spring.datasource.masterdb.url}")
    private String dbUrl;

    @Value("${spring.datasource.masterdb.username:masteruser}")
    private String username;

    @Value("${spring.datasource.masterdb.password:Pukang^O13}")
    private String password;

    @Value("${spring.datasource.masterdata.driver-class-name:com.mysql.cj.jdbc.Driver}")
    private String driverClassName;


    @Bean(name="masterdbdbSource")   //声明其为Bean实例
    public DataSource masterdbDataSource() {
        DruidDataSource datasource = new DruidDataSource();

        datasource.setUrl(this.dbUrl);
        datasource.setUsername(username);
        datasource.setPassword(password);
        datasource.setDriverClassName(driverClassName);
        datasource.setMinIdle(20);

        datasource.setMaxActive(30);
        datasource.setMaxWait(10000);
        datasource.setInitialSize(30);

        return datasource;
    }
    @Bean(name = "masterdbSqlSessionFactory")
    public SqlSessionFactory masterdbSqlSessionFactory(@Qualifier("masterdbdbSource") DataSource masterdbDataSource)
            throws Exception {
        MybatisSqlSessionFactoryBean sessionFactory = new MybatisSqlSessionFactoryBean();
        MybatisConfiguration configuration = new MybatisConfiguration();
        sessionFactory.setConfiguration(configuration);
        sessionFactory.setDataSource(masterdbDataSource);
        // 设置多个 Mapper XML 路径
        sessionFactory.setMapperLocations(resolveMapperLocations());
        sessionFactory.setTypeAliasesPackage(DOMAIN_PACKAGE);
        //mybatis 数据库字段与实体类属性驼峰映射配置
        sessionFactory.getObject().getConfiguration().setMapUnderscoreToCamelCase(true);
        return sessionFactory.getObject();
    }

    @Bean(name = "masterdataTransactionManager")
    public DataSourceTransactionManager tpaCoreTransactionManager() {
        return new DataSourceTransactionManager(masterdbDataSource());
    }

    @Bean(name = "masterdataTransactionTemplate")
    public TransactionTemplate tpadbTransactionTemplate(@Qualifier("masterdataTransactionManager") PlatformTransactionManager platformTransactionManager) {
        return new TransactionTemplate(platformTransactionManager);
    }

    Resource[] resolveMapperLocations() {
        ResourcePatternResolver resourceResolver = new PathMatchingResourcePatternResolver();
        List<String> mapperLocations = new ArrayList<>();
        mapperLocations.add(MAPPER_LOCATION);
        List<Resource> resources = new ArrayList();
        if (mapperLocations != null) {
            for (String mapperLocation : mapperLocations) {
                try {
                    Resource[] mappers = resourceResolver.getResources(mapperLocation);
                    resources.addAll(Arrays.asList(mappers));
                } catch (IOException e) {
                    // ignore
                }
            }
        }
        return resources.toArray(new Resource[resources.size()]);
    }

}
