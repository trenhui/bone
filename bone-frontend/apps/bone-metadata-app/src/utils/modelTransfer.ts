/**
 * F13 模型导入导出：实体定义 JSON 文件化（对标 Salesforce Metadata API 的文件形态，衔接 Docs-as-Code）。
 *
 * - 导出：实体定义 + 全部字段 + 关系（informational）→ `<code>.model.json` 下载。
 * - 导入：解析文件 → 结构校验 → 创建实体草稿 → 逐字段创建；关系不自动导入（跨实体依赖，需人工重建，与「复制」语义一致）。
 * - 雪花 ID 一律以字符串/原值透传，禁止 Number() 转换（2^53 截断坑）。
 */
import { metadataEntityApi, metadataFieldApi, metadataRelationApi, errorMessage } from '../services/metadataApi';
import type { MetaEntity, MetaField, MetaRelation } from '../types';

/** 导出文件格式标识与版本（后续演进字段结构时递增） */
export const MODEL_FILE_FORMAT = 'bone-model';
export const MODEL_FILE_VERSION = 1;

/** 导出文件结构（导入侧按此校验） */
export interface ModelTransferFile {
  format: string;
  version: number;
  exportedAt: string;
  entity: {
    name: string;
    code: string;
    displayName: string;
    description?: string;
    tableName: string;
    type?: number;
    deliveryMode?: number;
    icon?: string;
  };
  fields: Array<Omit<MetaField, 'id' | 'entityId' | 'version' | 'createdAt'>>;
  relations?: Array<Omit<MetaRelation, 'id' | 'sourceEntityId' | 'targetEntityId'>>;
}

/** 导入执行结果 */
export interface ImportResult {
  entityId: number;
  entityCode: string;
  createdFields: number;
  failedFields: Array<{ code: string; reason: string }>;
}

/** 标识符白名单（与后端 2a UC-MT2 / validate 规则一致） */
export const MODEL_CODE_PATTERN = /^[a-zA-Z][a-zA-Z0-9_]*$/;

/** 导出实体为 JSON 文件并触发下载 */
export async function exportEntityModel(entity: MetaEntity): Promise<void> {
  const [entityRes, fieldsRes, relationsRes] = await Promise.all([
    metadataEntityApi.detail(entity.id),
    metadataFieldApi.page(entity.id, { pageNum: 1, pageSize: 500 }),
    metadataRelationApi.page({ pageNum: 1, pageSize: 100, sourceEntityId: entity.id }),
  ]);
  if (entityRes.code !== 200) throw new Error(errorMessage(entityRes));

  const payload: ModelTransferFile = {
    format: MODEL_FILE_FORMAT,
    version: MODEL_FILE_VERSION,
    exportedAt: new Date().toISOString(),
    entity: {
      name: entityRes.data.name,
      code: entityRes.data.code,
      displayName: entityRes.data.displayName,
      description: entityRes.data.description,
      tableName: entityRes.data.tableName,
      type: entityRes.data.type,
      deliveryMode: entityRes.data.deliveryMode,
      icon: entityRes.data.icon,
    },
    fields: (fieldsRes.code === 200 ? fieldsRes.data.list : []).map((f) => ({
      name: f.name,
      code: f.code,
      displayName: f.displayName,
      type: f.type,
      length: f.length,
      required: f.required,
      unique: f.unique,
      sortOrder: f.sortOrder,
      comment: f.comment,
    })),
    relations: (relationsRes.code === 200 ? relationsRes.data.list : []).map((r) => ({
      name: r.name,
      type: r.type,
      foreignKeyField: r.foreignKeyField,
      required: r.required,
      cascadeType: r.cascadeType,
    })),
  };

  const blob = new Blob([JSON.stringify(payload, null, 2)], { type: 'application/json' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `${entity.code}.model.json`;
  a.click();
  URL.revokeObjectURL(url);
}

/** 解析上传的模型文件（不做业务校验） */
export function parseModelFile(file: File): Promise<ModelTransferFile> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => {
      try {
        resolve(JSON.parse(String(reader.result)) as ModelTransferFile);
      } catch {
        reject(new Error('文件不是合法 JSON'));
      }
    };
    reader.onerror = () => reject(new Error('文件读取失败'));
    reader.readAsText(file);
  });
}

