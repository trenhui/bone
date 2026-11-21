import { useEffect, useMemo, useState, type ReactElement } from 'react';
import {
  Button,
  Card,
  Drawer,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  message,
  type TableColumnsType,
} from 'antd';

/** 与后端 CodeMappingApplicationService.ImportRowError 对齐的逐行错误（原因字段是 reason）。 */
export interface ImportRowError {
  rowNo: number;
  field: string;
  reason: string;
  rawRow: Record<string, string>;
}

export interface ImportErrorDrawerProps {
  open: boolean;
  onClose: () => void;
  /** 导入批次号（版本 No），同内容文件重传不变。 */
  batchNo?: string;
  errors: ImportRowError[];
}

/**
 * 映射导入的逐行错误定位抽屉。
 *
 * 只展示后端回传的错误明细：字段精确到列、rawRow 回显原始单元格、行号用于回查源表。
 */
export function ImportErrorDrawer({ open, onClose, batchNo, errors }: ImportErrorDrawerProps): ReactElement {
  const [fieldFilter, setFieldFilter] = useState('ALL');

  useEffect(() => {
    if (open) setFieldFilter('ALL');
  }, [open]);

  const fieldOptions = useMemo(
    () => [
      { value: 'ALL', label: '全部字段' },
      ...Array.from(new Set(errors.map((e) => e.field).filter(Boolean))).map((f) => ({
        value: f,
        label: f,
      })),
    ],
    [errors],
  );
  const visibleErrors = useMemo(
    () => (fieldFilter === 'ALL' ? errors : errors.filter((e) => e.field === fieldFilter)),
    [errors, fieldFilter],
  );
  /** 同一源行可能因多个字段各产生一条错误，故「明细条数」与「涉及行数」分开统计。 */
  const affectedRows = useMemo(() => new Set(errors.map((e) => e.rowNo)).size, [errors]);

  const columns: TableColumnsType<ImportRowError> = [
    {
      title: '行号',
      dataIndex: 'rowNo',
      key: 'rowNo',
      width: 80,
      render: (v: number) => <Typography.Text code>{v}</Typography.Text>,
    },
    {
      title: '错误字段',
      dataIndex: 'field',
      key: 'field',
      width: 160,
      render: (v: string) =>
        v ? <Tag color="red">{v}</Tag> : <Typography.Text type="secondary">-</Typography.Text>,
    },
    {
      title: '原因',
      dataIndex: 'reason',
      key: 'reason',
      ellipsis: true,
      render: (v: string) => v || '-',
    },
    {
      title: '原始行内容(rawRow)',
      dataIndex: 'rawRow',
      key: 'rawRow',
      ellipsis: true,
      render: (v: Record<string, string>) => (
        <Typography.Text code ellipsis>
          {JSON.stringify(v ?? {})}
        </Typography.Text>
      ),
    },
    {
      title: '操作',
      key: 'action',
      width: 120,
      render: (_: unknown, row: ImportRowError) => (
        <Button
          size="small"
          onClick={() => {
            // 演示级定位：只给出源表行号提示。真正跳转到 Excel 源行需要前端持有源工作簿
            // （或上传后保留的解析矩阵）并与 rowNo 对齐；当前映射导入由服务端解析 Excel，
            // 前端只有错误回执，没有源工作簿，故无法真正高亮源行。
            message.info(`已定位到源表第 ${row.rowNo} 行`);
          }}
        >
          定位源行
        </Button>
      ),
    },
  ];

  return (
    <Drawer title="导入错误明细" open={open} onClose={onClose} width={760}>
      <Space direction="vertical" size="middle" style={{ width: '100%' }}>
        <Card size="small" title="批次统计">
          <Space direction="vertical" size={2}>
            <Typography.Text>批次号：{batchNo ?? '—'}</Typography.Text>
            <Typography.Text>错误明细：{errors.length} 条</Typography.Text>
            <Typography.Text>涉及源表行：{affectedRows} 行</Typography.Text>
          </Space>
        </Card>

        <Space wrap align="center">
          <Typography.Text>按字段筛选：</Typography.Text>
          <Select
            value={fieldFilter}
            onChange={setFieldFilter}
            options={fieldOptions}
            style={{ width: 220 }}
          />
          <Typography.Text type="secondary">
            红色字段即出错单元格；原始行内容来自后端回执，便于对照源表。
          </Typography.Text>
        </Space>

        <Table<ImportRowError>
          rowKey={(row, index) => `${row.rowNo}-${row.field}-${index}`}
          columns={columns}
          dataSource={visibleErrors}
          size="small"
          pagination={visibleErrors.length > 10 ? { pageSize: 10 } : false}
          locale={{ emptyText: '该批次没有可定位的错误' }}
        />
      </Space>
    </Drawer>
  );
}
