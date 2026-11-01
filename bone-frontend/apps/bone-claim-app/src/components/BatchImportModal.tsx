import { useEffect, useState, type ReactElement } from 'react';
import {
  Alert,
  App as AntApp,
  Button,
  Modal,
  Progress,
  Space,
  Table,
  Tag,
  Typography,
  Upload,
  type UploadProps,
} from 'antd';
import { DownloadOutlined, UploadOutlined } from '@ant-design/icons';
import { resolveErrorMessage } from '@bone/shared-utils';
import type { ApiResponse } from '@bone/shared-types';

/**
 * 通用 CSV 批量导入弹窗（配置面）。
 *
 * <p><b>为什么用 CSV 而不是 Excel</b>：本应用 {@code package.json} 未引入任何 xlsx/csv 解析库，
 * 引入即触发依赖新增（属 L3 需架构师审批）。CSV 用浏览器原生 {@code Blob} / {@code FileReader}
 * 即可完成模板下载与解析，零依赖、低风险，且 Excel 可直接「另存为 CSV / 打开 CSV」，
 * 对运营侧体验无损。表头用英文键（稳定、避开中文编码坑），中文说明放在弹窗内的说明表。
 *
 * <p><b>不重写建表逻辑</b>：每一行最终都调用调用方传入的 {@code createOne}（即现有单条创建接口），
 * 因此不引入新的后端契约，也不触碰既有架构——只是把「逐条手填」变成「按模板批量填 + 逐条创建」。
 * 失败的行不影响已成功的行（逐条独立事务），并支持「下载结果」回执。
 */
export interface ImportColumn {
  /** CSV 表头（英文键，稳定、避免中文编码坑）。 */
  key: string;
  /** 中文说明（模板说明表 + 预览表头用）。 */
  label: string;
  /** 是否必填（用于行级校验提示）。 */
  required?: boolean;
  /** 模板示例值（写入模板数据行；Excel 打开即可见格式）。 */
  example?: string;
}

export interface BatchImportModalProps<TReq = Record<string, unknown>> {
  open: boolean;
  onClose: () => void;
  title: string;
  /** 模板文件名（不含扩展名），如 'policy-template'。 */
  fileName: string;
  columns: ImportColumn[];
  /** 缺失单元格回退的默认值（如计划导入时把当前选中的保单号带进去）。 */
  defaults?: Record<string, string>;
  /** 把一行 CSV（键为 column.key）转成创建请求；返回 null 表示该行无需创建（标记成功并跳过）。 */
  buildRequest: (row: Record<string, string>) => TReq | null;
  /** 行级校验：返回错误信息字符串表示非法，返回 null 表示通过。 */
  validateRow?: (row: Record<string, string>) => string | null;
  /** 单条创建调用（返回 ApiResponse）。失败抛错由组件捕获并记到该行。 */
  createOne: (req: TReq) => Promise<ApiResponse<unknown>>;
  /** 全部导入完成后的回调（通常用于刷新列表）。 */
  onSuccess?: () => void;
}

interface ParsedRow {
  index: number;
  raw: Record<string, string>;
  /** 行级校验错误（非空则跳过导入）。 */
  error?: string;
  status: 'pending' | 'success' | 'failed';
  /** 成功/失败详情。 */
  message?: string;
}

/** 解析 CSV（最小 RFC4180：支持引号包裹、字段内逗号与双引号转义）。 */
function parseCsv(text: string): string[][] {
  const rows: string[][] = [];
  let row: string[] = [];
  let field = '';
  let inQuotes = false;
  for (let i = 0; i < text.length; i++) {
    const c = text[i];
    if (inQuotes) {
      if (c === '"') {
        if (text[i + 1] === '"') {
          field += '"';
          i++;
        } else {
          inQuotes = false;
        }
      } else {
        field += c;
      }
    } else if (c === '"') {
      inQuotes = true;
    } else if (c === ',') {
      row.push(field);
      field = '';
    } else if (c === '\n') {
      row.push(field);
      rows.push(row);
      row = [];
      field = '';
    } else if (c !== '\r') {
      field += c;
    }
  }
  if (field.length > 0 || row.length > 0) {
    row.push(field);
    rows.push(row);
  }
  // 丢弃完全空白的行（仅含空串）
  return rows.filter((r) => r.some((cell) => cell.trim() !== ''));
}

