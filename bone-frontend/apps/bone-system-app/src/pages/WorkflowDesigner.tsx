import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  Layout,
  Card,
  Button,
  Space,
  Input,
  Select,
  InputNumber,
  Tag,
  Drawer,
  Empty,
  Alert,
  Typography,
  Tooltip,
  message,
  Switch,
  Divider,
  Badge,
  Modal,
  Spin,
  Dropdown,
} from 'antd';
import {
  PlusOutlined,
  SaveOutlined,
  SendOutlined,
  EyeOutlined,
  DeleteOutlined,
  ExperimentOutlined,
  BranchesOutlined,
  AuditOutlined,
  AppstoreOutlined,
  ExportOutlined,
  SearchOutlined,
  FileTextOutlined,
  CopyOutlined,
  CheckCircleOutlined,
  PlayCircleOutlined,
  StopOutlined,
  UndoOutlined,
  RedoOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  ZoomInOutlined,
  ZoomOutOutlined,
  ThunderboltOutlined,
  SettingOutlined,
  AimOutlined,
  InboxOutlined,
  MoreOutlined,
} from '@ant-design/icons';
import {
  compile,
  type FlowDoc,
  type Step,
  type ApproveStep,
  type CcStep,
  type ConditionStep,
  type Branch,
  type AssignmentRule,
  type AssignmentStrategy,
  type ApprovalMode,
  type LayoutPoint,
  type Violation,
  type DefinitionNode,
  type DefinitionEdge,
  PARAM_KEY_BY_STRATEGY,
  groupViolationsByNode,
  hintOf,
  isPublishable,
} from '@bone/workflow-dsl';
import {
  workflowDesignerApi,
  docFromDefinitionJson,
  toWorkflowFailure,
  type WorkflowDefinitionSummary,
  type WorkflowPublished,
} from '@/services/workflowApi';
import { BonePermissionCodes, hasPermission } from '../auth/permission';
import { colors } from '@bone/ui';

const { Sider, Content } = Layout;
const { Text, Paragraph } = Typography;

const NODE_W = 216;
const NODE_H = 76;
const ROW_GAP = 118;
const COL_GAP = 260;
/** 画布四周留白：让「适应画布」不会把节点贴死在容器边缘。 */
const CANVAS_PAD = 180;
/** 只读预览的行列间距：留足分支标签的位置，比编辑态宽一档。 */
const PREVIEW_COL_GAP = 300;
const PREVIEW_ROW_GAP = 132;
/** 左上角给浮动节点库让出的空间：否则坐标原点上的发起节点会被浮层压住。 */
const CANVAS_INSET_X = 140;
const CANVAS_INSET_Y = 12;
const ZOOM_MIN = 0.4;
const ZOOM_MAX = 1.6;
const ZOOM_STEP = 0.1;

let codeSeq = 0;
const genCode = (prefix: string) => `${prefix}_${(++codeSeq).toString(36)}_${Date.now().toString(36).slice(-4)}`;

/** 从文档树计算默认画布坐标（未被拖拽覆盖时使用）。 */
function computeDefaultLayout(doc: FlowDoc): Record<string, LayoutPoint> {
  const layout: Record<string, LayoutPoint> = { START: { x: 0, y: 0 } };
  let y = ROW_GAP;

  const walk = (steps: Step[], depth: number) => {
    for (const step of steps) {
      layout[step.code] = { x: depth * COL_GAP, y };
      y += ROW_GAP;
      if (step.kind === 'condition') {
        for (const branch of step.branches) walk(branch.body, depth + 1);
        walk(step.defaultBranch.body, depth + 1);
      }
    }
  };
  walk(doc.steps, 0);
  layout.END = { x: 0, y };
  return layout;
}

const blankDoc = (key: string, name: string): FlowDoc => ({ key, name, steps: [] });

const emptyApprove = (code: string): ApproveStep => ({
  kind: 'approve',
  code,
  label: '审批节点',
  mode: 'SINGLE',
  rules: [],
});
const emptyCc = (code: string): CcStep => ({ kind: 'cc', code, label: '抄送节点', rules: [] });
const emptyCondition = (code: string): ConditionStep => ({
  kind: 'condition',
  code,
  label: '条件分支',
  branches: [{ id: genCode('b'), label: '分支 1', when: null, body: [] }],
  defaultBranch: { id: genCode('b'), label: '默认', when: null, body: [] },
});

/** 不可变地更新文档树里某个 code 对应的 step。 */
function updateStep(doc: FlowDoc, code: string, updater: (s: Step) => Step): FlowDoc {
  const map = (steps: Step[]): Step[] =>
    steps.map((s) => {
      if (s.code === code) return updater(s);
      if (s.kind === 'condition') {
        return {
          ...s,
          branches: s.branches.map((b) => ({ ...b, body: map(b.body) })),
          defaultBranch: { ...s.defaultBranch, body: map(s.defaultBranch.body) },
        };
      }
      return s;
    });
  return { ...doc, steps: map(doc.steps) };
}

function removeStep(doc: FlowDoc, code: string): FlowDoc {
  const filter = (steps: Step[]): Step[] =>
    steps
      .filter((s) => s.code !== code)
      .map((s) =>
        s.kind === 'condition'
          ? {
              ...s,
              branches: s.branches.map((b) => ({ ...b, body: filter(b.body) })),
              defaultBranch: { ...s.defaultBranch, body: filter(s.defaultBranch.body) },
            }
          : s,
      );
  return { ...doc, steps: filter(doc.steps) };
}

/**
 * 把 step 插到指定节点**之后**（同一层级）。
 *
 * <p>为什么不是「追加到末尾」：界面上新增节点的心理模型是「我选中了某一步，要在这步后面加一步」。
 * 旧实现一律 append 到主链末尾，于是想在第 1 步后插一个审批，得先加、再拖动、再删掉错位的那个——
 * 这是同类编辑器（n8n / 飞书审批）里最先被骂的交互。
 *
 * <p>{@code code == null} 表示没有选中任何节点：此时追加到主链末尾（与旧行为一致）。
 */
function insertAfter(doc: FlowDoc, code: string | null, step: Step): FlowDoc {
  if (code == null) return { ...doc, steps: [...doc.steps, step] };
  let done = false;
  const map = (steps: Step[]): Step[] => {
    const out: Step[] = [];
    for (const s of steps) {
      if (done) {
        out.push(s);
        continue;
      }
      if (s.code === code) {
        out.push(s, step);
        done = true;
        continue;
      }
      if (s.kind === 'condition') {
        // 注意顺序：先递归子分支，再落本节点——否则 done 标记会早于子分支生效。
        const branches = s.branches.map((b) => ({ ...b, body: map(b.body) }));
        const defaultBranch = { ...s.defaultBranch, body: map(s.defaultBranch.body) };
        out.push({ ...s, branches, defaultBranch });
        continue;
      }
      out.push(s);
    }
    return out;
  };
  const steps = map(doc.steps);
  // 目标节点不存在（并发改写 / 陈旧 selected）时兜底追加，不能让这次点击静默丢节点。
  return { ...doc, steps: done ? steps : [...steps, step] };
}

/** 收集文档树里全部 step 的 code（含条件分支内部）。 */
function collectStepCodes(steps: Step[]): string[] {
  const codes: string[] = [];
  for (const step of steps) {
    codes.push(step.code);
    if (step.kind === 'condition') {
      for (const branch of step.branches) codes.push(...collectStepCodes(branch.body));
      codes.push(...collectStepCodes(step.defaultBranch.body));
    }
  }
  return codes;
}

/** 结构统计：给右栏「流程设置」用，让用户一眼看到这个流程的复杂度。 */
function countSteps(steps: Step[]): { approve: number; cc: number; condition: number } {
  const acc = { approve: 0, cc: 0, condition: 0 };
  for (const s of steps) {
    if (s.kind === 'approve') acc.approve += 1;
    else if (s.kind === 'cc') acc.cc += 1;
    else {
      acc.condition += 1;
      const inner = countSteps([...s.branches.flatMap((b) => b.body), ...s.defaultBranch.body]);
      acc.approve += inner.approve;
      acc.cc += inner.cc;
      acc.condition += inner.condition;
    }
  }
  return acc;
}

/**
 * 正交折线路径：竖 → 横 → 竖，带圆角。
 *
 * <p>直线斜连在「分支汇合」的画法里会横穿其他节点卡片，节点一多就完全读不出层级。
 * 正交线是流程图（BPMN / 飞书审批）的默认视觉语言，且拐点位置可预测，便于在水平段挂分支标签。
 */
function edgePath(a: LayoutPoint, b: LayoutPoint): { d: string; mid: LayoutPoint } {
  // 同级节点之间的边（例如条件分支直接指向旁边的审批节点）：从右侧出、左侧入，走「横-竖-横」。
  // 若按上下走线，起点和终点在同一水平线上，只能画斜线或让标签压在节点上——实测就是这么糊成一团的。
  if (Math.abs(a.y - b.y) < 24 && Math.abs(a.x - b.x) > 1) {
    const dir = b.x > a.x ? 1 : -1;
    const x1 = dir > 0 ? a.x + NODE_W : a.x;
    const y1 = a.y + NODE_H / 2;
    const x2 = dir > 0 ? b.x : b.x + NODE_W;
    const y2 = b.y + NODE_H / 2;
    const mx = x1 + dir * 26;
    const r = Math.min(8, Math.abs(y2 - y1) / 2);
    const d =
      `M${x1},${y1} L${mx},${y1} L${mx},${y2 - r} Q${mx},${y2} ${mx + dir * r},${y2} L${x2},${y2}`;
    return { d, mid: { x: (x1 + x2) / 2, y: y1 } };
  }
  const x1 = a.x + NODE_W / 2;
  const y1 = a.y + NODE_H;
  const x2 = b.x + NODE_W / 2;
  const y2 = b.y;
  // 同一列（主链）：直接竖线，不引入无意义的折角。
  if (Math.abs(x1 - x2) < 1) return { d: `M${x1},${y1} L${x2},${y2}`, mid: { x: x1, y: (y1 + y2) / 2 } };
  // 行距太小（节点贴得太近）时退回直连，避免圆角半径算成负数画出畸形。
  if (y2 - y1 < 36) return { d: `M${x1},${y1} L${x2},${y2}`, mid: { x: (x1 + x2) / 2, y: (y1 + y2) / 2 } };
  const my = y1 + (y2 - y1) / 2;
  const dir = x2 > x1 ? 1 : -1;
  const r = Math.min(10, Math.abs(x2 - x1) / 2, (y2 - y1) / 2);
  const d =
    `M${x1},${y1} L${x1},${my - r} Q${x1},${my} ${x1 + dir * r},${my} ` +
    `L${x2 - dir * r},${my} Q${x2},${my} ${x2},${my + r} L${x2},${y2}`;
  return { d, mid: { x: (x1 + x2) / 2, y: my } };
}

/**
 * 只读预览用的分层布局。
 *
 * <p>为什么需要它：有些定义（多分支汇入同一节点）**无法**还原成文档树——树模型里同一个节点只能出现
 * 一次，而这类图里 `finance` 同时是两条分支的目标。硬还原会得到重复编码，编译直接失败，
 * 画布于是完全空白（比「显示得不准」糟得多）。这里退化为按 BFS 层级排布的真实图，只读展示。
 */
function layeredLayout(nodes: DefinitionNode[], edges: DefinitionEdge[]): Record<string, LayoutPoint> {
  const outs = new Map<string, string[]>();
  for (const e of edges) {
    const list = outs.get(e.from) ?? [];
    list.push(e.to);
    outs.set(e.from, list);
  }
  const startCode =
    nodes.find((n) => (n.type ?? '').toUpperCase() === 'START')?.code ?? nodes[0]?.code ?? null;
  const level = new Map<string, number>();
  if (startCode) {
    level.set(startCode, 0);
    const queue: string[] = [startCode];
    while (queue.length > 0) {
      const code = queue.shift() as string;
      const next = (level.get(code) ?? 0) + 1;
      for (const to of outs.get(code) ?? []) {
        if (!level.has(to)) {
          level.set(to, next);
          queue.push(to);
        }
      }
    }
  }
  const maxLevel = level.size > 0 ? Math.max(...level.values()) : 0;
  const rowOf = new Map<number, number>();
  const pos: Record<string, LayoutPoint> = {};
  for (const n of nodes) {
    const lv = level.get(n.code) ?? maxLevel + 1;
    const row = rowOf.get(lv) ?? 0;
    rowOf.set(lv, row + 1);
    // 间距比编辑态更宽：分支标签（胶囊）要能完整落在两列之间的缝里，否则会压住节点文字。
    pos[n.code] = { x: row * PREVIEW_COL_GAP, y: lv * PREVIEW_ROW_GAP };
  }
  return pos;
}

/**
 * 让设计器填满**宿主内容区**的可用高度。
 *
 * <p>实测（1600×1000 视口）：qiankun 里 {@code #root} 是 100vh，但祖先链上没有任何一层给子应用
 * 确定高度，{@code height:100%} 一路解析成 auto ⇒ 左栏 6 张模板卡把整页撑到 1160px，
 * 页面出现滚动条，而画布只分到 148px。设计器这类「工作台」页面必须自己占满视口、内部滚动，
 * 否则永远在跟宿主的文档流抢空间。
 */
function useFillHeight(rootRef: React.RefObject<HTMLElement | null>): number | undefined {
  const [height, setHeight] = useState<number | undefined>(undefined);
  useEffect(() => {
    const measure = () => {
      const el = rootRef.current;
      if (!el) return;
      // rect.top 会随页面滚动变化，加上 scrollY 换算回「文档坐标」，避免先滚后测算出偏大的高度。
      const docTop = el.getBoundingClientRect().top + window.scrollY;
      const parent = el.parentElement;
      const pb = parent ? parseFloat(getComputedStyle(parent).paddingBottom) || 0 : 0;
      setHeight(Math.max(460, Math.floor(window.innerHeight - docTop - (pb > 0 ? pb : 8))));
    };
    measure();
    window.addEventListener('resize', measure);
    // 宿主（shell 侧栏折叠、卡片挂载）布局稳定后会改变可用高度，补测一次。
    const timer = window.setTimeout(measure, 160);
    return () => {
      window.removeEventListener('resize', measure);
      window.clearTimeout(timer);
    };
  }, [rootRef]);
  return height;
}

