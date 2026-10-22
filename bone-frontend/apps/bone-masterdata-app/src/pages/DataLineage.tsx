import React, { useCallback, useMemo, useState } from 'react';
import { Alert, Button, Card, Form, Input, Modal, Segmented, Space, Table, Tag } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import { AuthButton } from '@bone/ui';
import { BonePermissionCodes } from '@bone/shared-types';
import { lineageApi } from '../services/api';
import type { RecordLineageReq } from '../types/governance';
import { useMessage } from '../App';

/** 多跳展开的上限：后端只返回直接边，多层血缘由前端逐跳 BFS 拉取，须有界防止无限扩张。 */
const MAX_DEPTH = 3;
const MAX_NODES = 15;

interface LineageNode {
  name: string;
  layer: number;
}
interface LineageEdge {
  id: string;
  from: string;
  to: string;
  fromField?: string;
  toField?: string;
  transformType?: string;
  schemaName?: string;
}

/** 数据血缘：按实体查询上游/下游血缘边，多跳展开渲染分层血缘图；采集新边需治理写权限。 */
const DataLineage: React.FC = () => {
  const message = useMessage();
  const [direction, setDirection] = useState<'upstream' | 'downstream'>('upstream');
  const [entity, setEntity] = useState('');
  const [queried, setQueried] = useState<string>('');
  const [nodes, setNodes] = useState<LineageNode[]>([]);
  const [edges, setEdges] = useState<LineageEdge[]>([]);
  const [loading, setLoading] = useState(false);
  const [recordOpen, setRecordOpen] = useState(false);
  const [form] = Form.useForm();

  /**
   * 有界 BFS 展开血缘：upstream 从目标实体反向找来源，downstream 从来源实体正向找目标。
   * 节点按 name 去重（保留最小层），边按 id 去重；超过 MAX_NODES 即停止扩张。
   */
  const expand = useCallback(
    async (root: string, dir: 'upstream' | 'downstream') => {
      setLoading(true);
      try {
        const nodeMap = new Map<string, number>([[root, 0]]);
        const edgeMap = new Map<string, LineageEdge>();
        let frontier = [root];
        for (let depth = 0; depth < MAX_DEPTH && frontier.length > 0; depth++) {
          if (nodeMap.size >= MAX_NODES) break;
          const next: string[] = [];
          for (const cur of frontier) {
            const res =
              dir === 'upstream'
                ? await lineageApi.list({ targetEntity: cur })
                : await lineageApi.list({ sourceEntity: cur });
            for (const e of res.data ?? []) {
              const other = dir === 'upstream' ? e.sourceEntity : e.targetEntity;
              if (!nodeMap.has(other)) {
                nodeMap.set(other, depth + 1);
                next.push(other);
              }
              edgeMap.set(String(e.id), {
                id: String(e.id),
                from: e.sourceEntity,
                to: e.targetEntity,
                fromField: e.sourceField,
                toField: e.targetField,
                transformType: e.transformType,
                schemaName: e.schemaName
              });
            }
          }
          frontier = next;
        }
        setNodes([...nodeMap.entries()].map(([name, layer]) => ({ name, layer })));
        setEdges([...edgeMap.values()]);
        setQueried(root);
      } catch {
        message.error('加载血缘失败');
      } finally {
        setLoading(false);
      }
    },
    [message]
  );

  const search = () => {
    const root = entity.trim();
    if (!root) {
      message.error('请先输入实体编码');
      return;
    }
    void expand(root, direction);
  };

  const diagram = useMemo(() => {
    if (nodes.length === 0 || nodes.length > MAX_NODES) return null;
    const COL_W = 190;
    const ROW_H = 56;
    const PAD_X = 24;
    const PAD_Y = 20;
    const maxLayer = Math.max(...nodes.map((n) => n.layer));
    // 上游查询：来源在左、目标在右；下游查询：来源在左、目标在右（layer 即流向）
    const colOf = (n: LineageNode) => (direction === 'upstream' ? maxLayer - n.layer : n.layer);
    const byCol = new Map<number, LineageNode[]>();
    nodes.forEach((n) => {
      const c = colOf(n);
      byCol.set(c, [...(byCol.get(c) ?? []), n]);
    });
    const maxRows = Math.max(...[...byCol.values()].map((v) => v.length));
    const width = PAD_X * 2 + (maxLayer + 1) * COL_W;
    const height = PAD_Y * 2 + maxRows * ROW_H;
    const pos = new Map<string, { x: number; y: number }>();
    [...byCol.entries()].forEach(([col, list]) => {
      list.forEach((n, i) => {
        const y = PAD_Y + (i + (maxRows - list.length) / 2) * ROW_H + ROW_H / 2;
        pos.set(n.name, { x: PAD_X + col * COL_W, y });
      });
    });
    return { width, height, pos, maxLayer };
  }, [nodes, direction]);

  const recordLineage = async () => {
    let v: Record<string, unknown>;
    try {
      v = await form.validateFields();
    } catch {
      return;
    }
    try {
      await lineageApi.record({
        sourceEntity: v.sourceEntity as string,
        sourceField: v.sourceField as string | undefined,
        transformType: v.transformType as string | undefined,
        targetEntity: v.targetEntity as string,
        targetField: v.targetField as string | undefined,
        schemaName: v.schemaName as string | undefined
      } satisfies RecordLineageReq);
      message.success('血缘已记录');
      setRecordOpen(false);
      form.resetFields();
      if (queried) void expand(queried, direction);
    } catch {
      message.error('记录血缘失败');
    }
  };

  const columns = [
    {
      title: '来源',
      key: 'from',
      render: (_: unknown, e: LineageEdge) => (
        <span>
          <Tag color="blue">{e.from}</Tag>
          {e.fromField ? <span style={{ color: 'rgba(0,0,0,0.45)' }}>.{e.fromField}</span> : null}
        </span>
      )
    },
    { title: '转换', dataIndex: 'transformType', key: 't', render: (v?: string) => v ?? '—' },
    {
      title: '目标',
      key: 'to',
      render: (_: unknown, e: LineageEdge) => (
        <span>
          <Tag color="green">{e.to}</Tag>
          {e.toField ? <span style={{ color: 'rgba(0,0,0,0.45)' }}>.{e.toField}</span> : null}
        </span>
      )
    },
    { title: '库表模式', dataIndex: 'schemaName', key: 's', render: (v?: string) => v ?? '—' }
  ];

  return (
    <Card
      title="数据血缘"
      extra={
        <AuthButton
          code={BonePermissionCodes.MASTERDATA_GOVERNANCE_WRITE}
          type="primary"
          icon={<PlusOutlined />}
          onClick={() => setRecordOpen(true)}
        >
          记录血缘
        </AuthButton>
      }
    >
      <Space wrap style={{ marginBottom: 16 }}>
        <Segmented
          value={direction}
          onChange={(v) => setDirection(v as 'upstream' | 'downstream')}
          options={[
            { label: '查上游（谁生成我）', value: 'upstream' },
            { label: '查下游（我影响谁）', value: 'downstream' }
          ]}
        />
        <Input
          allowClear
          placeholder="实体编码，如 MATERIAL"
          style={{ width: 240 }}
          value={entity}
          onChange={(e) => setEntity(e.target.value)}
          onPressEnter={search}
        />
        <Button type="primary" onClick={search}>
          查询
        </Button>
      </Space>

      {queried && (
        <>
          {diagram ? (
            <Card size="small" title={`血缘图 · ${queried}（${direction === 'upstream' ? '上游' : '下游'}，最多 ${MAX_DEPTH} 跳）`} style={{ marginBottom: 16 }}>
              <svg viewBox={`0 0 ${diagram.width} ${diagram.height}`} style={{ width: '100%', height: 'auto' }} role="img">
                <defs>
                  <marker id="lineage-arrow" markerWidth="8" markerHeight="8" refX="7" refY="4" orient="auto">
                    <path d="M0,0 L8,4 L0,8 z" fill="#8c8c8c" />
                  </marker>
                </defs>
                {edges.map((e) => {
                  const p1 = diagram.pos.get(e.from);
                  const p2 = diagram.pos.get(e.to);
                  if (!p1 || !p2) return null;
                  const x1 = p1.x + 150;
                  const x2 = p2.x;
                  const midX = (x1 + x2) / 2;
                  return (
                    <g key={e.id}>
                      <line x1={x1} y1={p1.y} x2={x2} y2={p2.y} stroke="#8c8c8c" strokeWidth={1.5} markerEnd="url(#lineage-arrow)" />
                      {e.transformType ? (
                        <text x={midX} y={(p1.y + p2.y) / 2 - 6} textAnchor="middle" fontSize={11} fill="#8c8c8c">
                          {e.transformType}
                        </text>
                      ) : null}
                    </g>
                  );
                })}
                {nodes.map((n) => {
                  const p = diagram.pos.get(n.name)!;
                  const isRoot = n.name === queried;
                  return (
                    <g key={n.name}>
                      <rect
                        x={p.x}
                        y={p.y - 17}
                        width={150}
                        height={34}
                        rx={6}
                        fill={isRoot ? '#e6f4ff' : '#fafafa'}
                        stroke={isRoot ? '#1677ff' : '#d9d9d9'}
                        strokeWidth={isRoot ? 2 : 1}
                      />
                      <text x={p.x + 75} y={p.y + 4} textAnchor="middle" fontSize={12} fill={isRoot ? '#1677ff' : 'rgba(0,0,0,0.88)'}>
                        {n.name}
                      </text>
                    </g>
                  );
                })}
              </svg>
            </Card>
          ) : (
            <Alert
              type="info"
              showIcon
              style={{ marginBottom: 16 }}
              message={`实体较多（${nodes.length} 个），图已省略，请查看下方明细表`}
            />
          )}

          <Table<LineageEdge>
            rowKey="id"
            loading={loading}
            size="small"
            columns={columns}
            dataSource={edges}
            pagination={{ pageSize: 10, showTotal: (t) => `共 ${t} 条血缘边` }}
          />
        </>
      )}

      <Modal
        title="记录血缘边"
        open={recordOpen}
        onCancel={() => setRecordOpen(false)}
        destroyOnHidden
        onOk={() => void recordLineage()}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="sourceEntity" label="来源实体" rules={[{ required: true, message: '来源实体不能为空' }]}>
            <Input placeholder="如 MATERIAL" />
          </Form.Item>
          <Form.Item name="sourceField" label="来源字段">
            <Input placeholder="可选，如 code" />
          </Form.Item>
          <Form.Item name="transformType" label="转换类型">
            <Input placeholder="如 COPY / MAP / AGGREGATE" />
          </Form.Item>
          <Form.Item name="targetEntity" label="目标实体" rules={[{ required: true, message: '目标实体不能为空' }]}>
            <Input placeholder="如 PURCHASE_ORDER" />
          </Form.Item>
          <Form.Item name="targetField" label="目标字段">
            <Input placeholder="可选，如 material_code" />
          </Form.Item>
          <Form.Item name="schemaName" label="库表模式">
            <Input placeholder="可选，如 bone_mdm" />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
};

export default DataLineage;
