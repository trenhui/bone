/**
 * ${className} 列表页面
 * 使用 ProTable 实现列表展示、分页、搜索
 */
import React from 'react';
import { Button } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import { ProTable } from '@ant-design/pro-components';
import type { ProColumns } from '@ant-design/components';
import type { ${className} } from '../../../services/${moduleName}/${lowerClassName}Api';
import { ${lowerClassName}Api } from '../../../services/${moduleName}/${lowerClassName}Api';
import ${className}FormModal from './components/${className}FormModal';
import { useModal } from '@umijs/max';

/**
 * ${className} 列表组件
 */
const ${className}List: React.FC = () => {
  const [modalProps, { open, setOpen }] = useModal<${className} | null>(null);

  /**
   * 表格列定义
<#list columns as column>
   * <#if column.isPrimaryKey>
   *  * - ${column.columnComment}: 主键
   * </#if>
<#if !column.isPrimaryKey>
   *  * - ${column.columnComment}: ${column.columnComment}
   * </#if>
</#list>
   */
  const columns: ProColumns<${className}>[] = [
<#list columns as column>
    <#if column.dictType?? && column.dictType?hasContent>
    {
      title: '${column.columnComment}',
      dataIndex: '${column.javaField}',
      valueType: 'select',
      request: async () => {
        return [
          // TODO: 从字典服务获取选项
        ];
      },
      width: 120,
    },
    <#elseif column.javaType == "LocalDateTime" || column.javaType == "Date">
    {
      title: '${column.columnComment}',
      dataIndex: '${column.javaField}',
      valueType: 'dateTime',
      width: 180,
    },
    <#elseif column.javaType == "Boolean">
    {
      title: '${column.columnComment}',
      dataIndex: '${column.javaField}',
      valueType: 'switch',
      width: 80,
    },
    <#else>
    {
      title: '${column.columnComment}',
      dataIndex: '${column.javaField}',
      valueType: '${column.htmlType}',
      width: ${column.isPrimaryKey? '120' : '150'},
    },
    </#if>
</#list>
    {
      title: '操作',
      dataIndex: 'option',
      valueType: 'option',
      width: 180,
      render: (_, record, __, action) => [
        <a
          key="editable"
          onClick={() => {
            setOpen(true);
            modalProps.onChange(record);
          }}
        >
          编辑
        </a>,
        <a
          key="delete"
          onClick={async () => {
            await ${lowerClassName}Api.delete${className}(record.${primaryColumn.javaField});
            action?.reload();
          }}
        >
          删除
        </a>,
      ],
    },
  ];

  /**
   * 处理表单提交
   */
  const handleSubmit = async (values: Partial<${className}>) => {
    if (modalProps.data && modalProps.data.${primaryColumn.javaField}) {
      await ${lowerClassName}Api.update${className}(modalProps.data.${primaryColumn.javaField}, values);
    } else {
      await ${lowerClassName}Api.create${className}(values);
    }
    setOpen(false);
    // 刷新表格
    // @ts-ignore
    action?.reload();
  };

  return (
    <>
      <ProTable<${className}>
        headerTitle="${className} 列表"
        toolBarRender={() => [
          <Button
            key="button"
            icon={<PlusOutlined />}
            onClick={() => {
              setOpen(true);
              modalProps.onChange(null);
            }}
            type="primary"
          >
            新建
          </Button>,
        ]}
        columns={columns}
        request={async (params) => {
          const { data, total } = await ${lowerClassName}Api.page${className}({
            current: params.current!,
            pageSize: params.pageSize!,
          });
          return {
            data: data || [],
            success: true,
            total: total || 0,
          };
        }}
        rowKey="id"
        pagination={{
          pageSize: 10,
        }}
      />
      <${className}FormModal
        open={open}
        currentRecord={modalProps.data}
        onCancel={() => setOpen(false)}
        onSubmit={handleSubmit}
      />
    </>
  );
};

export default ${className}List;
