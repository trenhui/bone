// 数据源类型
export interface DataSource {
  id: string;
  name: string;
  type: string;
  host: string;
  port: string;
  database: string;
  username: string;
  password: string;
  status: string;
  createdAt?: string;
  updatedAt?: string;
}

// 表结构类型
export interface TableColumn {
  columnName: string;
  dataType: string;
  columnComment: string;
  ordinalPosition: number;
  isPrimaryKey: boolean;
  isNullable: boolean;
  columnSize: number;
  decimalDigits: number;
  defaultValue: string;
}

export interface DatabaseTable {
  tableName: string;
  tableComment: string;
  columns?: TableColumn[];
  primaryKey?: string;
  indexes?: string[];
}

// 代码生成请求类型
export interface GenerateCodeRequest {
  templateId: string;
  name: string;
  description?: string;
  language: string;
  framework: string;
  parameters?: Record<string, unknown>;
  tags?: string[];
  outputFormat?: string;
  outputPath?: string;
  includeTests?: boolean;
  includeDocumentation?: boolean;
  dataSourceId: string;
  tableNames: string[];
  basePackage: string;
  moduleName: string;
}

// 代码生成响应类型
export interface GeneratedFile {
  fileName: string;
  filePath: string;
  content: string;
  fileType: string;
  fileSize: number;
}

export interface CodeGenerationResponse {
  generationId: string;
  status: string;
  message: string;
  generatedFiles: GeneratedFile[];
  executionTime: number;
  outputPath: string;
}

// 生成产物文件视图（GET /api/v1/generator/code-generation/tasks/{taskId}/files）
export interface GeneratedFileItem {
  filePath: string;
  fileName: string;
  size: number;
  /** 仅 content=true 时返回 */
  content?: string | null;
}

// 生成历史记录（对齐后端 CodeGenerationHistory 域模型，GET /api/v1/generator/history）
export interface GenerationHistoryItem {
  id: string;
  taskId: string;
  templateId: string;
  templateName: string;
  generationName: string;
  dataSourceId: string;
  tableNames: string[];
  basePackage: string;
  moduleName: string;
  status: 'PENDING' | 'PROCESSING' | 'SUCCESS' | 'FAILED';
  fileCount: number | null;
  executionTime: number | null;
  outputPath: string | null;
  errorMessage: string | null;
  startedAt: string | null;
  completedAt: string | null;
  createdAt: string | null;
}

// 分页结果类型（对齐 Bone-API §3.3）
export interface PageResult<T> {
  records: T[];
  /** @deprecated 过渡字段 */
  list?: T[];
  total: number;
  page: number;
  size: number;
  pages: number;
  hasNext?: boolean;
  hasPrevious?: boolean;
}

// API响应类型
export interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
  success: boolean;
}