import React, { useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Empty,
  message,
  Select,
  Space,
  Spin,
  Tag,
  Typography,
} from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import {
  type DependencyGraphView,
  type DependencyEdge,
  type DependencyNode,
  type ExtPointRow,
  type StudioPageResult,
  formatStudioError,
  getDependencyGraph,
  listExtPoints,
} from '@/services/extensionApi';

const NODE_WIDTH = 170;
const NODE_HEIGHT = 60;
const COL_GAP = 80;
const ROW_GAP = 40;

type Layout = {
  nodes: (DependencyNode & { x: number; y: number; col: number })[];
  edges: (DependencyEdge & { fromX: number; fromY: number; toX: number; toY: number; toId?: number })[];
  width: number;
  height: number;
};

function computeLayout(graph: DependencyGraphView): Layout {
  const nameToId = new Map<string, number>();
  graph.nodes.forEach((n) => {
    if (n.name) nameToId.set(n.name, n.id);
  });
  const incomingByNode = new Map<number, number>();
  graph.nodes.forEach((n) => incomingByNode.set(n.id, 0));
  graph.edges.forEach((e) => {
    const toId = nameToId.get(e.toName);
    if (toId != null) {
      incomingByNode.set(toId, (incomingByNode.get(toId) ?? 0) + 1);
    }
  });

  const nodesById = new Map<number, DependencyNode>();
  graph.nodes.forEach((n) => nodesById.set(n.id, n));

  const visited = new Set<number>();
  const columns: number[][] = [];

  const roots = graph.nodes.filter((n) => (incomingByNode.get(n.id) ?? 0) === 0);
  const startNodes = roots.length > 0 ? roots : graph.nodes;
  startNodes.forEach((n) => {
    if (!visited.has(n.id)) {
      visited.add(n.id);
      if (!columns[0]) columns[0] = [];
      columns[0].push(n.id);
    }
  });

  const outAdj = new Map<number, Set<number>>();
  graph.edges.forEach((e) => {
    const toId = nameToId.get(e.toName);
    if (toId != null) {
      if (!outAdj.has(e.fromId)) outAdj.set(e.fromId, new Set());
      outAdj.get(e.fromId)!.add(toId);
    }
  });

  for (let depth = 0; depth < 16 && depth < columns.length; depth++) {
    const current = columns[depth];
    for (const id of current) {
      const targets = outAdj.get(id);
      if (!targets) continue;
      for (const tid of targets) {
        if (!visited.has(tid)) {
          visited.add(tid);
          if (!columns[depth + 1]) columns[depth + 1] = [];
          columns[depth + 1].push(tid);
        }
      }
    }
  }

  graph.nodes.forEach((n) => {
    if (!visited.has(n.id)) {
      visited.add(n.id);
      const last = columns.length > 0 ? columns.length - 1 : 0;
      if (!columns[last]) columns[last] = [];
      columns[last].push(n.id);
    }
  });

  const positioned = columns.flatMap((col, cIdx) =>
    col.map((id, rIdx) => ({
      ...(nodesById.get(id) as DependencyNode),
      col: cIdx,
      x: 40 + cIdx * (NODE_WIDTH + COL_GAP),
      y: 40 + rIdx * (NODE_HEIGHT + ROW_GAP),
    })),
  );
  const positionMap = new Map(positioned.map((n) => [n.id, n]));

  const edges = graph.edges.map((e) => {
    const from = positionMap.get(e.fromId);
    const toId = nameToId.get(e.toName);
    const to = toId != null ? positionMap.get(toId) : undefined;
    return {
      ...e,
      toId,
      fromX: from ? from.x + NODE_WIDTH : 0,
      fromY: from ? from.y + NODE_HEIGHT / 2 : 0,
      toX: to ? to.x : (from ? from.x + NODE_WIDTH + COL_GAP / 2 : 0),
      toY: to ? to.y + NODE_HEIGHT / 2 : (from ? from.y + NODE_HEIGHT / 2 : 0),
    };
  });

  const width = columns.length * (NODE_WIDTH + COL_GAP) + 40;
  const maxRows = columns.reduce((m, c) => Math.max(m, c.length), 1);
  const height = maxRows * (NODE_HEIGHT + ROW_GAP) + 40;
  return { nodes: positioned, edges, width, height };
}

const STATUS_COLOR: Record<string, string> = {
  ACTIVE: '#52c41a',
  STAGED: '#faad14',
  VALIDATED: '#13c2c2',
  UPLOADED: '#1677ff',
  REJECTED: '#ff4d4f',
  DEPRECATED: '#bfbfbf',
};