/** 试运行报告的前端投影（与后端 {@code SimulationReport} 镜像的界面所需字段）。 */
interface SimReport {
  path?: string[];
  conditionTraces?: unknown[];
  warnings?: string[];
  error?: string | null;
}

/**
 * 画布状态快照：用于判断「有没有未保存的修改」。
 *
 * <p>比对的是**编译产物**而不是 state 对象引用：拖动一个节点只是改 layout，流程语义没变，但版式要保存，
 * 所以快照必须包含 layout；反过来，若拿原始 doc 比，编译选项变化（有损还原）也会被误判成「已修改」。
 * 编译失败返回 null（有损/半成品不参与脏判定，它们本来就禁止保存）。
 */
function snapshotOf(doc: FlowDoc, layout: Record<string, LayoutPoint>): string | null {
  try {
    return JSON.stringify(compile(doc, { layout: Object.keys(layout).length ? layout : undefined }));
  } catch {
    return null;
  }
}

/**
 * 二次确认（Promise 化）。
 *
 * <p>为什么不用 {@code Popconfirm}：需要在 async 流程中间（切换流程、载入历史版本、409 冲突）停下来问一句，
 * 而 Popconfirm 是绑定在某个按钮上的声明式组件，塞不进「已经在执行」的异步函数。
 */
function confirmDiscard(title: string, content: string, okText: string): Promise<boolean> {
  return new Promise((resolve) => {
    Modal.confirm({
      title,
      content,
      okText,
      cancelText: '继续编辑',
      okButtonProps: { danger: true },
      onOk: () => resolve(true),
      onCancel: () => resolve(false),
    });
  });
}

// ====================== 节点类型呈现 ======================

type NodeType = 'START' | 'APPROVE' | 'CONDITION' | 'CC' | 'END';

const NODE_META: Record<
  NodeType,
  { color: string; bg: string; label: string; Icon: React.ComponentType<{ style?: React.CSSProperties; className?: string }> }
> = {
  START: { color: colors.semantic.success, bg: colors.semantic.successBg, label: '发起', Icon: PlayCircleOutlined },
  APPROVE: { color: colors.primary[500], bg: colors.semantic.infoBg, label: '审批', Icon: CheckCircleOutlined },
  CONDITION: { color: colors.primary[700], bg: '#f9f0ff', label: '条件', Icon: BranchesOutlined },
  CC: { color: '#13c2c2', bg: '#e6fffb', label: '抄送', Icon: SendOutlined },
  END: { color: colors.gray[500], bg: colors.gray[100], label: '结束', Icon: StopOutlined },
};

const MODE_LABEL: Record<ApprovalMode, string> = {
  SINGLE: '单签',
  ALL: '会签',
  ANY: '或签',
  RATIO: '比例',
};

function nodeTypeOf(step: Step | null): NodeType {
  if (!step) return 'APPROVE';
  if (step.kind === 'condition') return 'CONDITION';
  if (step.kind === 'cc') return 'CC';
  return 'APPROVE';
}

function nodeSummary(step: Step | null): string {
  if (!step) return '';
  if (step.kind === 'approve') {
    const mode = MODE_LABEL[step.mode] ?? step.mode;
    const n = step.rules.length;
    return `${mode} · ${n} 条指派`;
  }
  if (step.kind === 'cc') return `抄送 · ${step.rules.length} 条`;
  if (step.kind === 'condition')
    return `分支 ${step.branches.length} + 默认`;
  return '';
}

// ====================== 审批模板（可复用 / 集成能力） ======================

function rule(strategy: AssignmentStrategy, params: Record<string, unknown> = {}): AssignmentRule {
  return { strategy, params };
}
function tApprove(label: string, mode: ApprovalMode, rules: AssignmentRule[]): ApproveStep {
  return { kind: 'approve', code: genCode('approve'), label, mode, rules };
}

interface WorkflowTemplate {
  id: string;
  name: string;
  desc: string;
  tags: string[];
  build: () => Step[];
}

/**
 * 内置审批模板库：覆盖企业最常见的几类审批场景。
 *
 * <p>这是「可被各种企业审批场景快速集成复用」的落点——运营 / 实施人员无需从零画线，
 * 选模板一键生成草稿并落库；也可以通过「导出 JSON」把定义带走，注册到任意租户 / 环境。
 * 模板只描述「结构骨架 + 指派策略」，不绑定具体角色码 / 字段码，落地时由用户在属性面板按需改。
 */
const TEMPLATES: WorkflowTemplate[] = [
  {
    id: 'general',
    name: '通用单人审批',
    desc: '提交 → 单人审批 → 结束，最轻量的审批流。',
    tags: ['通用', '轻量'],
    build: () => [tApprove('直属主管审批', 'SINGLE', [rule('LINE_MANAGER')])],
  },
  {
    id: 'multi',
    name: '多级审批',
    desc: '部门负责人 → 分管领导，逐级把关。',
    tags: ['通用', '多级'],
    build: () => [
      tApprove('部门负责人审批', 'SINGLE', [rule('DEPT_HEAD')]),
      tApprove('分管领导审批', 'SINGLE', [rule('LEVEL_UP', { level: 2 })]),
    ],
  },
  {
    id: 'expense',
    name: '报销审批',
    desc: '与 expense_reimbursement 实体对齐：财务 → 部门主管。',
    tags: ['财务', '报销'],
    build: () => [
      tApprove('财务复核', 'SINGLE', [rule('ROLE', { roleCode: 'FINANCE' })]),
      tApprove('部门主管审批', 'SINGLE', [rule('DEPT_HEAD')]),
    ],
  },
  {
    id: 'purchase',
    name: '采购审批',
    desc: '与 purchase_order 实体对齐：采购负责人审批后归档。',
    tags: ['采购', '供应链'],
    build: () => [tApprove('采购负责人审批', 'SINGLE', [rule('ROLE', { roleCode: 'PURCHASE_MANAGER' })]),
    ],
  },
  {
    id: 'countersign',
    name: '会签审批',
    desc: '多人并行审批，全部通过才算通过。',
    tags: ['会签', '并行'],
    build: () => [tApprove('会签审批', 'ALL', [rule('ROLE', { roleCode: 'APPROVER' })])],
  },
  {
    id: 'conditional',
    name: '条件分支审批',
    desc: '按金额分流：大额走总经理，默认走部门主管。',
    tags: ['条件', '金额'],
    build: () => [
      {
        kind: 'condition',
        code: genCode('condition'),
        label: '按金额分支',
        branches: [
          {
            id: genCode('b'),
            label: '大额 (≥5000)',
            when: { fieldCode: 'amount', operator: 'gte', value: 5000 },
            body: [tApprove('总经理审批', 'SINGLE', [rule('ROLE', { roleCode: 'GM' })])],
          },
        ],
        defaultBranch: {
          id: genCode('b'),
          label: '默认',
          when: null,
          body: [tApprove('部门主管审批', 'SINGLE', [rule('DEPT_HEAD')])],
        },
      },
    ],
  },
];

/**
 * 流程库列表项。
 *
 * <p>独立成组件而不是内联 map：hover 态需要本地 state，写在内联 map 里就只能靠 CSS 类，
 * 而本项目没有为该页引入样式文件（antd + inline style 的既有风格）。
 */
const FlowListItem: React.FC<{
  item: WorkflowDefinitionSummary;
  active: boolean;
  onOpen: () => void;
}> = ({ item, active, onOpen }) => {
  const [hover, setHover] = useState(false);
  return (
    <div
      role="button"
      tabIndex={0}
      onClick={onOpen}
      onKeyDown={(e) => {
        if (e.key === 'Enter') onOpen();
      }}
      onMouseEnter={() => setHover(true)}
      onMouseLeave={() => setHover(false)}
      title={`${item.defName || item.defKey}（${item.defKey}）`}
      style={{
        padding: '8px 10px',
        borderRadius: 8,
        cursor: 'pointer',
        marginBottom: 2,
        background: active ? colors.primary[50] : hover ? colors.gray[100] : 'transparent',
        border: `1px solid ${active ? colors.primary[200] : 'transparent'}`,
        outline: 'none',
      }}
    >
      <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
        <span
          style={{
            width: 6,
            height: 6,
            borderRadius: 3,
            flexShrink: 0,
            background: item.enabled ? colors.semantic.success : colors.gray[400],
          }}
        />
        <span
          style={{
            flex: 1,
            minWidth: 0,
            fontSize: 13,
            fontWeight: active ? 600 : 500,
            lineHeight: '18px',
            whiteSpace: 'nowrap',
            overflow: 'hidden',
            textOverflow: 'ellipsis',
          }}
        >
          {item.defName || item.defKey}
        </span>
        {item.version > 0 ? (
          <Tag style={{ marginInlineEnd: 0, fontSize: 11, lineHeight: '16px' }} color={active ? 'blue' : undefined}>
            v{item.version}
          </Tag>
        ) : (
          <Tag style={{ marginInlineEnd: 0, fontSize: 11, lineHeight: '16px' }} color="orange">
            草稿
          </Tag>
        )}
      </div>
      <div
        style={{
          fontSize: 11,
          color: colors.gray[500],
          paddingLeft: 12,
          marginTop: 2,
          whiteSpace: 'nowrap',
          overflow: 'hidden',
          textOverflow: 'ellipsis',
        }}
      >
        {item.defKey}
        {item.version > 0 && !item.enabled && (
          <span style={{ marginLeft: 6, color: colors.gray[400] }}>· 已下线</span>
        )}
      </div>
    </div>
  );
};

// ====================== 组件 ======================

