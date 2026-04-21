/**
 * ${className} API 服务
 * 封装所有与${className}相关的后端API调用
 */
import { request } from '@umijs/max';

/**
 * ${className} 实体类型
 */
export interface ${className} {
<#list columns as column>
  /** ${column.columnComment} */
  ${column.javaField}: ${column.javaType};
</#list>
}

/**
 * 分页查询参数
 */
export interface PageQuery {
  current: number;
  pageSize: number;
}

/**
 * 分页响应
 */
export interface PageResult<T> {
  data: T[];
  total: number;
}

/**
 * 分页查询${className}列表
 */
export async function page${className}(params: PageQuery) {
  return request<PageResult<${className}>>(`/api/v1/codegen/${lowerClassName}/page`, {
    method: 'GET',
    params,
  });
}

/**
 * 根据ID获取${className}详情
 */
export async function get${className}ById(id: ${primaryColumn.javaType}) {
  return request<${className}>(`/api/v1/codegen/${lowerClassName}/${id}`, {
    method: 'GET',
  });
}

/**
 * 创建${className}
 */
export async function create${className}(data: Partial<${className}>) {
  return request<boolean>(`/api/v1/codegen/${lowerClassName}`, {
    method: 'POST',
    data,
  });
}

/**
 * 更新${className}
 */
export async function update${className}(id: ${primaryColumn.javaType}, data: Partial<${className}>) {
  return request<boolean>(`/api/v1/codegen/${lowerClassName}/${id}`, {
    method: 'PUT',
    data,
  });
}

/**
 * 删除${className}
 */
export async function delete${className}(id: ${primaryColumn.javaType}) {
  return request<boolean>(`/api/v1/codegen/${lowerClassName}/${id}`, {
    method: 'DELETE',
  });
}
