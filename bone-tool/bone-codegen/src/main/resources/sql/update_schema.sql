-- 更新建表SQL脚本
-- 基于最新实体类生成

-- 1. 数据源配置表
CREATE TABLE IF NOT EXISTS `datasource` (
  `id` bigint(20) NOT NULL COMMENT '主键ID',
  `name` varchar(255) NOT NULL COMMENT '数据源名称',
  `url` varchar(512) NOT NULL COMMENT '数据库连接URL',
  `username` varchar(255) NOT NULL COMMENT '数据库用户名',
  `password` varchar(255) NOT NULL COMMENT '数据库密码',
  `driver_class_name` varchar(255) NOT NULL COMMENT '数据库驱动类名',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `updated_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(1) DEFAULT 0 COMMENT '是否删除(0:未删除,1:已删除)',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据源配置';

-- 2. 代码生成表配置表
CREATE TABLE IF NOT EXISTS `codegen_table` (
  `id` bigint(20) NOT NULL COMMENT '主键ID',
  `datasource_id` bigint(20) NOT NULL COMMENT '所属数据源ID',
  `table_name` varchar(255) NOT NULL COMMENT '表名',
  `table_comment` varchar(512) DEFAULT NULL COMMENT '表注释',
  `module_name` varchar(255) DEFAULT NULL COMMENT '模块名称',
  `package_name` varchar(512) DEFAULT NULL COMMENT '包名',
  `business_name` varchar(255) DEFAULT NULL COMMENT '业务名称',
  `class_name` varchar(255) DEFAULT NULL COMMENT '类名',
  `class_comment` varchar(512) DEFAULT NULL COMMENT '类注释',
  `author` varchar(64) DEFAULT NULL COMMENT '作者',
  `template_type` int(11) DEFAULT NULL COMMENT '模板类型',
  `scene` int(11) DEFAULT NULL COMMENT '场景',
  `parent_menu_id` bigint(20) DEFAULT NULL COMMENT '父菜单ID',
  `master_table_id` bigint(20) DEFAULT NULL COMMENT '主表ID',
  `sub_join_column_id` bigint(20) DEFAULT NULL COMMENT '子表关联列ID',
  `sub_join_many` tinyint(1) DEFAULT 0 COMMENT '子表是否关联多条',
  `tree_parent_column_id` bigint(20) DEFAULT NULL COMMENT '树父节点列ID',
  `tree_name_column_id` bigint(20) DEFAULT NULL COMMENT '树名称列ID',
  `code_files` text COMMENT '代码文件映射(JSON格式)',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `updated_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(1) DEFAULT 0 COMMENT '是否删除(0:未删除,1:已删除)',
  PRIMARY KEY (`id`),
  KEY `idx_datasource_id` (`datasource_id`),
  KEY `idx_table_name` (`table_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='代码生成表配置';

-- 3. 代码生成列配置表
CREATE TABLE IF NOT EXISTS `codegen_column` (
  `id` bigint(20) NOT NULL COMMENT '主键ID',
  `table_id` bigint(20) NOT NULL COMMENT '所属表ID',
  `column_name` varchar(255) NOT NULL COMMENT '数据库列名',
  `data_type` varchar(128) DEFAULT NULL COMMENT '数据库数据类型',
  `column_comment` varchar(512) DEFAULT NULL COMMENT '列注释',
  `java_type` varchar(128) DEFAULT NULL COMMENT 'Java数据类型',
  `java_field` varchar(255) DEFAULT NULL COMMENT 'Java字段名',
  `primary_key` tinyint(1) DEFAULT 0 COMMENT '是否主键(0:否,1:是)',
  `auto_increment` tinyint(1) DEFAULT 0 COMMENT '是否自增(0:否,1:是)',
  `nullable` tinyint(1) DEFAULT 1 COMMENT '是否可为空(0:否,1:是)',
  `enable_create` tinyint(1) DEFAULT 1 COMMENT '是否用于创建操作(0:否,1:是)',
  `enable_update` tinyint(1) DEFAULT 1 COMMENT '是否用于更新操作(0:否,1:是)',
  `enable_query` tinyint(1) DEFAULT 0 COMMENT '是否用于列表查询(0:否,1:是)',
  `show_in_list` tinyint(1) DEFAULT 1 COMMENT '是否在列表结果中展示(0:否,1:是)',
  `list_query_condition` varchar(64) DEFAULT NULL COMMENT '列表查询条件类型',
  `html_type` varchar(64) DEFAULT NULL COMMENT 'HTML表单控件类型',
  `dict_type` varchar(64) DEFAULT NULL COMMENT '字典类型编码',
  `relation_table_name` varchar(255) DEFAULT NULL COMMENT '关联表名',
  `relation_show_field` varchar(255) DEFAULT NULL COMMENT '关联表展示字段',
  `relation_query_field` varchar(255) DEFAULT NULL COMMENT '关联表查询字段',
  `extra_attrs` text COMMENT '扩展属性(JSON格式)',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `updated_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(1) DEFAULT 0 COMMENT '是否删除(0:未删除,1:已删除)',
  PRIMARY KEY (`id`),
  KEY `idx_table_id` (`table_id`),
  KEY `idx_column_name` (`column_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='代码生成列配置';

-- 添加外键约束（可选，根据实际需求启用）
-- ALTER TABLE `codegen_table` ADD CONSTRAINT `fk_codegen_table_datasource` FOREIGN KEY (`datasource_id`) REFERENCES `datasource` (`id`);
-- ALTER TABLE `codegen_column` ADD CONSTRAINT `fk_codegen_column_table` FOREIGN KEY (`table_id`) REFERENCES `codegen_table` (`id`);