const WorkflowDesigner: React.FC = () => {
  const [defKey, setDefKey] = useState('');
  const [defName, setDefName] = useState('');
  const [doc, setDoc] = useState<FlowDoc | null>(null);
  const [layout, setLayout] = useState<Record<string, LayoutPoint>>({});
  const [selected, setSelected] = useState<string | null>(null);
  const [revision, setRevision] = useState<number | null>(null);
  const [draftExists, setDraftExists] = useState(false);
  /** 上一次「读入 / 保存」时的画布快照，与当前快照比对即得 dirty。 */
  const [savedSnapshot, setSavedSnapshot] = useState<string | null>(null);
  const [lossy, setLossy] = useState(false);
  /** 旧定义结构重建时的告警（来自 {@code parse()}），要在界面上明确告诉用户「结构是推断的」。 */
  const [loadWarnings, setLoadWarnings] = useState<string[]>([]);
  /** 服务端定义原文里的 nodes/edges：树还原失败时用它做只读图预览，避免空白画布。 */
  const [rawDefinition, setRawDefinition] = useState<{
    nodes: DefinitionNode[];
    edges: DefinitionEdge[];
  } | null>(null);
  const [violations, setViolations] = useState<Violation[]>([]);
  const [simReport, setSimReport] = useState<SimReport | null>(null);
  const [defList, setDefList] = useState<WorkflowDefinitionSummary[]>([]);
  /**
   * 本次会话里「新建但还没发布」的 key。
   *
   * <p>服务端 `/definitions` 只返回**已发布**的定义，于是刚从模板创建的流程在流程库里根本没有条目——
   * 用户一离开页面就再也点不回来（只能靠记住 key 重新输入）。这里把会话内新建的 key 合进列表并标注「草稿」，
   * 至少让当前会话内的操作是自洽的。
   */
  const [sessionKeys, setSessionKeys] = useState<string[]>([]);
  const [versions, setVersions] = useState<WorkflowPublished[]>([]);
  const [versionDrawer, setVersionDrawer] = useState(false);
  const [filterText, setFilterText] = useState('');
  const [loading, setLoading] = useState(false);
  const [drag, setDrag] = useState<{
    code: string;
    originX: number;
    originY: number;
    px: number;
    py: number;
    moved: boolean;
  } | null>(null);
  const [tplModal, setTplModal] = useState<{ open: boolean; tpl: WorkflowTemplate | null; key: string; name: string }>({
    open: false,
    tpl: null,
    key: '',
    name: '',
  });
  const [exportJson, setExportJson] = useState<string | null>(null);
  /** 模板画廊 / 新建流程：把「从模板开始」与「空流程」两种入口从常驻侧栏里拆出来。 */
  const [tplGallery, setTplGallery] = useState(false);
  const [createModal, setCreateModal] = useState<{ open: boolean; key: string; name: string }>({
    open: false,
    key: '',
    name: '',
  });
  const [siderCollapsed, setSiderCollapsed] = useState(false);
  const [panelCollapsed, setPanelCollapsed] = useState(false);
  /**
   * 窄屏收敛（<1500）：1440 笔记本上 8 个动作 + 流程名 + 草稿标签已经互相挤压（实测流程名被截）。
   * 收敛后次要动作收进「更多」菜单，主操作（保存 / 发布）保留文案，侧栏宽度同步让位给画布。
   */
  const [compact, setCompact] = useState(false);
  useEffect(() => {
    const sync = () => setCompact(window.innerWidth < 1500);
    sync();
    window.addEventListener('resize', sync);
    return () => window.removeEventListener('resize', sync);
  }, []);
  /** 画布缩放：同类编辑器（Figma / n8n / Camunda）标配，没有它超过 6 个节点就只能靠滚轮找。 */
  const [scale, setScale] = useState(1);
  const containerRef = useRef<HTMLDivElement>(null);
  const rootRef = useRef<HTMLDivElement>(null);
  /** 拖拽开始前的版式：mouseup 时作为撤销目标（拖动过程中每帧都改 layout，不能逐帧留痕）。 */
  const beforeDragRef = useRef<Record<string, LayoutPoint>>({});
  const fillHeight = useFillHeight(rootRef);

  // ---- 撤销 / 重做 ----
  /** 历史栈只存「文档 + 版式 + 选中」，三者共同决定画布呈现；60 步足够覆盖一次完整编排。 */
  type Snap = { doc: FlowDoc | null; layout: Record<string, LayoutPoint>; selected: string | null };
  const pastRef = useRef<Snap[]>([]);
  const futureRef = useRef<Snap[]>([]);
  /** 属性面板连续打字时合并历史，否则一次改名会灌进十几个撤销步。 */
  const lastEditRef = useRef<{ key: string; at: number } | null>(null);
  /** 撤销/重做按钮的可用态（历史栈在 ref 里，改完需要主动同步一次渲染）。 */
  const [histState, setHistState] = useState({ canUndo: false, canRedo: false });

  /**
   * 写权限（UX 门禁）：没有 workflow:definitions:write 时，保存/发布/丢弃/上下线全部禁用，
   * 画布仍可查看、校验与试运行——「能看不能改」比「整页 403」更接近真实分权场景（评审者、业务方）。
   * 真正的授权在后端 WorkflowDesignerAuthorizer，这里只避免让用户点出一个必然失败的请求。
   */
  const canWrite = useMemo(
    () => hasPermission(BonePermissionCodes.WORKFLOW_DEFINITIONS_WRITE),
    [],
  );

  /**
   * 默认坐标（只依赖文档结构，与拖拽覆盖无关）。
   *
   * <p>必须 memo：它此前在<b>每个节点、每条边</b>的渲染里都被调用一次，100 个节点就是 100 次全树遍历 + 100 份对象；
   * 拖动时每次 mousemove 都重算，画布越复杂越涩，而症状表现为「节点一多就卡」，很难归因到渲染函数里的一行取值。
   */
  const defaultLayout = useMemo<Record<string, LayoutPoint>>(
    () => (doc ? computeDefaultLayout(doc) : {}),
    [doc],
  );

  /**
   * 生效版式 = 默认版式（按文档结构算）+ 用户拖拽覆盖。
   *
   * <p>必须在这里合并，而不是在渲染时各取一次：`layout` 只记录「拖过的节点」，一旦把这张**局部**表
   * 原样序列化（保存）再读回来，未拖过的节点位置就冻在旧结构上——实测症状是「新增一个节点后保存重开，
   * 结束节点压在别的节点身上」。合并后编译/保存/渲染共用同一份完整坐标。
   */
  const effectiveLayout = useMemo<Record<string, LayoutPoint>>(
    () => ({ ...defaultLayout, ...layout }),
    [defaultLayout, layout],
  );

  const compiled = useMemo(() => {
    if (!doc) return null;
    try {
      return compile(doc, { layout: effectiveLayout });
    } catch {
      return null;
    }
  }, [doc, effectiveLayout]);

  const violationByNode = useMemo(() => groupViolationsByNode(violations), [violations]);

  /** 画布当前快照 / 未保存标记（比的是编译产物，见 {@link snapshotOf}）。 */
  const currentSnapshot = useMemo(() => (doc ? snapshotOf(doc, effectiveLayout) : null), [doc, effectiveLayout]);
  const dirty = currentSnapshot !== null && currentSnapshot !== savedSnapshot;

  // ---------- 历史栈 ----------
  const snap = useCallback<() => Snap>(() => ({ doc, layout, selected }), [doc, layout, selected]);

  const syncHist = useCallback(() => {
    setHistState({ canUndo: pastRef.current.length > 0, canRedo: futureRef.current.length > 0 });
  }, []);

  const pushPast = useCallback(
    (s: Snap) => {
      pastRef.current.push(s);
      if (pastRef.current.length > 60) pastRef.current.shift();
      // 新改动一定让「重做」失效——否则重做会把用户刚做的编辑覆盖回旧分支。
      futureRef.current = [];
      syncHist();
    },
    [syncHist],
  );

  /**
   * 统一的文档变更入口：留痕 + 落状态。
   *
   * <p>为什么强制走这里：旧代码里 {@code setDoc} 散落在 6 处，撤销/重做只能在每处补一遍，
   * 漏一处就出现「这个操作撤不回去」的诡异体验。收敛成一个入口后，「哪些操作可撤销」是结构性的，不靠自觉。
   */
  const applyDoc = useCallback(
    (
      next: FlowDoc,
      opts?: { layout?: Record<string, LayoutPoint>; select?: string | null; coalesce?: string },
    ) => {
      const now = Date.now();
      const key = opts?.coalesce;
      const merging = !!key && !!lastEditRef.current && lastEditRef.current.key === key && now - lastEditRef.current.at < 700;
      if (!merging) pushPast(snap());
      lastEditRef.current = key ? { key, at: now } : null;
      setDoc(next);
      if (opts?.layout) setLayout(opts.layout);
      if (opts?.select !== undefined) setSelected(opts.select);
    },
    [snap, pushPast],
  );

  const resetHistory = useCallback(() => {
    pastRef.current = [];
    futureRef.current = [];
    lastEditRef.current = null;
    syncHist();
  }, [syncHist]);

  const undo = useCallback(() => {
    const prev = pastRef.current.pop();
    if (!prev) return;
    futureRef.current.push(snap());
    lastEditRef.current = null;
    setDoc(prev.doc);
    setLayout(prev.layout);
    setSelected(prev.selected);
    syncHist();
  }, [snap, syncHist]);

  const redo = useCallback(() => {
    const next = futureRef.current.pop();
    if (!next) return;
    pastRef.current.push(snap());
    lastEditRef.current = null;
    setDoc(next.doc);
    setLayout(next.layout);
    setSelected(next.selected);
    syncHist();
  }, [snap, syncHist]);

  /**
   * 失败统一呈现（联调实测：拦截器在非 2xx 时 reject，不 catch 就完全没有反馈）。
   *
   * <p>三个分支各有不同处置：403 说明授权没接或没权限，只能提示；409 是并发写冲突，必须重新拉草稿
   * 否则后续每次保存都会继续 409；422 是发布 fail-closed，要把后端回传的违规清单直接灌进画布高亮。
   */
  const reportFailure = useCallback(
    (error: unknown, scene: string, onConflict?: () => void): boolean => {
      const failure = toWorkflowFailure(error);
      switch (failure.kind) {
        case 'unauthenticated':
        case 'forbidden':
          message.error(`${scene}失败：无权限（${failure.message}）`);
          break;
        case 'conflict':
          message.warning(`${scene}失败：${failure.message}`);
          onConflict?.();
          break;
        case 'invalid':
          if (failure.violations.length) setViolations(failure.violations);
          message.error(
            `${scene}失败：存在 ${failure.violations.length} 处违规（已在画布高亮），请修正后重试`,
          );
          break;
        case 'network':
          message.error(`${scene}失败：网络异常，请检查后端是否可达`);
          break;
        default:
          message.error(`${scene}失败：${failure.message}`);
      }
      return false;
    },
    [],
  );

  const refreshList = useCallback(async () => {
    try {
      const res = await workflowDesignerApi.listLatest();
      if (res.code === 200) setDefList(res.data ?? []);
    } catch (error) {
      reportFailure(error, '加载流程定义列表');
    }
  }, [reportFailure]);

  useEffect(() => {
    void refreshList();
  }, [refreshList]);

  /**
   * 把服务端的定义原文装进画布，并把结果记为「已保存」基线。
   *
   * <p>三条载入路径（草稿 / 最新已发布版 / 空白新建）共用它：此前是三段近乎复制粘贴的代码，各自 setDoc +
   * setLayout，基线维护一旦漏掉一处，那条路径就会永远显示「未保存」或者反过来把修改当已保存。
   */
  const applyLoaded = useCallback(
    (key: string, definitionJson: string, fallbackName: string) => {
      const loaded = docFromDefinitionJson(definitionJson);
      const parsed = loaded.doc;
      let uiLayout: Record<string, LayoutPoint> | undefined;
      try {
        const raw = JSON.parse(definitionJson) as {
          ui?: { layout?: Record<string, LayoutPoint> };
          nodes?: DefinitionNode[];
          edges?: DefinitionEdge[];
        };
        uiLayout = raw.ui?.layout;
        setRawDefinition(
          Array.isArray(raw.nodes) && raw.nodes.length > 0
            ? { nodes: raw.nodes, edges: raw.edges ?? [] }
            : null,
        );
      } catch {
        // 原文不可解析：由 parsed == null 的有损分支兜住
      }
      const nextDoc = parsed ?? blankDoc(key, fallbackName);
      /**
       * 坐标只在「与结构一一对应」时才可信。
       *
       * <p>`ui.layout` 是保存那一刻的**完整**快照；之后只要增删过节点，这份表就过期了——旧实现在这里
       * 无条件采用，于是结束节点停在中途、新节点被压在别人身上（本次实测的真实缺陷）。
       * 判据很直接：坐标表的 key 集合必须恰好等于当前结构的 key 集合。
       */
      const structCodes = new Set(['START', 'END', ...collectStepCodes(nextDoc.steps)]);
      const layoutCodes = Object.keys(uiLayout ?? {});
      // 光「key 集合一致」还不够：结束节点必须位于最下方，否则就是旧快照（结束停在中途）。
      const endBelowAll =
        !!uiLayout?.END &&
        layoutCodes.every(
          (c) => c === 'END' || (uiLayout as Record<string, LayoutPoint>)[c].y < (uiLayout as Record<string, LayoutPoint>).END.y,
        );
      const layoutFresh =
        !!uiLayout &&
        layoutCodes.length === structCodes.size &&
        layoutCodes.every((c) => structCodes.has(c)) &&
        endBelowAll;
      const nextLayout = layoutFresh ? (uiLayout as Record<string, LayoutPoint>) : computeDefaultLayout(nextDoc);
      setDoc(nextDoc);
      setLayout(nextLayout);
      /**
       * 基线必须按**渲染时实际用的版式**算：`dirty` 比的是 `{...defaultLayout, ...layout}`，
       * 这里若只拿 `nextLayout` 建基线，只要 `ui.layout` 少一个 key（旧快照），一打开就恒显示「未保存」。
       */
      setSavedSnapshot(snapshotOf(nextDoc, { ...computeDefaultLayout(nextDoc), ...nextLayout }));
      setLossy(loaded.lossy || !parsed);
      setLoadWarnings(loaded.warnings);
      setSelected(null);
      // 换了一份文档，历史栈必须清空：跨文档撤销会把 A 流程的节点贴进 B 流程，属于数据损坏而非「撤销」。
      resetHistory();
    },
    [resetHistory],
  );

  const openKey = useCallback(
    async (key: string, name?: string, force = false) => {
      // force 只给「用户已在上层确认过」的调用方用（409 处置），否则会二次弹窗。
      if (!force && dirty) {
        const proceed = await confirmDiscard(
          '当前画布有未保存的修改',
          `打开「${key}」会丢弃画布上未保存的内容，确定继续？`,
          '放弃修改并打开',
        );
        if (!proceed) return;
      }
      setDefKey(key);
      if (name) setDefName(name);
      setSelected(null);
      setViolations([]);
      setSimReport(null);
      setLoading(true);
      try {
        const draft = await workflowDesignerApi.findDraft(key);
        if (draft.code === 200 && draft.data) {
          setDraftExists(true);
          setRevision(draft.data.revision);
          applyLoaded(key, draft.data.definitionJson, draft.data.defName);
          setDefName(draft.data.defName || name || key);
          return;
        }
        // 无草稿：尝试取已发布最新版还原（flowDoc 在 ui 段）。后端按版本号降序返回，data[0] 即最新版。
        const vers = await workflowDesignerApi.listVersions(key);
        if (vers.code === 200 && vers.data && vers.data.length > 0) {
          const latest = vers.data[0];
          applyLoaded(key, latest.definitionJson, latest.defName);
          setDraftExists(false);
          setRevision(null);
          return;
        }
        const fresh = blankDoc(key, name ?? key);
        const freshLayout = computeDefaultLayout(fresh);
        setDoc(fresh);
        setLayout(freshLayout);
        setSavedSnapshot(snapshotOf(fresh, freshLayout));
        setLossy(false);
        setLoadWarnings([]);
        setRawDefinition(null);
        setDraftExists(false);
        setRevision(null);
        resetHistory();
      } catch (error) {
        // 读不到草稿/版本时不能把界面留在空白态：给出空文档让人可继续编辑，同时说明原因。
        reportFailure(error, '打开流程定义');
        const fresh = blankDoc(key, name ?? key);
        const freshLayout = computeDefaultLayout(fresh);
        setDoc(fresh);
        setLayout(freshLayout);
        setSavedSnapshot(snapshotOf(fresh, freshLayout));
        setLossy(false);
        setLoadWarnings([]);
        setRawDefinition(null);
        setDraftExists(false);
        setRevision(null);
        resetHistory();
      } finally {
        setLoading(false);
      }
    },
    [applyLoaded, dirty, reportFailure, resetHistory],
  );

  /** 只把 revision 基线对齐到服务端，**不动画布**——用户选择「保留本地修改」时用。 */
  const syncRevision = useCallback(
    async (key: string) => {
      try {
        const draft = await workflowDesignerApi.findDraft(key);
        if (draft.code === 200 && draft.data) {
          setDraftExists(true);
          setRevision(draft.data.revision);
        } else {
          // 对方把草稿发布或丢弃了：本地内容此时没有基线，下次保存会以新建草稿落库。
          setDraftExists(false);
          setRevision(null);
        }
      } catch (error) {
        reportFailure(error, '同步草稿版本');
      }
    },
    [reportFailure],
  );

  /**
   * 409 的统一处置：必须让用户显式选，不能替他决定。
   *
   * <p>上一版实现是「冲突就重新拉草稿」——它解决了「revision 永远落后、每次保存都 409」，
   * 代价是把「不再报错」换成了「悄悄丢掉用户画了半天的内容」，而这比 409 本身严重得多。现在分两种：
   * 无本地修改时直接拉（无损）；有本地修改时二选一——载入服务器版本，或保留本地但把 revision 对齐
   * （下次保存会覆盖对方，属于知情下的覆盖）。
   */
  const handleConflict = useCallback(
    async (key: string) => {
      if (!dirty) {
        void openKey(key, undefined, true);
        return;
      }
      const takeServer = await new Promise<boolean>((resolve) => {
        Modal.confirm({
          title: '草稿已被他人修改',
          content:
            '载入服务器上的最新草稿会丢弃你当前画布上未保存的修改；选择「保留本地修改」则只对齐版本号，下次保存会覆盖对方的内容。',
          okText: '载入服务器版本',
          cancelText: '保留本地修改',
          okButtonProps: { danger: true },
          onOk: () => resolve(true),
          onCancel: () => resolve(false),
        });
      });
      if (takeServer) {
        void openKey(key, undefined, true);
        return;
      }
      await syncRevision(key);
      message.info('已对齐到最新 revision；再次保存将覆盖对方内容');
    },
    [dirty, openKey, syncRevision],
  );

  const onValidate = useCallback(async () => {
    if (!compiled) return;
    try {
      const res = await workflowDesignerApi.validate(JSON.stringify(compiled));
      if (res.code === 200) {
        const list = res.data?.violations ?? [];
        setViolations(list);
        if (isPublishable(list)) message.success('校验通过，可发布');
        else message.warning(`存在 ${list.length} 处问题`);
      }
    } catch (error) {
      reportFailure(error, '校验');
    }
  }, [compiled, reportFailure]);

  const onSaveDraft = useCallback(async () => {
    if (!doc || !defKey) {
      message.error('请先指定流程 key');
      return;
    }
    if (lossy) {
      message.error('当前为旧数据有损还原，请补全节点后再保存');
      return;
    }
    const def = compile(doc, { layout: effectiveLayout });
    try {
      const res = await workflowDesignerApi.saveDraft(defKey, JSON.stringify(def), revision);
      if (res.code === 200 && res.data) {
        setRevision(res.data.draft.revision);
        setDraftExists(true);
        setViolations(res.data.violations);
        // 保存成功了才推进基线，否则「已保存」标记会先于落库出现，用户以为存住了其实没有。
        setSavedSnapshot(JSON.stringify(def));
        message.success(`草稿已保存（revision=${res.data.draft.revision}）`);
      }
    } catch (error) {
      // 409 = 别人的 revision 更新了：交给 handleConflict 让用户选「载入服务器版本」还是「保留本地」。
      reportFailure(error, '保存草稿', () => void handleConflict(defKey));
    }
  }, [doc, defKey, lossy, effectiveLayout, revision, reportFailure, handleConflict]);

  /**
   * 「有损还原」的出路：把按图推断出来的结构**显式**固化成草稿。
   *
   * <p>为什么需要它：种子数据 / 迁移导入的定义没有 `ui.flowDoc`，只能靠可达性推断分支结构。推断结果可能有偏差，
   * 所以默认禁止保存（否则一次随手保存就把分支抹平）。但如果没有出口，这类流程就永远无法在设计器里编辑——
   * 用户只能看着它、不能改它。这里让用户「核对后确认」，把推断结果落成草稿，从此走无损路径。
   */
  const onAdoptLossy = useCallback(async () => {
    if (!doc || !defKey) return;
    const ok = await new Promise<boolean>((resolve) => {
      Modal.confirm({
        title: '以当前结构重建草稿？',
        content:
          '当前结构是从旧定义按图推断的（原定义未携带可视化信息）。确认前请在画布上核对分支与审批人配置；' +
          '重建后会存成新草稿并补齐可视化信息，之后即可正常编辑与发布。',
        okText: '核对无误，重建草稿',
        cancelText: '再看看',
        okButtonProps: { danger: true },
        onOk: () => resolve(true),
        onCancel: () => resolve(false),
      });
    });
    if (!ok) return;
    try {
      const def = compile(doc, { layout: effectiveLayout });
      const res = await workflowDesignerApi.saveDraft(
        defKey,
        JSON.stringify(def),
        draftExists ? revision : null,
      );
      if (res.code === 200 && res.data) {
        setRevision(res.data.draft.revision);
        setDraftExists(true);
        setViolations(res.data.violations ?? []);
        setSavedSnapshot(JSON.stringify(def));
        setLossy(false);
        setLoadWarnings([]);
        message.success('已按当前结构重建草稿，现在可以正常编辑与发布');
      }
    } catch (error) {
      if (error instanceof Error && error.name === 'FlowDocCompileError') {
        message.error(`无法重建：${error.message}（该结构不能表达为流程树）`);
        return;
      }
      reportFailure(error, '重建草稿', () => void handleConflict(defKey));
    }
  }, [doc, defKey, effectiveLayout, draftExists, revision, reportFailure, handleConflict]);

  const onPublish = useCallback(async () => {
    if (!doc || !defKey) return;
    // 发布的对象是「库里的草稿」，不是画布。没有草稿时先保存——否则后端会回 409「没有草稿，无法发布」，
    // 而用户刚点完「发布」看到「没有草稿」只会以为是权限或系统问题。
    if (!draftExists) {
      message.warning('画布内容还没有存成草稿，请先点「保存草稿」再发布');
      return;
    }
    const def = compile(doc, { layout: effectiveLayout });
    try {
      // 前端预检只为省一次往返，最终以服务端 fail-closed 结论为准（下面 422 分支会接收后端回传的违规）。
      const vRes = await workflowDesignerApi.validate(JSON.stringify(def));
      const localViolations = vRes.data?.violations ?? [];
      if (vRes.code !== 200 || !isPublishable(localViolations)) {
        setViolations(localViolations);
        message.error('存在违规，拒绝发布（fail-closed）');
        return;
      }
      setViolations([]);
      const res = await workflowDesignerApi.publish(defKey, revision);
      if (res.code === 200) {
        message.success(`已发布 v${res.data?.version}`);
        setDraftExists(false);
        setRevision(null);
        // 草稿已被转成不可变版本，画布内容与库里一致，故推进基线（否则会一直显示「未保存」）。
        setSavedSnapshot(JSON.stringify(def));
        void refreshList();
      }
    } catch (error) {
      reportFailure(error, '发布', () => void handleConflict(defKey));
    }
  }, [doc, defKey, draftExists, effectiveLayout, revision, refreshList, reportFailure, handleConflict]);

  const onSimulate = useCallback(async () => {
    if (!compiled) return;
    try {
      const res = await workflowDesignerApi.simulate({
        definitionJson: JSON.stringify(compiled),
        sampleData: {},
        initiatorId: 'sim-user',
      });
      if (res.code === 200) setSimReport(res.data ?? null);
    } catch (error) {
      reportFailure(error, '试运行');
    }
  }, [compiled, reportFailure]);

  const onDiscardDraft = useCallback(async () => {
    if (!defKey) return;
    try {
      await workflowDesignerApi.discardDraft(defKey);
      setDraftExists(false);
      setRevision(null);
      setViolations([]);
      // 草稿已从库里删除，画布内容此刻「哪里都没存」，基线必须清空让它显示为未保存。
      setSavedSnapshot(null);
      message.success('草稿已丢弃');
    } catch (error) {
      reportFailure(error, '丢弃草稿');
    }
  }, [defKey, reportFailure]);

  const onOpenVersions = useCallback(async () => {
    setVersionDrawer(true);
    if (!defKey) return;
    try {
      const res = await workflowDesignerApi.listVersions(defKey);
      if (res.code === 200) setVersions(res.data ?? []);
    } catch (error) {
      reportFailure(error, '加载版本列表');
    }
  }, [defKey, reportFailure]);

  const onToggleEnabled = useCallback(
    async (version: number, enabled: boolean) => {
      if (!defKey) return;
      try {
        await workflowDesignerApi.setEnabled(defKey, version, enabled);
        const res = await workflowDesignerApi.listVersions(defKey);
        if (res.code === 200) setVersions(res.data ?? []);
        void refreshList();
        message.success(enabled ? `v${version} 已上线` : `v${version} 已下线`);
      } catch (error) {
        reportFailure(error, enabled ? '上线版本' : '下线版本');
      }
    },
    [defKey, refreshList, reportFailure],
  );

  /**
   * 把历史版本载入画布。
   *
   * <p>必须确认：载入是**覆盖画布**的动作，而覆盖之后只有「保存草稿」才会落库——用户很容易以为「载入了就是切过去了」，
   * 于是接着改了别的地方再保存，把想保留的草稿冲掉。故这里明确提示「保存后将覆盖当前草稿」。
   */
  const loadVersion = useCallback(
    async (v: WorkflowPublished) => {
      if (dirty) {
        const proceed = await confirmDiscard(
          `载入 v${v.version} 会覆盖当前画布`,
          '当前画布有未保存的修改，载入历史版本会丢弃它们。注意：载入后保存将覆盖现有草稿。',
          '载入并丢弃修改',
        );
        if (!proceed) return;
      }
      applyLoaded(v.defKey, v.definitionJson, v.defName);
      setVersionDrawer(false);
      message.info(`已载入 v${v.version} 到画布；保存后将成为草稿内容`);
    },
    [applyLoaded, dirty],
  );

  /** 新增节点：插到选中节点之后（同一层级），未选中则追加到主链末尾。 */
  /**
   * 结构变更后重建版式。
   *
   * <p>保留「还活着」的节点的手工位置（用户拖过就别动他），新节点与结束节点按新结构重算——
   * 否则 END 会停在中途、新节点会与已有节点重叠。
   */
  const rebuildLayout = (nextDoc: FlowDoc): Record<string, LayoutPoint> => {
    const auto = computeDefaultLayout(nextDoc);
    const next: Record<string, LayoutPoint> = {};
    for (const [code, point] of Object.entries(auto)) {
      // END 必须**永远**重算：它代表「最后一步之后」，结构一变它的旧坐标就落到了中途。
      next[code] = code === 'END' ? point : layout[code] ?? point;
    }
    return next;
  };

  const onAdd = (kind: 'approve' | 'cc' | 'condition') => {
    if (!doc) return;
    const code = genCode(kind);
    const step: Step =
      kind === 'approve' ? emptyApprove(code) : kind === 'cc' ? emptyCc(code) : emptyCondition(code);
    const nextDoc = insertAfter(doc, selected, step);
    applyDoc(nextDoc, { select: code, layout: rebuildLayout(nextDoc) });
    // 新节点可能在视口之外（长流程），主动滚过去，避免「点了没反应」的错觉。
    window.requestAnimationFrame(() => {
      const pos = layout[code] ?? computeDefaultLayout(insertAfter(doc, selected, step))[code];
      const el = containerRef.current;
      if (!pos || !el) return;
      el.scrollTo({
        left: Math.max(0, pos.x * scale + CANVAS_INSET_X - 120),
        top: Math.max(0, pos.y * scale + CANVAS_INSET_Y - 80),
        behavior: 'smooth',
      });
    });
  };

  const onDelete = (code: string) => {
    if (!doc) return;
    const nextDoc = removeStep(doc, code);
    applyDoc(nextDoc, { select: null, layout: rebuildLayout(nextDoc) });
  };

  const onMouseDown = (e: React.MouseEvent, code: string) => {
    if (e.button !== 0) return;
    e.preventDefault();
    e.stopPropagation();
    setSelected(code);
    const start = layout[code] ?? defaultLayout[code] ?? { x: 0, y: 0 };
    beforeDragRef.current = layout;
    setDrag({ code, originX: start.x, originY: start.y, px: e.clientX, py: e.clientY, moved: false });
  };
  useEffect(() => {
    if (!drag) return;
    const move = (e: MouseEvent) => {
      const dx = (e.clientX - drag.px) / scale;
      const dy = (e.clientY - drag.py) / scale;
      if (!drag.moved && Math.abs(dx) < 2 && Math.abs(dy) < 2) return;
      if (!drag.moved) setDrag((d) => (d ? { ...d, moved: true } : d));
      setLayout((prev) => ({ ...prev, [drag.code]: { x: drag.originX + dx, y: drag.originY + dy } }));
    };
    const up = () => {
      // 只有真的移动过才产生一步历史：点一下节点只是选中，不该占用撤销位。
      if (drag.moved) {
        pastRef.current.push({ doc, layout: beforeDragRef.current, selected });
        if (pastRef.current.length > 60) pastRef.current.shift();
        futureRef.current = [];
        syncHist();
      }
      setDrag(null);
    };
    window.addEventListener('mousemove', move);
    window.addEventListener('mouseup', up);
    return () => {
      window.removeEventListener('mousemove', move);
      window.removeEventListener('mouseup', up);
    };
  }, [drag, scale, doc, selected, syncHist]);

  /**
   * 关闭 / 刷新页面时拦一下。
   *
   * <p>这是最常见的一条丢数据路径，比 409 常见得多：设计一个流程要十几分钟，误关标签、误刷新全白干。浏览器只允许
   * 「提示」，文案由浏览器决定（不能自定义），但没有这一步就连提示都没有。
   */
  useEffect(() => {
    if (!dirty) return;
    const handler = (event: BeforeUnloadEvent) => {
      event.preventDefault();
      // 部分浏览器（Chrome < 119）只认 returnValue 的赋值，两处都写才覆盖全。
      event.returnValue = '';
    };
    window.addEventListener('beforeunload', handler);
    return () => window.removeEventListener('beforeunload', handler);
  }, [dirty]);

  /**
   * 快捷键。
   *
   * <p>设计器是高频编辑场景，纯鼠标操作（选中→点删除→再选中）在真实使用里非常耗人。这里对齐同类工具的最小集：
   * ⌘/Ctrl+S 保存、⌘/Ctrl+Z 撤销、⌘/Ctrl+Shift+Z（或 ⌘/Ctrl+Y）重做、Delete 删除选中节点、Esc 取消选中。
   *
   * <p>**输入框内一律放行**：用户在属性面板里按 ⌘Z 期望的是撤销这行文字，而不是把整个画布回滚一步。
   */
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const t = e.target as HTMLElement | null;
      const typing =
        !!t &&
        (t.tagName === 'INPUT' ||
          t.tagName === 'TEXTAREA' ||
          t.isContentEditable ||
          !!t.closest('.ant-select, .ant-input-number'));
      const mod = e.metaKey || e.ctrlKey;
      if (mod && e.key.toLowerCase() === 's') {
        e.preventDefault();
        if (doc && canWrite && !lossy) void onSaveDraft();
        return;
      }
      if (typing) return;
      if (mod && e.key.toLowerCase() === 'z') {
        e.preventDefault();
        if (e.shiftKey) redo();
        else undo();
        return;
      }
      if (mod && e.key.toLowerCase() === 'y') {
        e.preventDefault();
        redo();
        return;
      }
      if ((e.key === 'Delete' || e.key === 'Backspace') && selected && canWrite) {
        e.preventDefault();
        onDelete(selected);
        return;
      }
      if (e.key === 'Escape') setSelected(null);
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [doc, canWrite, lossy, onSaveDraft, undo, redo, selected, onDelete]);

  // ---- 模板：从模板创建并落库 ----
  const confirmCreateFromTemplate = useCallback(async () => {
    const { tpl, key, name } = tplModal;
    if (!tpl) return;
    if (!key.trim()) {
      message.error('请填写流程 key（字母 / 数字 / 下划线）');
      return;
    }
    if (!canWrite) {
      message.warning('当前账号无 workflow:definitions:write，无法创建');
      return;
    }
    const steps = tpl.build();
    const nextDoc: FlowDoc = { key: key.trim(), name: name.trim() || tpl.name, steps };
    const lay = computeDefaultLayout(nextDoc);
    const def = compile(nextDoc, { layout: lay });
    setLoading(true);
    try {
      // 先探一次：key 已存在时后端会回 409，把它翻译成「换个 key」而不是抛一个并发冲突给用户。
      const existing = await workflowDesignerApi.findDraft(key.trim());
      if (existing.code === 200 && existing.data) {
        message.error(`流程 key「${key.trim()}」已存在，请换一个 key，或直接从流程库打开它`);
        return;
      }
      const res = await workflowDesignerApi.saveDraft(key.trim(), JSON.stringify(def), null);
      if (res.code === 200 && res.data) {
        setDoc(nextDoc);
        setLayout(lay);
        setDefKey(key.trim());
        setDefName(nextDoc.name);
        setRevision(res.data.draft.revision);
        setDraftExists(true);
        setSavedSnapshot(JSON.stringify(def));
        setSelected(null);
        setViolations(res.data.violations ?? []);
        setLossy(false);
        setTplModal({ open: false, tpl: null, key: '', name: '' });
        setTplGallery(false);
        setSessionKeys((keys) => (keys.includes(key.trim()) ? keys : [...keys, key.trim()]));
        resetHistory();
        void refreshList();
        message.success(`已从模板「${tpl.name}」创建并保存草稿`);
      } else {
        reportFailure({ kind: 'unknown', message: `保存失败（code=${res.code}）` } as never, '从模板创建');
      }
    } catch (error) {
      const failure = toWorkflowFailure(error);
      if (failure.kind === 'conflict') {
        message.error(`流程 key「${key.trim()}」已存在，请换一个 key`);
      } else {
        reportFailure(error, '从模板创建');
      }
    } finally {
      setLoading(false);
    }
  }, [tplModal, canWrite, refreshList, reportFailure]);

  // ---- 导出 JSON（跨环境集成复用） ----
  const onExport = useCallback(() => {
    if (!compiled) return;
    setExportJson(JSON.stringify(compiled, null, 2));
  }, [compiled]);

  const copyExport = useCallback(async () => {
    if (!exportJson) return;
    try {
      await navigator.clipboard.writeText(exportJson);
      message.success('已复制定义 JSON，可粘贴到任意环境注册');
    } catch {
      message.warning('当前环境禁止剪贴板，请手动选中复制');
    }
  }, [exportJson]);

  const selectedStep = doc && selected ? findStep(doc, selected) : null;
  const selectedType = nodeTypeOf(selectedStep);
  const selectedMeta = NODE_META[selectedType];

  const filteredList = useMemo(() => {
    const t = filterText.trim().toLowerCase();
    const draftOnly = sessionKeys
      .filter((k) => !defList.some((d) => d.defKey === k))
      .map<WorkflowDefinitionSummary>((k) => ({
        defKey: k,
        defName: k === defKey && defName ? defName : k,
        version: 0,
        enabled: false,
      }));
    const all = [...defList, ...draftOnly];
    if (!t) return all;
    return all.filter(
      (d) => (d.defKey ?? '').toLowerCase().includes(t) || (d.defName ?? '').toLowerCase().includes(t),
    );
  }, [defList, sessionKeys, defKey, defName, filterText]);

  // ---- 画布几何 ----
  /**
   * 画布数据源：优先用「文档树编译产物」（可编辑），编译失败时退化为服务端原始图（只读预览）。
   *
   * <p>两者节点/边结构一致（code/type/label、from/to/label），渲染层因此只认一套形状。
   */
  const canvas = useMemo(() => {
    if (compiled) {
      return {
        nodes: compiled.nodes as DefinitionNode[],
        edges: compiled.edges as DefinitionEdge[],
        pos: null as Record<string, LayoutPoint> | null,
        editable: true,
      };
    }
    if (rawDefinition && rawDefinition.nodes.length > 0) {
      return {
        nodes: rawDefinition.nodes,
        edges: rawDefinition.edges,
        pos: layeredLayout(rawDefinition.nodes, rawDefinition.edges),
        editable: false,
      };
    }
    return null;
  }, [compiled, rawDefinition]);

  const posOf = useCallback(
    (code: string): LayoutPoint | undefined =>
      canvas?.pos ? canvas.pos[code] : effectiveLayout[code],
    [canvas, effectiveLayout],
  );

  /** 画布内容外框：决定滚动区域大小与「适应画布」的基准。 */
  const bounds = useMemo(() => {
    let maxX = 0;
    let maxY = 0;
    for (const n of canvas?.nodes ?? []) {
      const p = posOf(n.code);
      if (!p) continue;
      maxX = Math.max(maxX, p.x + NODE_W);
      maxY = Math.max(maxY, p.y + NODE_H);
    }
    return { w: Math.max(maxX + CANVAS_PAD, 900), h: Math.max(maxY + CANVAS_PAD, 560) };
  }, [canvas, posOf]);

  const zoomBy = useCallback((delta: number) => {
    setScale((s) => Math.max(ZOOM_MIN, Math.min(ZOOM_MAX, Number((s + delta).toFixed(2)))));
  }, []);

  const fitToView = useCallback(() => {
    const el = containerRef.current;
    if (!el) return;
    const { width, height } = el.getBoundingClientRect();
    const s = Math.min((width - 100) / bounds.w, (height - 100) / bounds.h, 1);
    setScale(Math.max(ZOOM_MIN, Math.min(ZOOM_MAX, Number(s.toFixed(2)))));
    el.scrollTo({ left: 0, top: 0 });
  }, [bounds]);

  /** Ctrl/⌘ + 滚轮缩放（与 Figma / Camunda 一致）。非 passive 才能 preventDefault 掉浏览器整页缩放。 */
  useEffect(() => {
    const el = containerRef.current;
    if (!el) return;
    const onWheel = (e: WheelEvent) => {
      if (!(e.ctrlKey || e.metaKey)) return;
      e.preventDefault();
      zoomBy(e.deltaY > 0 ? -ZOOM_STEP : ZOOM_STEP);
    };
    el.addEventListener('wheel', onWheel, { passive: false });
    return () => el.removeEventListener('wheel', onWheel);
  }, [zoomBy]);

  /**
   * 换文档时回到 100% 并滚回起点。
   *
   * <p>为什么**不**自动「适应画布」：分支图动辄 5-8 层，塞进 700×500 的面板需要缩到 40%，
   * 节点标题只有 6-7px、完全不可读——「看全」远不如「看清」。适应画布保留为显式按钮。
   */
  const canvasKey = `${defKey}|${canvas?.editable ? 'edit' : 'preview'}|${canvas?.nodes.length ?? 0}|${defName}`;
  useEffect(() => {
    if (!canvas) return;
    setScale(1);
    containerRef.current?.scrollTo({ left: 0, top: 0 });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [canvasKey]);

  /** 选中并滚到视野内：右栏点违规项、列表里选节点时用，避免「高亮在屏幕外」。 */
  const selectAndReveal = useCallback(
    (code: string) => {
      setSelected(code);
      const pos = effectiveLayout[code];
      const el = containerRef.current;
      if (!pos || !el) return;
      el.scrollTo({
        left: Math.max(0, pos.x * scale + CANVAS_INSET_X - 120),
        top: Math.max(0, pos.y * scale + CANVAS_INSET_Y - 80),
        behavior: 'smooth',
      });
    },
    [effectiveLayout, scale],
  );

  /** 改名要同时改文档（compile 用 doc.name 作为定义名），只改界面状态会发布出旧名字。 */
  const renameFlow = useCallback(
    (name: string) => {
      setDefName(name);
      if (doc) applyDoc({ ...doc, name }, { coalesce: 'flow:name' });
    },
    [doc, applyDoc],
  );

  const confirmDiscardDraft = useCallback(() => {
    Modal.confirm({
      title: '确认丢弃草稿？',
      content: '画布内容会保留，但服务端这份草稿会被删除；未发布的修改将不存在于服务端。',
      okText: '丢弃草稿',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: () => void onDiscardDraft(),
    });
  }, [onDiscardDraft]);

  const stats = useMemo(
    () => (doc ? countSteps(doc.steps) : { approve: 0, cc: 0, condition: 0 }),
    [doc],
  );
  const publishable = isPublishable(violations);
  const nodeCount = stats.approve + stats.cc + stats.condition;
  const startCreate = (tpl: WorkflowTemplate) => {
    if (!canWrite) {
      message.warning('当前账号无 workflow:definitions:write，无法创建');
      return;
    }
    setTplModal({ open: true, tpl, key: `${tpl.id}_approval`, name: tpl.name });
  };

  return (
    <div ref={rootRef} style={{ height: fillHeight ?? '100%', display: 'flex', minHeight: 0 }}>
      <Layout style={{ height: '100%', width: '100%', overflow: 'hidden', background: colors.gray[150] }}>
        {/* ============ 左：流程库 ============ */}
        <Sider
          width={compact ? 268 : 296}
          collapsedWidth={0}
          collapsed={siderCollapsed}
          trigger={null}
          theme="light"
          style={{
            borderRight: `1px solid ${colors.gray[200]}`,
            height: '100%',
            background: '#fff',
          }}
        >
          <div style={{ display: 'flex', flexDirection: 'column', height: '100%', minHeight: 0 }}>
            <div style={{ padding: '12px 12px 10px', borderBottom: `1px solid ${colors.gray[200]}` }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <div
                  style={{
                    width: 28,
                    height: 28,
                    borderRadius: 8,
                    background: colors.primary[50],
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    flexShrink: 0,
                  }}
                >
                  <AppstoreOutlined style={{ color: colors.primary[500], fontSize: 15 }} />
                </div>
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{ fontSize: 13, fontWeight: 600, lineHeight: '18px' }}>流程库</div>
                  <div style={{ fontSize: 11, color: colors.gray[500], lineHeight: '15px' }}>
                    {defList.length} 个流程 · {canWrite ? '可编辑' : '只读'}
                  </div>
                </div>
                <Tooltip title="新建空白流程">
                  <Button
                    type="text"
                    size="small"
                    icon={<PlusOutlined />}
                    aria-label="新建空白流程"
                    disabled={!canWrite}
                    onClick={() => setCreateModal({ open: true, key: '', name: '' })}
                  />
                </Tooltip>
                <Tooltip title="收起流程库">
                  <Button
                    type="text"
                    size="small"
                    icon={<MenuFoldOutlined />}
                    aria-label="收起流程库"
                    onClick={() => setSiderCollapsed(true)}
                  />
                </Tooltip>
              </div>
              <Input
                allowClear
                size="small"
                prefix={<SearchOutlined style={{ color: colors.gray[400] }} />}
                placeholder="搜索名称或 key"
                value={filterText}
                onChange={(e) => setFilterText(e.target.value)}
                style={{ marginTop: 10 }}
              />
            </div>

            <div style={{ flex: 1, overflow: 'auto', minHeight: 0, padding: 8 }}>
              {filteredList.map((d) => (
                <FlowListItem
                  key={d.defKey}
                  item={d}
                  active={d.defKey === defKey}
                  onOpen={() => void openKey(d.defKey, d.defName)}
                />
              ))}
              {filteredList.length === 0 && (
                <div style={{ padding: '48px 8px' }}>
                  <Empty
                    image={Empty.PRESENTED_IMAGE_SIMPLE}
                    description={filterText ? '没有匹配的流程' : '还没有流程定义'}
                  />
                </div>
              )}
            </div>

            <div style={{ padding: 10, borderTop: `1px solid ${colors.gray[200]}` }}>
              <Button
                block
                icon={<ThunderboltOutlined />}
                onClick={() => setTplGallery(true)}
                disabled={!canWrite}
              >
                从模板创建
              </Button>
            </div>
          </div>
        </Sider>

        <Content style={{ display: 'flex', flexDirection: 'column', height: '100%', minWidth: 0, background: '#fff' }}>
          {/* ============ 工具栏 ============ */}
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 8,
              padding: '8px 12px',
              borderBottom: `1px solid ${colors.gray[200]}`,
              background: '#fff',
              flexShrink: 0,
              minHeight: 54,
            }}
          >
            {siderCollapsed && (
              <Tooltip title="展开流程库">
                <Button
                  type="text"
                  icon={<MenuUnfoldOutlined />}
                  aria-label="展开流程库"
                  onClick={() => setSiderCollapsed(false)}
                />
              </Tooltip>
            )}
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, minWidth: 0, flex: 1 }}>
              <FileTextOutlined style={{ color: colors.primary[500], flexShrink: 0 }} />
              <Text
                strong
                style={{
                  fontSize: 14,
                  maxWidth: compact ? 140 : 220,
                  whiteSpace: 'nowrap',
                  overflow: 'hidden',
                  textOverflow: 'ellipsis',
                }}
                title={defName || undefined}
              >
                {defName || '未选择流程'}
              </Text>
              {defKey && !compact && (
                <Text code style={{ fontSize: 11, flexShrink: 0 }}>
                  {defKey}
                </Text>
              )}
              <Space size={4} style={{ flexShrink: 0 }}>
                {draftExists && (
                  <Tooltip title={`草稿修订号 ${revision}；发布会把它固化成新版本`}>
                    <Tag color="orange" style={{ marginInlineEnd: 0 }}>
                      草稿 rev {revision}
                    </Tag>
                  </Tooltip>
                )}
                {dirty && (
                  <Tooltip title="画布内容与上次保存 / 载入不一致，关闭页面前请先保存">
                    <Tag color="red" style={{ marginInlineEnd: 0 }}>
                      未保存
                    </Tag>
                  </Tooltip>
                )}
                {lossy && (
                  <Tooltip title="旧数据无法完整反解析为节点树，补全后才能保存">
                    <Tag color="red" style={{ marginInlineEnd: 0 }}>
                      有损还原
                    </Tag>
                  </Tooltip>
                )}
                {!canWrite && (
                  <Tooltip title="缺少 workflow:definitions:write，仅可查看 / 校验 / 试运行">
                    <Tag style={{ marginInlineEnd: 0 }}>只读</Tag>
                  </Tooltip>
                )}
              </Space>
            </div>

            <Space size={4} style={{ flexShrink: 0 }}>
              <Tooltip title="撤销（⌘/Ctrl + Z）">
                <Button
                  type="text"
                  icon={<UndoOutlined />}
                  aria-label="撤销"
                  disabled={!histState.canUndo}
                  onClick={undo}
                />
              </Tooltip>
              <Tooltip title="重做（⌘/Ctrl + Shift + Z）">
                <Button
                  type="text"
                  icon={<RedoOutlined />}
                  aria-label="重做"
                  disabled={!histState.canRedo}
                  onClick={redo}
                />
              </Tooltip>
              <Divider type="vertical" style={{ margin: '0 2px' }} />
              {compact ? (
                <Dropdown
                  trigger={['click']}
                  placement="bottomRight"
                  menu={{
                    items: [
                      { key: 'validate', icon: <EyeOutlined />, label: '校验定义', disabled: !doc },
                      { key: 'simulate', icon: <ExperimentOutlined />, label: '试运行', disabled: !doc },
                      { key: 'versions', icon: <AuditOutlined />, label: '版本历史' },
                      { key: 'export', icon: <ExportOutlined />, label: '导出 JSON', disabled: !compiled },
                      ...(draftExists
                        ? [
                            { type: 'divider' as const },
                            { key: 'discard', icon: <DeleteOutlined />, label: '丢弃服务端草稿', danger: true },
                          ]
                        : []),
                    ],
                    onClick: ({ key }) => {
                      if (key === 'validate') void onValidate();
                      else if (key === 'simulate') void onSimulate();
                      else if (key === 'versions') void onOpenVersions();
                      else if (key === 'export') onExport();
                      else if (key === 'discard') confirmDiscardDraft();
                    },
                  }}
                >
                  <Tooltip title="校验 / 试运行 / 版本 / 导出">
                    <Button icon={<MoreOutlined />} aria-label="更多操作">
                      更多
                    </Button>
                  </Tooltip>
                </Dropdown>
              ) : (
                <>
                  <Button icon={<EyeOutlined />} onClick={() => void onValidate()} disabled={!doc}>
                    校验
                  </Button>
                  <Button icon={<ExperimentOutlined />} onClick={() => void onSimulate()} disabled={!doc}>
                    试运行
                  </Button>
                  <Button icon={<AuditOutlined />} onClick={() => void onOpenVersions()}>
                    版本
                  </Button>
                  <Tooltip title="导出定义 JSON（跨环境 / 跨租户复用）">
                    <Button icon={<ExportOutlined />} aria-label="导出" onClick={onExport} disabled={!compiled} />
                  </Tooltip>
                  {draftExists && (
                    <Tooltip title="丢弃服务端草稿（画布内容保留）">
                      <Button
                        type="text"
                        danger
                        icon={<DeleteOutlined />}
                        aria-label="丢弃草稿"
                        onClick={confirmDiscardDraft}
                      />
                    </Tooltip>
                  )}
                </>
              )}
              <Divider type="vertical" style={{ margin: '0 2px' }} />
              <Button icon={<SaveOutlined />} onClick={() => void onSaveDraft()} disabled={!doc || !canWrite || lossy}>
                保存草稿
              </Button>
              <Button
                type="primary"
                icon={<SendOutlined />}
                onClick={() => void onPublish()}
                disabled={!doc || !canWrite || lossy}
              >
                发布
              </Button>
            </Space>
          </div>

          <div style={{ flex: 1, display: 'flex', minHeight: 0, position: 'relative' }}>
            {/* ============ 画布 ============ */}
            <div style={{ position: 'relative', flex: 1, minWidth: 0, display: 'flex' }}>
              <div
                ref={containerRef}
                onMouseDown={() => setSelected(null)}
                style={{
                  flex: 1,
                  position: 'relative',
                  overflow: 'auto',
                  background: colors.gray[150],
                  backgroundImage: `radial-gradient(${colors.gray[300]} 1px, transparent 1px)`,
                  backgroundSize: `${20 * scale}px ${20 * scale}px`,
                }}
              >
                {!doc && (
                  <div
                    style={{
                      position: 'absolute',
                      inset: 0,
                      display: 'flex',
                      flexDirection: 'column',
                      alignItems: 'center',
                      justifyContent: 'center',
                      gap: 12,
                    }}
                  >
                    <div
                      style={{
                        width: 56,
                        height: 56,
                        borderRadius: 16,
                        background: '#fff',
                        border: `1px solid ${colors.gray[200]}`,
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                      }}
                    >
                      <BranchesOutlined style={{ fontSize: 24, color: colors.primary[400] }} />
                    </div>
                    <div style={{ fontSize: 15, fontWeight: 600 }}>开始设计一条审批流</div>
                    <Text type="secondary" style={{ fontSize: 12 }}>
                      从场景模板一键生成，或新建空白流程后逐个添加节点
                    </Text>
                    <Space size={8} style={{ marginTop: 4 }}>
                      <Button
                        type="primary"
                        icon={<ThunderboltOutlined />}
                        disabled={!canWrite}
                        onClick={() => setTplGallery(true)}
                      >
                        从模板创建
                      </Button>
                      <Button
                        icon={<PlusOutlined />}
                        disabled={!canWrite}
                        onClick={() => setCreateModal({ open: true, key: '', name: '' })}
                      >
                        新建空白流程
                      </Button>
                    </Space>
                  </div>
                )}

                {canvas && (
                  <div
                    style={{
                      position: 'relative',
                      width: bounds.w * scale + CANVAS_INSET_X,
                      height: bounds.h * scale + CANVAS_INSET_Y,
                    }}
                  >
                    <div
                      style={{
                        position: 'absolute',
                        left: CANVAS_INSET_X,
                        top: CANVAS_INSET_Y,
                        width: bounds.w,
                        height: bounds.h,
                        transform: `scale(${scale})`,
                        transformOrigin: '0 0',
                      }}
                    >
                      <svg
                        width={bounds.w}
                        height={bounds.h}
                        style={{ position: 'absolute', inset: 0, pointerEvents: 'none', overflow: 'visible' }}
                      >
                        <defs>
                          <marker
                            id="wf-arrow"
                            viewBox="0 0 10 10"
                            refX="9"
                            refY="5"
                            markerWidth="6"
                            markerHeight="6"
                            orient="auto-start-reverse"
                          >
                            <path d="M0,0 L10,5 L0,10 z" fill={colors.gray[400]} />
                          </marker>
                          <marker
                            id="wf-arrow-active"
                            viewBox="0 0 10 10"
                            refX="9"
                            refY="5"
                            markerWidth="6"
                            markerHeight="6"
                            orient="auto-start-reverse"
                          >
                            <path d="M0,0 L10,5 L0,10 z" fill={colors.primary[500]} />
                          </marker>
                        </defs>
                        {(canvas?.edges ?? []).map((e, i) => {
                          const a = posOf(e.from);
                          const b = posOf(e.to);
                          if (!a || !b) return null;
                          const { d, mid } = edgePath(a, b);
                          const active = selected === e.from || selected === e.to;
                          const label = e.label ?? '';
                          const labelW = Math.max(30, label.length * 11.5 + 12);
                          return (
                            <g key={`${e.from}-${e.to}-${i}`}>
                              <path
                                d={d}
                                fill="none"
                                stroke={active ? colors.primary[400] : colors.gray[300]}
                                strokeWidth={1.5}
                                markerEnd={`url(#${active ? 'wf-arrow-active' : 'wf-arrow'})`}
                              />
                              {label && (
                                <g>
                                  <rect
                                    x={mid.x - labelW / 2}
                                    y={mid.y - 9}
                                    width={labelW}
                                    height={18}
                                    rx={9}
                                    fill="#fff"
                                    stroke={colors.gray[200]}
                                  />
                                  <text
                                    x={mid.x}
                                    y={mid.y + 4}
                                    textAnchor="middle"
                                    fontSize={11}
                                    fill={colors.gray[600]}
                                  >
                                    {label}
                                  </text>
                                </g>
                              )}
                            </g>
                          );
                        })}
                      </svg>

                      {(canvas?.nodes ?? []).map((n) => {
                        const pos = posOf(n.code) ?? { x: 0, y: 0 };
                        const vs = violationByNode.get(n.code) ?? [];
                        const meta = NODE_META[n.type as NodeType] ?? NODE_META.APPROVE;
                        const { Icon } = meta;
                        const isStartEnd = n.type === 'START' || n.type === 'END';
                        const isSel = selected === n.code;
                        return (
                          <div
                            key={n.code}
                            data-wf-node={n.code}
                            onMouseDown={(e) => {
                              if (canvas?.editable) onMouseDown(e, n.code);
                            }}
                            onClick={(e) => {
                              if (!canvas?.editable) return;
                              e.stopPropagation();
                              setSelected(n.code);
                            }}
                            title={n.label ?? n.code}
                            style={{
                              position: 'absolute',
                              left: pos.x,
                              top: pos.y,
                              width: NODE_W,
                              height: NODE_H,
                              display: 'flex',
                              alignItems: 'center',
                              gap: 10,
                              padding: '0 12px',
                              background: '#fff',
                              border: `1.5px solid ${isSel ? colors.primary[500] : colors.gray[300]}`,
                              borderRadius: 10,
                              boxShadow: isSel
                                ? `0 0 0 3px ${colors.primary[100]}, 0 8px 20px rgba(0,0,0,.08)`
                                : '0 1px 3px rgba(0,0,0,.06)',
                              cursor: canvas?.editable
                                ? drag?.code === n.code
                                  ? 'grabbing'
                                  : 'grab'
                                : 'default',
                              userSelect: 'none',
                              overflow: 'hidden',
                            }}
                          >
                            <div
                              style={{
                                width: 30,
                                height: 30,
                                borderRadius: 8,
                                background: meta.bg,
                                display: 'flex',
                                alignItems: 'center',
                                justifyContent: 'center',
                                flexShrink: 0,
                              }}
                            >
                              <Icon style={{ color: meta.color, fontSize: 15 }} />
                            </div>
                            <div style={{ minWidth: 0, flex: 1 }}>
                              <div
                                style={{
                                  fontSize: 10,
                                  fontWeight: 600,
                                  color: meta.color,
                                  letterSpacing: 0.5,
                                  lineHeight: '13px',
                                }}
                              >
                                {meta.label}
                              </div>
                              <div
                                style={{
                                  fontSize: 13,
                                  fontWeight: 600,
                                  lineHeight: '18px',
                                  whiteSpace: 'nowrap',
                                  overflow: 'hidden',
                                  textOverflow: 'ellipsis',
                                }}
                              >
                                {n.label}
                              </div>
                              {!isStartEnd && (
                                <div
                                  style={{
                                    fontSize: 11,
                                    color: colors.gray[500],
                                    lineHeight: '15px',
                                    whiteSpace: 'nowrap',
                                    overflow: 'hidden',
                                    textOverflow: 'ellipsis',
                                  }}
                                >
                                  {nodeSummary(doc ? findStep(doc, n.code) : null)}
                                </div>
                              )}
                            </div>
                            {vs.length > 0 && (
                              <Badge
                                count={vs.length}
                                size="small"
                                style={{ position: 'absolute', top: 6, right: 6 }}
                              />
                            )}
                          </div>
                        );
                      })}
                    </div>
                  </div>
                )}

                {canvas?.editable && nodeCount === 0 && (
                  <div
                    style={{
                      position: 'absolute',
                      left: 0,
                      right: 0,
                      top: 320,
                      display: 'flex',
                      justifyContent: 'center',
                      pointerEvents: 'none',
                    }}
                  >
                    <div
                      style={{
                        background: '#fff',
                        border: `1px dashed ${colors.primary[200]}`,
                        borderRadius: 10,
                        padding: '10px 16px',
                        fontSize: 12,
                        color: colors.gray[600],
                        display: 'flex',
                        alignItems: 'center',
                        gap: 8,
                      }}
                    >
                      <AimOutlined style={{ color: colors.primary[500] }} />
                      流程还是空的：从左上角节点库添加第一个节点
                    </div>
                  </div>
                )}
              </div>

              {loading && (
                <div
                  style={{
                    position: 'absolute',
                    inset: 0,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    background: 'rgba(255,255,255,.6)',
                    zIndex: 6,
                  }}
                >
                  <Spin />
                </div>
              )}

              {canvas && !canvas.editable && (
                <div
                  style={{
                    position: 'absolute',
                    left: '50%',
                    transform: 'translateX(-50%)',
                    bottom: 12,
                    display: 'flex',
                    alignItems: 'center',
                    gap: 8,
                    background: colors.semantic.warningBg,
                    border: `1px solid ${colors.semantic.warningBorder}`,
                    borderRadius: 8,
                    padding: '6px 12px',
                    fontSize: 12,
                    color: colors.gray[700],
                    maxWidth: 520,
                  }}
                >
                  <EyeOutlined style={{ color: colors.semantic.warning }} />
                  只读预览：该定义含共享节点（多个分支汇入同一节点），无法还原为可编辑树
                </div>
              )}

              {/* 浮动节点库：把「加节点」从文件工具栏里拆出来，放回它语义上属于的画布 */}
              {canvas?.editable && (
                <div
                  style={{
                    position: 'absolute',
                    left: 12,
                    top: 12,
                    display: 'flex',
                    flexDirection: 'column',
                    gap: 4,
                    background: '#fff',
                    border: `1px solid ${colors.gray[200]}`,
                    borderRadius: 10,
                    padding: 4,
                    boxShadow: '0 4px 16px rgba(0,0,0,.08)',
                  }}
                >
                  <Tooltip title={selected ? '在选中节点之后插入审批节点' : '在主链末尾添加审批节点'} placement="right">
                    <Button
                      type="text"
                      size="small"
                      icon={<PlusOutlined />}
                      aria-label="添加审批节点"
                      disabled={!canWrite}
                      onClick={() => onAdd('approve')}
                    >
                      审批
                    </Button>
                  </Tooltip>
                  <Tooltip title="添加抄送节点（不阻塞流程）" placement="right">
                    <Button
                      type="text"
                      size="small"
                      icon={<SendOutlined />}
                      aria-label="添加抄送节点"
                      disabled={!canWrite}
                      onClick={() => onAdd('cc')}
                    >
                      抄送
                    </Button>
                  </Tooltip>
                  <Tooltip title="添加条件分支（按字段值分流）" placement="right">
                    <Button
                      type="text"
                      size="small"
                      icon={<BranchesOutlined />}
                      aria-label="添加条件分支"
                      disabled={!canWrite}
                      onClick={() => onAdd('condition')}
                    >
                      条件
                    </Button>
                  </Tooltip>
                </div>
              )}

              {/* 缩放条 */}
              {canvas && (
                <div
                  style={{
                    position: 'absolute',
                    right: 12,
                    bottom: 12,
                    display: 'flex',
                    alignItems: 'center',
                    gap: 2,
                    background: '#fff',
                    border: `1px solid ${colors.gray[200]}`,
                    borderRadius: 8,
                    padding: 2,
                    boxShadow: '0 2px 10px rgba(0,0,0,.08)',
                  }}
                >
                  <Tooltip title="缩小">
                    <Button
                      type="text"
                      size="small"
                      icon={<ZoomOutOutlined />}
                      aria-label="缩小"
                      onClick={() => zoomBy(-ZOOM_STEP)}
                    />
                  </Tooltip>
                  <button
                    type="button"
                    onClick={() => setScale(1)}
                    title="恢复 100%"
                    style={{
                      border: 0,
                      background: 'transparent',
                      fontSize: 12,
                      width: 48,
                      cursor: 'pointer',
                      color: colors.gray[700],
                      fontVariantNumeric: 'tabular-nums',
                    }}
                  >
                    {Math.round(scale * 100)}%
                  </button>
                  <Tooltip title="放大">
                    <Button
                      type="text"
                      size="small"
                      icon={<ZoomInOutlined />}
                      aria-label="放大"
                      onClick={() => zoomBy(ZOOM_STEP)}
                    />
                  </Tooltip>
                  <Divider type="vertical" style={{ margin: '0 2px' }} />
                  <Tooltip title="适应画布">
                    <Button
                      type="text"
                      size="small"
                      icon={<AimOutlined />}
                      aria-label="适应画布"
                      onClick={fitToView}
                    />
                  </Tooltip>
                </div>
              )}

              {/* 画布左下角提示：节点数与快捷键，降低学习成本（窄屏只留计数，避免压到右下角缩放条） */}
              {canvas && (
                <div
                  style={{
                    position: 'absolute',
                    left: 12,
                    bottom: 12,
                    maxWidth: 'calc(100% - 168px)',
                    whiteSpace: 'nowrap',
                    overflow: 'hidden',
                    textOverflow: 'ellipsis',
                    fontSize: 11,
                    color: colors.gray[500],
                    background: 'rgba(255,255,255,.9)',
                    border: `1px solid ${colors.gray[200]}`,
                    borderRadius: 6,
                    padding: '4px 8px',
                    pointerEvents: 'none',
                  }}
                >
                  {canvas.editable
                    ? compact
                      ? `${canvas.nodes.length} 个节点 / ${nodeCount} 个步骤`
                      : `${canvas.nodes.length} 个节点 / ${nodeCount} 个步骤 · 拖拽移动 · Delete 删除 · ⌘/Ctrl 滚轮缩放`
                    : `${canvas.nodes.length} 个节点 · 只读预览`}
                </div>
              )}
            </div>

            {/* ============ 右：属性 / 流程设置 ============ */}
            {panelCollapsed ? (
              <Tooltip title="展开属性面板" placement="left">
                <div
                  role="button"
                  tabIndex={0}
                  onClick={() => setPanelCollapsed(false)}
                  onKeyDown={(e) => e.key === 'Enter' && setPanelCollapsed(false)}
                  style={{
                    width: 34,
                    borderLeft: `1px solid ${colors.gray[200]}`,
                    background: '#fff',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    cursor: 'pointer',
                  }}
                >
                  <MenuUnfoldOutlined style={{ color: colors.gray[500], transform: 'rotate(180deg)' }} />
                </div>
              </Tooltip>
            ) : (
              <div
                style={{
                  width: compact ? 308 : 344,
                  flexShrink: 0,
                  borderLeft: `1px solid ${colors.gray[200]}`,
                  background: '#fff',
                  display: 'flex',
                  flexDirection: 'column',
                  minHeight: 0,
                }}
              >
                <div
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 6,
                    padding: '10px 12px',
                    borderBottom: `1px solid ${colors.gray[200]}`,
                    flexShrink: 0,
                  }}
                >
                  {selectedStep ? (
                    <>
                      <selectedMeta.Icon style={{ color: selectedMeta.color, fontSize: 15 }} />
                      <Text strong style={{ fontSize: 13 }}>
                        {selectedMeta.label}节点
                      </Text>
                      <Tag color={selectedMeta.color} style={{ marginInlineEnd: 0 }}>
                        {MODE_LABEL[(selectedStep as ApproveStep).mode] ?? selectedStep.kind}
                      </Tag>
                    </>
                  ) : (
                    <>
                      <SettingOutlined style={{ color: colors.gray[600], fontSize: 15 }} />
                      <Text strong style={{ fontSize: 13 }}>
                        流程设置
                      </Text>
                    </>
                  )}
                  <div style={{ flex: 1 }} />
                  {selectedStep && (
                    <Tooltip title="返回流程设置">
                      <Button
                        type="text"
                        size="small"
                        icon={<SettingOutlined />}
                        aria-label="返回流程设置"
                        onClick={() => setSelected(null)}
                      />
                    </Tooltip>
                  )}
                  <Tooltip title="收起属性面板">
                    <Button
                      type="text"
                      size="small"
                      icon={<MenuFoldOutlined style={{ transform: 'rotate(180deg)' }} />}
                      aria-label="收起属性面板"
                      onClick={() => setPanelCollapsed(true)}
                    />
                  </Tooltip>
                </div>

                <div style={{ flex: 1, overflow: 'auto', minHeight: 0, padding: 12 }}>
                  {selectedStep ? (
                    <StepPropertyPanel
                      step={selectedStep}
                      onChange={(next) =>
                        applyDoc(updateStep(doc as FlowDoc, selectedStep.code, () => next), {
                          coalesce: `step:${selectedStep.code}`,
                        })
                      }
                      onDelete={() => onDelete(selectedStep.code)}
                      violations={violationByNode.get(selectedStep.code) ?? []}
                      readOnly={!canWrite}
                    />
                  ) : (
                    <Space direction="vertical" style={{ width: '100%' }} size={16}>
                      {!doc && (
                        <div style={{ textAlign: 'center', padding: '32px 8px', color: colors.gray[500] }}>
                          <InboxOutlined style={{ fontSize: 32, color: colors.gray[300] }} />
                          <div style={{ marginTop: 8, fontSize: 13 }}>未打开任何流程</div>
                          <div style={{ fontSize: 12, marginTop: 4 }}>从左侧选择流程，或新建一个</div>
                        </div>
                      )}

                      {doc && (
                        <>
                          {lossy && (
                            <Alert
                              type="warning"
                              showIcon
                              message="该定义由旧数据重建"
                              description={
                                compiled
                                  ? loadWarnings[0] ??
                                    '原定义未携带可视化信息，节点结构按图推断，可能有偏差；保存已禁用，核对无误后可重建为可编辑草稿。'
                                  : '原定义未携带可视化信息，且含共享节点（多个分支汇入同一节点），无法还原为可编辑的流程树。当前按真实结构只读展示；如需可视化编辑，需先把该流程调整为树形分支结构。'
                              }
                              action={
                                canWrite && compiled ? (
                                  <Button size="small" type="primary" onClick={() => void onAdoptLossy()}>
                                    重建草稿
                                  </Button>
                                ) : null
                              }
                            />
                          )}
                          <div>
                            <Text type="secondary" style={{ fontSize: 12 }}>
                              流程名称
                            </Text>
                            <Input
                              value={defName}
                              maxLength={64}
                              disabled={!canWrite}
                              onChange={(e) => renameFlow(e.target.value)}
                              placeholder="如 费用报销审批"
                              style={{ marginTop: 4 }}
                            />
                          </div>
                          <div>
                            <Text type="secondary" style={{ fontSize: 12 }}>
                              流程标识（创建后不建议修改）
                            </Text>
                            <Input
                              value={defKey}
                              readOnly
                              suffix={
                                <Tooltip title="复制 key">
                                  <CopyOutlined
                                    style={{ color: colors.gray[400], cursor: 'pointer' }}
                                    onClick={() => {
                                      void navigator.clipboard
                                        ?.writeText(defKey)
                                        .then(() => message.success('已复制流程 key'))
                                        .catch(() => message.warning('当前环境禁止剪贴板'));
                                    }}
                                  />
                                </Tooltip>
                              }
                              style={{ marginTop: 4, fontFamily: 'monospace', fontSize: 12 }}
                            />
                          </div>

                          <div>
                            <Text type="secondary" style={{ fontSize: 12 }}>
                              结构统计
                            </Text>
                            <div
                              style={{
                                display: 'grid',
                                gridTemplateColumns: 'repeat(3, 1fr)',
                                gap: 8,
                                marginTop: 6,
                              }}
                            >
                              {[
                                { label: '审批', value: stats.approve, color: colors.primary[500] },
                                { label: '抄送', value: stats.cc, color: '#13c2c2' },
                                { label: '条件', value: stats.condition, color: colors.primary[700] },
                              ].map((s) => (
                                <div
                                  key={s.label}
                                  style={{
                                    border: `1px solid ${colors.gray[200]}`,
                                    borderRadius: 8,
                                    padding: '8px 10px',
                                    textAlign: 'center',
                                  }}
                                >
                                  <div style={{ fontSize: 18, fontWeight: 600, color: s.color, lineHeight: '24px' }}>
                                    {s.value}
                                  </div>
                                  <div style={{ fontSize: 11, color: colors.gray[500] }}>{s.label}</div>
                                </div>
                              ))}
                            </div>
                          </div>

                          <div>
                            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                              <Text type="secondary" style={{ fontSize: 12 }}>
                                校验结果
                              </Text>
                              <Button size="small" type="link" onClick={() => void onValidate()} disabled={!compiled}>
                                立即校验
                              </Button>
                            </div>
                            {publishable ? (
                              <div
                                style={{
                                  marginTop: 4,
                                  fontSize: 12,
                                  color: colors.gray[500],
                                  border: `1px dashed ${colors.gray[200]}`,
                                  borderRadius: 8,
                                  padding: '10px 12px',
                                }}
                              >
                                {violations.length === 0 ? '尚未校验。发布前会自动校验，存在违规会拒绝发布。' : '没有阻断性问题，可发布。'}
                              </div>
                            ) : (
                              <div style={{ marginTop: 4, fontSize: 12, color: colors.semantic.error }}>
                                存在 {violations.length} 处问题，发布会被拒绝（fail-closed）
                              </div>
                            )}
                          </div>

                          <Divider style={{ margin: 0 }} />

                          <Space wrap size={8}>
                            <Button size="small" icon={<ExperimentOutlined />} onClick={() => void onSimulate()}>
                              试运行
                            </Button>
                            <Button size="small" icon={<AuditOutlined />} onClick={() => void onOpenVersions()}>
                              版本历史
                            </Button>
                            <Button size="small" icon={<ExportOutlined />} onClick={onExport} disabled={!compiled}>
                              导出 JSON
                            </Button>
                          </Space>
                        </>
                      )}

                      {violations.length > 0 && (
                        <>
                          <Divider style={{ margin: 0 }}>校验问题</Divider>
                          <Space direction="vertical" style={{ width: '100%' }} size={8}>
                            {violations.map((v, i) => (
                              <Alert
                                key={i}
                                type="error"
                                showIcon
                                message={`${v.code}${v.locator ? ` @${v.locator}` : ''}`}
                                description={hintOf(v).suggestion ?? v.message}
                                onClick={() => v.locator && selectAndReveal(v.locator)}
                                style={{ cursor: v.locator ? 'pointer' : 'default', fontSize: 12 }}
                              />
                            ))}
                          </Space>
                        </>
                      )}

                      {simReport && (
                        <>
                          <Divider style={{ margin: 0 }}>试运行结果</Divider>
                          <SimReportView report={simReport} />
                        </>
                      )}
                    </Space>
                  )}

                  {selectedStep && violations.length > 0 && violationByNode.get(selectedStep.code)?.length === 0 && (
                    <Text type="secondary" style={{ fontSize: 11, display: 'block', marginTop: 8 }}>
                      其他节点还有 {violations.length} 处问题，收起选中可查看全部
                    </Text>
                  )}
                </div>
              </div>
            )}
          </div>
        </Content>
      </Layout>

      {/* ============ 抽屉 / 弹窗 ============ */}
      <Drawer
        title={`版本管理 · ${defKey}`}
        open={versionDrawer}
        onClose={() => setVersionDrawer(false)}
        width={440}
      >
        <Space direction="vertical" style={{ width: '100%' }}>
          {versions.map((v) => (
            <Card key={v.version} size="small">
              <Space style={{ width: '100%', justifyContent: 'space-between' }}>
                <span>
                  v{v.version}{' '}
                  <Tag color={v.enabled ? 'green' : 'default'}>{v.enabled ? '已上线' : '已下线'}</Tag>
                </span>
                <Space>
                  <Button size="small" onClick={() => void loadVersion(v)}>
                    载入
                  </Button>
                  <Switch
                    checked={v.enabled}
                    size="small"
                    disabled={!canWrite}
                    onChange={(on) => void onToggleEnabled(v.version, on)}
                  />
                </Space>
              </Space>
            </Card>
          ))}
          {versions.length === 0 && <Empty description="暂无版本" />}
        </Space>
      </Drawer>

      {/* 模板画廊：常驻侧栏里最占纵向空间的一块，独立成弹窗后「我的流程」才回到首屏 */}
      <Modal
        title="从模板创建流程"
        open={tplGallery}
        onCancel={() => setTplGallery(false)}
        footer={null}
        width={760}
        destroyOnHidden
      >
        <Paragraph type="secondary" style={{ fontSize: 12, marginTop: -4 }}>
          模板只提供结构骨架与指派策略，创建后可在画布上按需调整；导出 JSON 可注册到任意环境复用。
        </Paragraph>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, minmax(0, 1fr))', gap: 12 }}>
          {TEMPLATES.map((tpl) => (
            <Card
              key={tpl.id}
              size="small"
              hoverable
              onClick={() => startCreate(tpl)}
              style={{ cursor: canWrite ? 'pointer' : 'not-allowed' }}
            >
              <div style={{ display: 'flex', alignItems: 'flex-start', gap: 10 }}>
                <div
                  style={{
                    width: 32,
                    height: 32,
                    borderRadius: 8,
                    background: colors.primary[50],
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    flexShrink: 0,
                  }}
                >
                  <BranchesOutlined style={{ color: colors.primary[500] }} />
                </div>
                <div style={{ minWidth: 0, flex: 1 }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 6, flexWrap: 'wrap' }}>
                    <Text strong style={{ fontSize: 13 }}>
                      {tpl.name}
                    </Text>
                    {tpl.tags.map((tg) => (
                      <Tag key={tg} color="blue" style={{ marginInlineEnd: 0, fontSize: 11 }}>
                        {tg}
                      </Tag>
                    ))}
                  </div>
                  <Paragraph type="secondary" style={{ fontSize: 12, margin: '4px 0 0', lineHeight: 1.5 }}>
                    {tpl.desc}
                  </Paragraph>
                </div>
              </div>
            </Card>
          ))}
        </div>
      </Modal>

      {/* 新建空白流程 */}
      <Modal
        title="新建空白流程"
        open={createModal.open}
        onCancel={() => setCreateModal({ open: false, key: '', name: '' })}
        onOk={() => {
          const key = createModal.key.trim();
          if (!/^[A-Za-z][A-Za-z0-9_]{2,63}$/.test(key)) {
            message.error('流程 key 需以字母开头，仅含字母 / 数字 / 下划线，长度 3-64');
            return;
          }
          setCreateModal({ open: false, key: '', name: '' });
          setSessionKeys((keys) => (keys.includes(key) ? keys : [...keys, key]));
          void openKey(key, createModal.name.trim() || key);
        }}
        okText="创建并打开"
        cancelText="取消"
        okButtonProps={{ disabled: !canWrite }}
      >
        <Space direction="vertical" style={{ width: '100%' }} size={12}>
          <div>
            <Text type="secondary" style={{ fontSize: 12 }}>
              流程 key（唯一标识，创建后不建议修改）
            </Text>
            <Input
              value={createModal.key}
              maxLength={64}
              placeholder="如 expense_approval"
              onChange={(e) => setCreateModal((p) => ({ ...p, key: e.target.value }))}
              onPressEnter={() => undefined}
              style={{ marginTop: 4 }}
            />
          </div>
          <div>
            <Text type="secondary" style={{ fontSize: 12 }}>
              流程名称
            </Text>
            <Input
              value={createModal.name}
              maxLength={64}
              placeholder="如 费用报销审批"
              onChange={(e) => setCreateModal((p) => ({ ...p, name: e.target.value }))}
              style={{ marginTop: 4 }}
            />
          </div>
        </Space>
      </Modal>

      {/* 从模板创建 */}
      <Modal
        title={`从模板创建 · ${tplModal.tpl?.name ?? ''}`}
        open={tplModal.open}
        onOk={() => void confirmCreateFromTemplate()}
        onCancel={() => setTplModal({ open: false, tpl: null, key: '', name: '' })}
        okText="创建并保存草稿"
        cancelText="取消"
        okButtonProps={{ disabled: !canWrite }}
      >
        <Paragraph type="secondary" style={{ fontSize: 12 }}>
          {tplModal.tpl?.desc}
        </Paragraph>
        <div style={{ marginBottom: 12 }}>
          <Text type="secondary" style={{ fontSize: 12 }}>
            流程 key（唯一标识，字母 / 数字 / 下划线）
          </Text>
          <Input
            value={tplModal.key}
            onChange={(e) => setTplModal((p) => ({ ...p, key: e.target.value }))}
            placeholder="如 purchase_approval"
            style={{ marginTop: 4 }}
          />
        </div>
        <div>
          <Text type="secondary" style={{ fontSize: 12 }}>
            流程名称
          </Text>
          <Input
            value={tplModal.name}
            onChange={(e) => setTplModal((p) => ({ ...p, name: e.target.value }))}
            placeholder="如 采购审批"
            style={{ marginTop: 4 }}
          />
        </div>
      </Modal>

      {/* 导出 JSON */}
      <Modal
        title="导出工作流定义"
        open={exportJson !== null}
        onOk={() => setExportJson(null)}
        onCancel={() => setExportJson(null)}
        okText="关闭"
        cancelText="复制"
        cancelButtonProps={{ icon: <CopyOutlined />, onClick: () => void copyExport() }}
      >
        <Paragraph type="secondary" style={{ fontSize: 12 }}>
          把以下 JSON 注册到任意租户 / 环境，即可复用该审批流（跨环境集成）。
        </Paragraph>
        <Input.TextArea
          readOnly
          value={exportJson ?? ''}
          autoSize={{ minRows: 12, maxRows: 20 }}
          style={{ fontFamily: 'monospace', fontSize: 12 }}
        />
      </Modal>
    </div>
  );
};