/** CSV 单元格转义：含逗号/引号/换行时用引号包裹并把内部引号翻倍。 */
function csvCell(value: string): string {
  if (/[",\n\r]/.test(value)) {
    return '"' + value.replace(/"/g, '""') + '"';
  }
  return value;
}

export function BatchImportModal<TReq = Record<string, unknown>>(
  props: BatchImportModalProps<TReq>,
): ReactElement {
  const {
    open,
    onClose,
    title,
    fileName,
    columns,
    defaults,
    buildRequest,
    validateRow,
    createOne,
    onSuccess,
  } = props;
  const { message } = AntApp.useApp();
  const [rows, setRows] = useState<ParsedRow[]>([]);
  const [importing, setImporting] = useState(false);
  const [done, setDone] = useState(0);

  useEffect(() => {
    if (open) {
      setRows([]);
      setDone(0);
      setImporting(false);
    }
  }, [open]);

  const downloadTemplate = () => {
    const header = columns.map((c) => c.key).join(',');
    const data = columns.map((c) => csvCell(c.example ?? '')).join(',');
    const csv = '﻿' + header + '\n' + data + '\n';
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `${fileName}.csv`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  };

  const parseAndLoad = (rawText: string) => {
    const text = rawText.replace(/^﻿/, '');
    const matrix = parseCsv(text);
    if (matrix.length < 2) {
      message.warning('文件没有可导入的数据行（至少需表头 + 1 行数据）');
      setRows([]);
      return;
    }
    const headerKeys = matrix[0].map((h) => h.trim());
    const parsed = matrix.slice(1).map((cells, i) => {
      const raw: Record<string, string> = {};
      headerKeys.forEach((k, j) => {
        raw[k] = cells[j] ?? '';
      });
      if (defaults) {
        for (const [k, v] of Object.entries(defaults)) {
          if (!raw[k]) raw[k] = v;
        }
      }
      const err = validateRow ? validateRow(raw) : null;
      return { index: i + 1, raw, error: err ?? undefined, status: 'pending' as const };
    });
    setRows(parsed);
    setDone(0);
  };

  const beforeUpload: UploadProps['beforeUpload'] = (file) => {
    const reader = new FileReader();
    reader.onload = () => parseAndLoad(String(reader.result ?? ''));
    reader.readAsText(file, 'UTF-8');
    return false; // 阻止 antd 自动上传：本组件自行读取并解析
  };

  const startImport = async () => {
    setImporting(true);
    const next = [...rows];
    let processed = 0;
    for (let i = 0; i < next.length; i++) {
      const r = next[i];
      if (r.error) {
        // 校验未通过的行直接跳过，不调用后端
        processed++;
        setDone(processed);
        continue;
      }
      const req = buildRequest(r.raw);
      try {
        if (!req) {
          next[i] = { ...r, status: 'success', message: '跳过' };
        } else {
          await createOne(req);
          next[i] = { ...r, status: 'success', message: '成功' };
        }
      } catch (err) {
        next[i] = { ...r, status: 'failed', message: resolveErrorMessage(err, '创建失败') };
      }
      processed++;
      setRows([...next]);
      setDone(processed);
    }
    setImporting(false);
    onSuccess?.();
    message.success(
      `导入完成：成功 ${next.filter((x) => x.status === 'success').length}，失败 ${next.filter((x) => x.status === 'failed').length}`,
    );
  };

  const downloadResult = () => {
    const header = columns.map((c) => c.key).join(',') + ',导入结果,错误信息';
    const lines = rows.map((r) => {
      const cells = columns.map((c) => csvCell(r.raw[c.key] ?? ''));
      const result =
        r.status === 'success'
          ? '成功'
          : r.status === 'failed'
            ? '失败'
            : r.error
              ? '校验未通过'
              : '未处理';
      cells.push(csvCell(result));
      cells.push(csvCell(r.message ?? r.error ?? ''));
      return cells.join(',');
    });
    const csv = '﻿' + [header, ...lines].join('\n');
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `${fileName}-result.csv`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  };

  const validRows = rows.filter((r) => !r.error);
  const successCount = rows.filter((r) => r.status === 'success').length;
  const failCount = rows.filter((r) => r.status === 'failed').length;
  const invalidCount = rows.filter((r) => r.error).length;
  const finished = rows.length > 0 && done === rows.length;

  const previewColumns = [
    ...columns.map((c) => ({
      title: c.label,
      dataIndex: ['raw', c.key] as [string, string],
      ellipsis: true,
      width: 150,
      render: (_: unknown, r: ParsedRow) =>
        r.raw[c.key] ? (
          r.raw[c.key]
        ) : (
          <Typography.Text type="secondary">—</Typography.Text>
        ),
    })),
    {
      title: '校验 / 结果',
      width: 220,
      render: (_: unknown, r: ParsedRow) => {
        if (!importing && r.error) {
          return <Tag color="red">校验未通过：{r.error}</Tag>;
        }
        if (r.status === 'success') return <Tag color="green">{r.message ?? '成功'}</Tag>;
        if (r.status === 'failed') return <Tag color="red">{r.message ?? '失败'}</Tag>;
        return <Typography.Text type="secondary">待导入</Typography.Text>;
      },
    },
  ];

  return (
    <Modal
      open={open}
      title={title}
      onCancel={onClose}
      footer={null}
      width={900}
      destroyOnHidden
    >
      <Space direction="vertical" size="middle" style={{ width: '100%' }}>
        <Alert
          type="info"
          showIcon
          message="导入说明"
          description={
            <>
              下载模板后按列填写，保存为 .csv（Excel 可「另存为 CSV」）。必填列不可空；导入按行逐条创建，
              失败不影响已成功的行，完成后可「下载结果」回执。
            </>
          }
        />
        <Space wrap>
          <Button icon={<DownloadOutlined />} onClick={downloadTemplate}>
            下载模板
          </Button>
          <Upload accept=".csv" beforeUpload={beforeUpload} showUploadList={false}>
            <Button icon={<UploadOutlined />}>选择 CSV 文件</Button>
          </Upload>
          <Button
            type="primary"
            disabled={validRows.length === 0 || importing}
            loading={importing}
            onClick={() => void startImport()}
          >
            开始导入（{validRows.length} 条）
          </Button>
          {finished && (
            <Button onClick={downloadResult}>下载结果</Button>
          )}
        </Space>
        {importing && (
          <Progress percent={rows.length ? Math.round((done / rows.length) * 100) : 0} />
        )}
        {finished && (
          <Alert
            type={failCount === 0 && invalidCount === 0 ? 'success' : 'warning'}
            showIcon
            message={`导入完成：成功 ${successCount} 条，失败 ${failCount} 条，校验未通过 ${invalidCount} 条`}
          />
        )}
        {rows.length > 0 && (
          <Table<ParsedRow>
            rowKey="index"
            size="small"
            dataSource={rows}
            columns={previewColumns}
            pagination={rows.length > 10 ? { pageSize: 10 } : false}
            scroll={{ y: 320 }}
          />
        )}
      </Space>
    </Modal>
  );
}