const DependencyGraph: React.FC = () => {
  const [graph, setGraph] = useState<DependencyGraphView | null>(null);
  const [loading, setLoading] = useState(false);
  const [extPointId, setExtPointId] = useState<number | undefined>(undefined);
  const [extPointOptions, setExtPointOptions] = useState<{ label: string; value: number }[]>([]);

  const load = async (filterPointId?: number) => {
    setLoading(true);
    try {
      const result = await getDependencyGraph(filterPointId);
      setGraph(result);
    } catch (e) {
      message.error(formatStudioError(e, '加载依赖图失败'));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    listExtPoints()
      .then((result) => {
        const rows: ExtPointRow[] = Array.isArray(result)
          ? result
          : (result as StudioPageResult<ExtPointRow>).records ?? [];
        setExtPointOptions(
          rows.map((p) => ({ value: p.id, label: p.name ?? p.interfaceName ?? `#${p.id}` })),
        );
      })
      .catch(() => undefined);
    load();
  }, []);

  const layout = useMemo(() => (graph ? computeLayout(graph) : null), [graph]);

  const orphanCount = useMemo(() => {
    if (!graph) return 0;
    return graph.edges.filter((e) => !e.resolved).length;
  }, [graph]);

  return (
    <Card>
      <Space style={{ marginBottom: 12 }} wrap>
        <Select<number>
          allowClear
          placeholder="按扩展点过滤"
          style={{ width: 260 }}
          options={extPointOptions}
          value={extPointId}
          onChange={(value) => {
            setExtPointId(value);
            load(value);
          }}
        />
        <Button icon={<ReloadOutlined />} onClick={() => load(extPointId)}>
          刷新
        </Button>
        {orphanCount > 0 ? (
          <Tag color="orange">{orphanCount} 个未解析依赖（外部名称）</Tag>
        ) : null}
      </Space>
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 12 }}
        message={
          <span>
            依赖来源：插件 <code>config_json.dependencies</code> 字段（按 <code>name</code> 匹配）。
            未解析的依赖以虚线表示，可能是同步顺序问题或外部依赖。
          </span>
        }
      />
      {loading ? (
        <div style={{ textAlign: 'center', padding: 48 }}>
          <Spin />
        </div>
      ) : !graph || graph.nodes.length === 0 ? (
        <Empty description="暂无插件依赖数据" />
      ) : (
        <div style={{ overflow: 'auto', border: '1px solid #f0f0f0', borderRadius: 4 }}>
          <svg width={layout?.width ?? 600} height={layout?.height ?? 200}>
            <defs>
              <marker
                id="arrow-resolved"
                viewBox="0 0 10 10"
                refX="10"
                refY="5"
                markerWidth="6"
                markerHeight="6"
                orient="auto-start-reverse"
              >
                <path d="M 0 0 L 10 5 L 0 10 z" fill="#1677ff" />
              </marker>
              <marker
                id="arrow-orphan"
                viewBox="0 0 10 10"
                refX="10"
                refY="5"
                markerWidth="6"
                markerHeight="6"
                orient="auto-start-reverse"
              >
                <path d="M 0 0 L 10 5 L 0 10 z" fill="#faad14" />
              </marker>
            </defs>
            {layout?.edges.map((e, idx) => (
              <line
                key={idx}
                x1={e.fromX}
                y1={e.fromY}
                x2={e.toX - 6}
                y2={e.toY}
                stroke={e.resolved ? '#1677ff' : '#faad14'}
                strokeWidth={1.5}
                strokeDasharray={e.resolved ? undefined : '4 4'}
                markerEnd={e.resolved ? 'url(#arrow-resolved)' : 'url(#arrow-orphan)'}
              />
            ))}
            {layout?.nodes.map((n) => (
              <g key={n.id} transform={`translate(${n.x},${n.y})`}>
                <rect
                  width={NODE_WIDTH}
                  height={NODE_HEIGHT}
                  rx={6}
                  ry={6}
                  fill="#fff"
                  stroke={n.enabled ? '#1677ff' : '#d9d9d9'}
                  strokeWidth={1.5}
                />
                <circle
                  cx={NODE_WIDTH - 14}
                  cy={14}
                  r={5}
                  fill={(n.deploymentStatus && STATUS_COLOR[n.deploymentStatus]) ?? '#bfbfbf'}
                />
                <text x={12} y={22} fontSize={13} fontWeight={600} fill="#1f1f1f">
                  {n.name?.slice(0, 18) ?? `#${n.id}`}
                </text>
                <text x={12} y={42} fontSize={11} fill="#666">
                  {(n.className ?? '').split('.').slice(-1)[0]?.slice(0, 22) ?? ''}
                </text>
              </g>
            ))}
          </svg>
        </div>
      )}
      <Typography.Paragraph type="secondary" style={{ marginTop: 12, fontSize: 12 }}>
        节点边框=启用态，右上角圆点=部署状态色。
      </Typography.Paragraph>
    </Card>
  );
};

export default DependencyGraph;