/** 在文档树里定位 code 所在的 step。 */
function findStep(doc: FlowDoc, code: string): Step | null {
  const walk = (steps: Step[]): Step | null => {
    for (const s of steps) {
      if (s.code === code) return s;
      if (s.kind === 'condition') {
        for (const b of s.branches) {
          const r = walk(b.body);
          if (r) return r;
        }
        const d = walk(s.defaultBranch.body);
        if (d) return d;
      }
    }
    return null;
  };
  return walk(doc.steps);
}

const STEP_COLOR: Record<string, string> = {
  approve: colors.primary[500],
  cc: '#13c2c2',
  condition: colors.primary[700],
};

const StepPropertyPanel: React.FC<{
  step: Step;
  onChange: (next: Step) => void;
  onDelete: () => void;
  violations: Violation[];
  readOnly?: boolean;
}> = ({ step, onChange, onDelete, violations, readOnly = false }) => {
  return (
    // 只读模式用原生 <fieldset disabled> 一次性停用面板内全部表单控件（input / select / button），
    // 不逐个传 disabled：属性面板里控件有二十多个，逐个传既易漏也让后续新增控件默认「忘了门禁」。
    <fieldset
      disabled={readOnly}
      style={{ border: 0, padding: 0, margin: 0, minWidth: 0, opacity: readOnly ? 0.75 : 1 }}
    >
      <Space style={{ justifyContent: 'space-between', width: '100%' }}>
        <Tag color={STEP_COLOR[step.kind]}>{step.kind}</Tag>
        <Button size="small" danger icon={<DeleteOutlined />} onClick={onDelete}>
          删除
        </Button>
      </Space>
      <Divider style={{ margin: '12px 0' }} />
      <div style={{ marginBottom: 8 }}>
        <Text type="secondary">节点编码</Text>
        <div>{step.code}</div>
      </div>
      <div style={{ marginBottom: 8 }}>
        <Text type="secondary">名称</Text>
        <Input value={step.label} onChange={(e) => onChange({ ...step, label: e.target.value })} />
      </div>

      {step.kind === 'approve' && (
        <>
          <div style={{ marginBottom: 8 }}>
            <Text type="secondary">完成方式</Text>
            <Select
              style={{ width: '100%' }}
              value={step.mode}
              onChange={(v: ApprovalMode) => onChange({ ...step, mode: v })}
              options={[
                { value: 'SINGLE', label: '单签' },
                { value: 'ALL', label: '会签（全部）' },
                { value: 'ANY', label: '或签（任一）' },
                { value: 'RATIO', label: '比例会签' },
              ]}
            />
          </div>
          {step.mode === 'RATIO' && (
            <div style={{ marginBottom: 8 }}>
              <Text type="secondary">至少通过人数</Text>
              <InputNumber
                min={1}
                style={{ width: '100%' }}
                value={step.quorum ?? 1}
                onChange={(v) => onChange({ ...step, quorum: v ?? 1 })}
              />
            </div>
          )}
          <RuleEditor rules={step.rules} onChange={(rules) => onChange({ ...step, rules })} />
        </>
      )}

      {step.kind === 'cc' && <RuleEditor rules={step.rules} onChange={(rules) => onChange({ ...step, rules })} />}

      {step.kind === 'condition' && (
        <BranchEditor
          branches={step.branches}
          defaultBranch={step.defaultBranch}
          onChange={(branches, defaultBranch) => onChange({ ...step, branches, defaultBranch })}
        />
      )}

      {violations.length > 0 && (
        <Alert
          style={{ marginTop: 8 }}
          type="error"
          showIcon
          message={violations.map((v) => `${v.code}`).join('、')}
          description={violations.map((v) => hintOf(v).suggestion ?? v.message).join('；')}
        />
      )}
    </fieldset>
  );
};