/** 结构与业务校验，返回问题列表（空数组 = 可导入） */
export function validateModelFile(parsed: ModelTransferFile): string[] {
  const issues: string[] = [];
  if (parsed.format !== MODEL_FILE_FORMAT) {
    issues.push(`格式标识不是「${MODEL_FILE_FORMAT}」，请使用本平台导出的模型文件`);
  }
  if (parsed.version !== MODEL_FILE_VERSION) {
    issues.push(`文件版本 ${parsed.version} 与当前支持版本 ${MODEL_FILE_VERSION} 不一致`);
  }
  const e = parsed.entity;
  if (!e || typeof e !== 'object') {
    issues.push('缺少 entity 定义');
    return issues;
  }
  if (!e.code || !MODEL_CODE_PATTERN.test(e.code)) issues.push('实体编码缺失或不合法（字母开头，仅字母/数字/下划线）');
  if (!e.name) issues.push('实体名称（name）缺失');
  if (!e.displayName) issues.push('实体显示名（displayName）缺失');
  if (!e.tableName || !MODEL_CODE_PATTERN.test(e.tableName)) issues.push('数据库表名缺失或不合法');
  if (!Array.isArray(parsed.fields)) {
    issues.push('fields 不是数组');
  } else {
    const codes = new Set<string>();
    parsed.fields.forEach((f, i) => {
      if (!f?.code || !MODEL_CODE_PATTERN.test(f.code)) issues.push(`字段[${i}] 编码缺失或不合法`);
      else if (codes.has(f.code)) issues.push(`字段编码重复：${f.code}`);
      else codes.add(f.code);
      if (!f?.displayName) issues.push(`字段[${i}] 显示名缺失`);
      if (!f?.type) issues.push(`字段[${i}] 类型缺失`);
      if (!f?.name) issues.push(`字段[${i}] 名称（name）缺失`);
    });
  }
  return issues;
}

/** 导入：创建实体草稿 + 逐字段创建（关系不导入，返回提示由 UI 呈现） */
export async function importEntityModel(
  parsed: ModelTransferFile,
  options: { moduleId?: number | string; onProgress?: (done: number, total: number) => void },
): Promise<ImportResult> {
  const { moduleId, onProgress } = options;
  const e = parsed.entity;

  const createRes = await metadataEntityApi.create({
    name: e.name,
    code: e.code,
    displayName: e.displayName,
    description: e.description,
    tableName: e.tableName,
    type: e.type,
    deliveryMode: e.deliveryMode,
    icon: e.icon,
    moduleId,
  });
  if (createRes.code !== 200 && createRes.code !== 201) {
    throw new Error(errorMessage(createRes));
  }
  const entityId = createRes.data as unknown as number;

  const failedFields: ImportResult['failedFields'] = [];
  const fields = parsed.fields ?? [];
  let done = 0;
  for (const f of fields) {
    try {
      const res = await metadataFieldApi.create(entityId, {
        name: f.name,
        code: f.code,
        displayName: f.displayName,
        type: f.type,
        length: f.length,
        required: f.required,
        unique: f.unique,
        sortOrder: f.sortOrder,
        comment: f.comment,
      });
      if (res.code !== 200 && res.code !== 201) {
        failedFields.push({ code: f.code, reason: errorMessage(res) });
      }
    } catch (err) {
      failedFields.push({ code: f.code, reason: err instanceof Error ? err.message : '网络错误' });
    }
    done += 1;
    onProgress?.(done, fields.length);
  }

  return { entityId, entityCode: e.code, createdFields: fields.length - failedFields.length, failedFields };
}
