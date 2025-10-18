-- 数据源配置表
CREATE TABLE `codegen_datasource` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `name` varchar(100) NOT NULL COMMENT '数据源名称',
  `url` varchar(255) NOT NULL COMMENT '数据库连接URL',
  `username` varchar(50) NOT NULL COMMENT '数据库用户名',
  `password` varchar(255) NOT NULL COMMENT '数据库密码',
  `driver_class_name` varchar(255) NOT NULL COMMENT '数据库驱动类名',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` varchar(50) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(50) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(1) DEFAULT 0 COMMENT '是否删除',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据源配置表';

-- 代码生成表配置表
CREATE TABLE `codegen_table` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `datasource_id` bigint(20) NOT NULL COMMENT '数据源ID',
  `scene` int(11) DEFAULT NULL COMMENT '生成场景',
  `table_name` varchar(100) NOT NULL COMMENT '表名',
  `table_comment` varchar(255) DEFAULT NULL COMMENT '表描述',
  `module_name` varchar(50) DEFAULT NULL COMMENT '模块名',
  `package_name` varchar(255) DEFAULT NULL COMMENT '包路径',
  `business_name` varchar(50) DEFAULT NULL COMMENT '业务名称',
  `class_name` varchar(100) NOT NULL COMMENT 'Java类名',
  `class_comment` varchar(255) DEFAULT NULL COMMENT '类描述',
  `author` varchar(50) DEFAULT NULL COMMENT '作者',
  `template_type` int(11) DEFAULT NULL COMMENT '模板类型',
  `parent_menu_id` bigint(20) DEFAULT NULL COMMENT '父菜单ID',
  `master_table_id` bigint(20) DEFAULT NULL COMMENT '主表ID',
  `sub_join_column_id` bigint(20) DEFAULT NULL COMMENT '子表关联主表的字段ID',
  `sub_join_many` tinyint(1) DEFAULT 0 COMMENT '是否一对多关系',
  `tree_parent_column_id` bigint(20) DEFAULT NULL COMMENT '树表父字段ID',
  `tree_name_column_id` bigint(20) DEFAULT NULL COMMENT '树表名称字段ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` varchar(50) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(50) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(1) DEFAULT 0 COMMENT '是否删除',
  PRIMARY KEY (`id`),
  KEY `idx_datasource_id` (`datasource_id`),
  KEY `idx_table_name` (`table_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='代码生成表配置表';

-- 代码生成列配置表
CREATE TABLE `codegen_column` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `table_id` bigint(20) NOT NULL COMMENT '所属表ID',
  `column_name` varchar(100) NOT NULL COMMENT '数据库列名',
  `data_type` varchar(50) NOT NULL COMMENT '数据库数据类型',
  `column_comment` varchar(255) DEFAULT NULL COMMENT '列注释',
  `java_type` varchar(100) NOT NULL COMMENT 'Java数据类型',
  `java_field` varchar(100) NOT NULL COMMENT 'Java字段名',
  `primary_key` tinyint(1) DEFAULT 0 COMMENT '是否主键',
  `auto_increment` tinyint(1) DEFAULT 0 COMMENT '是否自增',
  `nullable` tinyint(1) DEFAULT 1 COMMENT '是否可为空',
  `enable_create` tinyint(1) DEFAULT 1 COMMENT '是否用于创建操作',
  `enable_update` tinyint(1) DEFAULT 1 COMMENT '是否用于更新操作',
  `enable_query` tinyint(1) DEFAULT 1 COMMENT '是否用于列表查询',
  `show_in_list` tinyint(1) DEFAULT 1 COMMENT '是否在列表结果中展示',
  `list_query_condition` varchar(50) DEFAULT NULL COMMENT '列表查询条件类型',
  `html_type` varchar(50) DEFAULT NULL COMMENT 'HTML表单控件类型',
  `dict_type` varchar(100) DEFAULT NULL COMMENT '字典类型编码',
  `relation_table_name` varchar(100) DEFAULT NULL COMMENT '关联表名',
  `relation_show_field` varchar(100) DEFAULT NULL COMMENT '关联表展示字段',
  `relation_query_field` varchar(100) DEFAULT NULL COMMENT '关联表查询字段',
  `extra_attrs` text COMMENT '扩展属性，JSON格式',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` varchar(50) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(50) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(1) DEFAULT 0 COMMENT '是否删除',
  PRIMARY KEY (`id`),
  KEY `idx_table_id` (`table_id`),
  KEY `idx_column_name` (`column_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='代码生成列配置表';

-- 插入主数据源配置示例数据
INSERT INTO `codegen_datasource` (`id`, `name`, `url`, `username`, `password`, `driver_class_name`) 
VALUES (0, '主数据源', 'jdbc:mysql://localhost:3306/example_db?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai', 'root', 'password', 'com.mysql.cj.jdbc.Driver');