const RuleEditor: React.FC<{ rules: AssignmentRule[]; onChange: (rules: AssignmentRule[]) => void }> = ({
  rules,
  onChange,
}) => {
  const update = (i: number, rule: AssignmentRule) => onChange(rules.map((r, idx) => (idx === i ? rule : r)));
  return (
    <div>
      <Text type="secondary">审批人 / 抄送人指派</Text>
      {rules.map((rule, i) => (
        <Card key={i} size="small" style={{ margin: '8px 0' }}>
          <Select
            style={{ width: '100%', marginBottom: 6 }}
            value={rule.strategy}
            onChange={(v: AssignmentStrategy) => update(i, { strategy: v, params: {} })}
            options={[
              { value: 'LINE_MANAGER', label: '直属主管' },
              { value: 'DEPT_HEAD', label: '部门负责人' },
              { value: 'LEVEL_UP', label: '向上 N 级' },
              { value: 'ROLE', label: '角色' },
              { value: 'USER', label: '指定人' },
              { value: 'FIELD', label: '表单字段' },
              { value: 'INITIATOR_SELECT', label: '发起人自选' },
            ]}
          />
          {PARAM_KEY_BY_STRATEGY[rule.strategy] && (
            <Input
              placeholder={PARAM_KEY_BY_STRATEGY[rule.strategy] as string}
              value={(rule.params[PARAM_KEY_BY_STRATEGY[rule.strategy] as string] as string) ?? ''}
              onChange={(e) =>
                update(i, {
                  ...rule,
                  params: {
                    ...rule.params,
                    [PARAM_KEY_BY_STRATEGY[rule.strategy] as string]: e.target.value,
                  },
                })
              }
            />
          )}
          <Button
            size="small"
            danger
            style={{ marginTop: 6 }}
            onClick={() => onChange(rules.filter((_, idx) => idx !== i))}
          >
            移除
          </Button>
        </Card>
      ))}
      <Button
        size="small"
        icon={<PlusOutlined />}
        onClick={() => onChange([...rules, { strategy: 'LINE_MANAGER', params: {} }])}
      >
        添加指派
      </Button>
    </div>
  );
};

const OPERATORS = [
  { value: 'eq', label: '等于' },
  { value: 'neq', label: '不等于' },
  { value: 'gt', label: '大于' },
  { value: 'gte', label: '大于等于' },
  { value: 'lt', label: '小于' },
  { value: 'lte', label: '小于等于' },
  { value: 'in', label: '属于' },
  { value: 'between', label: '介于' },
];

const BranchEditor: React.FC<{
  branches: Branch[];
  defaultBranch: Branch;
  onChange: (branches: Branch[], defaultBranch: Branch) => void;
}> = ({ branches, defaultBranch, onChange }) => {
  const renderBranch = (b: Branch, isDefault: boolean) => (
    <Card
      key={b.id}
      size="small"
      title={isDefault ? `默认分支：${b.label}` : `分支：${b.label}`}
      style={{ margin: '8px 0' }}
    >
      <Input
        placeholder="分支名称"
        value={b.label}
        onChange={(e) => patch(b.id, { ...b, label: e.target.value }, isDefault)}
        style={{ marginBottom: 6 }}
      />
      {!isDefault && (
        <div style={{ display: 'flex', gap: 4, marginBottom: 6 }}>
          <Input
            placeholder="字段 fieldCode"
            value={(b.when as { fieldCode?: string } | null)?.fieldCode ?? ''}
            onChange={(e) =>
              patch(
                b.id,
                {
                  ...b,
                  when: {
                    fieldCode: e.target.value,
                    operator: ((b.when as { operator?: string } | null)?.operator as never) ?? 'eq',
                    value: ((b.when as { value?: unknown } | null)?.value as never) ?? '',
                  },
                },
                isDefault,
              )
            }
          />
          <Select
            style={{ width: 90 }}
            value={((b.when as { operator?: string } | null)?.operator as string) ?? 'eq'}
            onChange={(op) =>
              patch(
                b.id,
                {
                  ...b,
                  when: {
                    fieldCode: ((b.when as { fieldCode?: string } | null)?.fieldCode as string) ?? '',
                    operator: op as never,
                    value: ((b.when as { value?: unknown } | null)?.value as never) ?? '',
                  },
                },
                isDefault,
              )
            }
            options={OPERATORS}
          />
          <Input
            placeholder="值"
            value={((b.when as { value?: unknown } | null)?.value as string) ?? ''}
            onChange={(e) =>
              patch(
                b.id,
                {
                  ...b,
                  when: {
                    fieldCode: ((b.when as { fieldCode?: string } | null)?.fieldCode as string) ?? '',
                    operator: ((b.when as { operator?: string } | null)?.operator as never) ?? 'eq',
                    value: e.target.value as never,
                  },
                },
                isDefault,
              )
            }
          />
        </div>
      )}
      <Text type="secondary">分支内步骤：{b.body.length} 个</Text>
    </Card>
  );
  const patch = (id: string, next: Branch, isDefault: boolean) => {
    if (isDefault) onChange(branches, next);
    else onChange(branches.map((x) => (x.id === id ? next : x)), defaultBranch);
  };
  return (
    <div>
      {branches.map((b) => renderBranch(b, false))}
      {renderBranch(defaultBranch, true)}
      <Button
        size="small"
        icon={<PlusOutlined />}
        onClick={() =>
          onChange(
            [...branches, { id: genCode('b'), label: `分支 ${branches.length + 1}`, when: null, body: [] }],
            defaultBranch,
          )
        }
      >
        添加分支
      </Button>
    </div>
  );
};

const SimReportView: React.FC<{
  report: { path?: string[]; conditionTraces?: unknown[]; warnings?: string[]; error?: string | null };
}> = ({ report }) => {
  if (report.error) return <Alert type="error" message="试运行失败" description={report.error} />;
  return (
    <div>
      <div>
        <Text type="secondary">路径：</Text> {report.path?.join(' → ')}
      </div>
      {report.warnings?.map((w, i) => (
        <Alert key={i} type="warning" showIcon message={w} style={{ margin: '4px 0' }} />
      ))}
      {report.conditionTraces && report.conditionTraces.length > 0 && (
        <div style={{ marginTop: 8 }}>
          <Text type="secondary">分支判定：</Text>
          {report.conditionTraces.map((t, i) => (
            <div key={i}>
              {(t as { label?: string }).label}：
              {(t as { branches?: Array<{ label: string; matched: boolean | null }> }).branches
                ?.map((b) => `${b.label}=${b.matched}`)
                .join('，')}
            </div>
          ))}
        </div>
      )}
    </div>
  );
};

export default WorkflowDesigner;
